package app.filmcraft.mobile.core

import org.json.JSONObject

/**
 * Ponte para o núcleo Rust (rust/mobile-core). Todas as chamadas trocam JSON em texto.
 * O estado do projeto vive aqui (Kotlin) como String; o Rust é stateless.
 */
object NativeCore {
    var loaded: Boolean = false
        private set

    init {
        loaded = runCatching { System.loadLibrary("filmcraft_mobile") }.isSuccess
    }

    // Nomes precisam bater com as funções em rust/mobile-core/src/jni_bridge.rs
    external fun version(): String
    external fun newProject(): String
    external fun applyOp(stateJson: String, opJson: String): String
    external fun timecode(ms: Long, num: Int, den: Int): String

    fun create(): CoreResult = parseResult(newProject())

    fun apply(stateJson: String, op: JSONObject): CoreResult = parseResult(applyOp(stateJson, op.toString()))
}

/** Construtores das operações de edição (ver `Op` em timeline.rs). */
object Ops {
    fun addClip(uri: String, name: String, durationMs: Long): JSONObject =
        JSONObject().put("op", "add_clip").put("uri", uri).put("name", name).put("duration_ms", durationMs)

    fun remove(id: Long): JSONObject = JSONObject().put("op", "remove").put("id", id)

    fun move(id: Long, toIndex: Int): JSONObject =
        JSONObject().put("op", "move").put("id", id).put("to_index", toIndex)

    fun trim(id: Long, inMs: Long, outMs: Long): JSONObject =
        JSONObject().put("op", "trim").put("id", id).put("in_ms", inMs).put("out_ms", outMs)

    fun split(atMs: Long): JSONObject = JSONObject().put("op", "split").put("at_ms", atMs)

    fun duplicate(id: Long): JSONObject = JSONObject().put("op", "duplicate").put("id", id)

    fun toggleMute(id: Long): JSONObject = JSONObject().put("op", "toggle_mute").put("id", id)
}
