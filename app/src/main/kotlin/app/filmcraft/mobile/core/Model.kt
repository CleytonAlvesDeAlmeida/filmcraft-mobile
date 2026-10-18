package app.filmcraft.mobile.core

import org.json.JSONObject

data class ClipView(
    val id: Long,
    val uri: String,
    val name: String,
    val sourceMs: Long,
    val inMs: Long,
    val outMs: Long,
    val startMs: Long,
    val durationMs: Long,
    val durationTimecode: String,
    val muted: Boolean,
)

data class ProjectView(
    val frameRate: String,
    val totalMs: Long,
    val totalTimecode: String,
    val clips: List<ClipView>,
) {
    companion object {
        val EMPTY = ProjectView("30", 0L, "00:00:00:00", emptyList())
    }
}

sealed interface CoreResult {
    data class Ok(val stateJson: String, val view: ProjectView) : CoreResult
    data class Err(val message: String) : CoreResult
}

internal fun parseResult(raw: String): CoreResult = try {
    val root = JSONObject(raw)
    if (!root.optBoolean("ok", false)) {
        CoreResult.Err(root.optString("error", "Erro desconhecido"))
    } else {
        val v = root.getJSONObject("view")
        val arr = v.getJSONArray("clips")
        val clips = (0 until arr.length()).map { i ->
            val c = arr.getJSONObject(i)
            ClipView(
                id = c.getLong("id"),
                uri = c.getString("uri"),
                name = c.getString("name"),
                sourceMs = c.getLong("sourceMs"),
                inMs = c.getLong("inMs"),
                outMs = c.getLong("outMs"),
                startMs = c.getLong("startMs"),
                durationMs = c.getLong("durationMs"),
                durationTimecode = c.getString("durationTimecode"),
                muted = c.optBoolean("muted", false),
            )
        }
        CoreResult.Ok(
            stateJson = root.getJSONObject("state").toString(),
            view = ProjectView(
                frameRate = v.getString("frameRate"),
                totalMs = v.getLong("totalMs"),
                totalTimecode = v.getString("totalTimecode"),
                clips = clips,
            ),
        )
    }
} catch (e: Exception) {
    CoreResult.Err("Resposta inválida do núcleo: ${e.message}")
}
