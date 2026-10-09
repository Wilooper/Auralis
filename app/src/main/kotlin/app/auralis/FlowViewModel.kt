package app.auralis

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.auralis.library.SongAnalyzer
import app.auralis.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class FlowState(val goal: FlowGoal = FlowGoal.STEADY, val busy: Boolean = false, val analyzed: Int = 0, val total: Int = 0, val done: Int = 0,
    val skipped: Int = 0, val message: String? = null, val preview: List<LibraryTrack> = emptyList())
class FlowViewModel(app: Application) : AndroidViewModel(app) {
    private val db = (app as AuralisApplication).library.database
    private val mutable = MutableStateFlow(FlowState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    init { reload() }
    fun reload() { viewModelScope.launch(Dispatchers.IO) { mutable.update { it.copy(analyzed = db.analyses().values.count { profile -> profile.confidence > 0 }, total = db.count()) } } }
    fun analyze() {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            mutable.update { it.copy(busy = true, done = 0, skipped = 0, message = null, preview = emptyList()) }
            try { withContext(Dispatchers.IO) {
                val cached = db.analyses()
                mutable.update { it.copy(analyzed = cached.values.count { profile -> profile.confidence > 0 }, total = db.count()) }
                val tracks = db.search("", limit = 100_000).filter { it.id !in cached }
                val analyzer = SongAnalyzer(getApplication())
                for (track in tracks) {
                    ensureActive()
                    val result = analyzer.analyze(track)
                    db.saveAnalysis(track, result ?: SongFeatures(0.0, 0.0, 0.0, 0.0))
                    mutable.update { it.copy(done = it.done+1, analyzed = it.analyzed + if(result != null) 1 else 0,
                        skipped = it.skipped + if(result == null) 1 else 0, message = track.title) }
                    delay(30)
                }
            } } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: Exception) { mutable.update { it.copy(message = failure.message) }
            } finally { mutable.update { it.copy(busy = false, message = "Saved ${it.analyzed} audio profiles · ${it.skipped} unavailable or silent") }; reload() }
        }
    }
    fun cancel() { job?.cancel() }
    fun preview(goal: FlowGoal, seed: String?) {
        mutable.update { it.copy(goal = goal, preview = emptyList()) }
        viewModelScope.launch(Dispatchers.IO) {
            val result = SmartFlow.order(db.search("", limit = 100_000), db.analyses(), seed, goal)
            mutable.update { if(it.goal != goal) it else it.copy(preview = result, message = if(result.isEmpty()) "Analyze songs first to create a flow." else "${result.size} songs · preview the order before playing") }
        }
    }
}
