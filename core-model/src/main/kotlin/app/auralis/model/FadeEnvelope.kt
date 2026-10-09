package app.auralis.model

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class DeckGains(val outgoing: Double, val incoming: Double)

object FadeEnvelope {
    // Normalized equal-power shape keeps summed gain <= 1 for correlated tracks.
    fun at(progress: Double, dj: Boolean = false): DeckGains {
        val p = progress.coerceIn(0.0, 1.0)
        val shaped = if(dj) p*p*(3-2*p) else p
        val angle = shaped * PI / 2
        val outgoing = cos(angle)
        val incoming = sin(angle)
        val sum = outgoing + incoming
        return DeckGains(outgoing / sum, incoming / sum)
    }
}
