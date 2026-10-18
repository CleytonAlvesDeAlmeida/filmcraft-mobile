package app.filmcraft.mobile

import java.util.Locale

object ClipFormat {
    /** mm:ss.d para tempos em ms (leitura rápida na UI; o timecode exato vem do núcleo Rust). */
    fun clock(ms: Long): String {
        val total = ms.coerceAtLeast(0L)
        val m = total / 60_000
        val s = (total / 1000) % 60
        val d = (total % 1000) / 100
        return String.format(Locale.US, "%02d:%02d.%d", m, s, d)
    }
}
