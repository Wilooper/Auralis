package app.auralis.model

/** Host-owned admission policy. It bounds accepted work, not arbitrary APK CPU usage. */
class ExtensionTaskPolicy(private val clock: () -> Long, private val globalLimit: Int = 2,
    private val deadlineMs: Long = 30_000) {
    data class Lease(val task: String, val extension: String, val deadline: Long, val background: Boolean)
    private val active = linkedMapOf<String, Lease>()
    private var visible = false

    @Synchronized fun begin(task: String, extension: String, backgroundGranted: Boolean): Lease {
        require(task.isNotBlank() && extension.isNotBlank() && task.length <= 80 && extension.length <= 512)
        check(visible || backgroundGranted) { "Open ReVa to run this task" }
        check(task !in active) { "Duplicate task" }
        check(active.size < globalLimit) { "Two tasks are already running" }
        check(active.values.none { it.extension == extension }) { "This extension already has a running task" }
        return Lease(task, extension, clock() + deadlineMs, backgroundGranted).also { active[task] = it }
    }
    @Synchronized fun valid(task: String): Boolean = active[task]?.let {
        clock() < it.deadline && (visible || it.background)
    } == true
    @Synchronized fun visibility(value: Boolean): List<Lease> {
        visible = value
        return remove { clock() >= it.deadline || (!value && !it.background) }
    }
    @Synchronized fun expire(): List<Lease> = remove { clock() >= it.deadline }
    @Synchronized fun revoke(extension: String): List<Lease> = remove { it.extension == extension }
    @Synchronized fun finish(task: String) { active.remove(task) }
    @Synchronized fun snapshot(): List<Lease> = active.values.toList()
    private fun remove(predicate: (Lease) -> Boolean): List<Lease> = active.values.filter(predicate).also { leases ->
        leases.forEach { active.remove(it.task) }
    }
}
