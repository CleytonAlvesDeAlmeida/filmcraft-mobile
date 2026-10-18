//! Núcleo do FilmCraft Mobile.
//!
//! Interface com o Android: tudo passa como JSON (strings), o que mantém a ponte JNI mínima e
//! fácil de evoluir. A UI guarda o estado como texto e o devolve a cada operação; o Rust nunca
//! guarda estado global.

pub mod jni_bridge;
pub mod timeline;

use serde::Serialize;
use timeline::{Op, Project, View};

#[derive(Serialize)]
#[serde(untagged)]
enum Envelope<'a> {
    Ok { ok: bool, state: &'a Project, view: View },
    Err { ok: bool, error: String },
}

fn ok(p: &Project) -> String {
    serde_json::to_string(&Envelope::Ok { ok: true, state: p, view: p.view() }).unwrap_or_else(|e| err(e.to_string()))
}

fn err(msg: impl Into<String>) -> String {
    serde_json::to_string(&Envelope::Err { ok: false, error: msg.into() })
        .unwrap_or_else(|_| r#"{"ok":false,"error":"erro interno"}"#.to_string())
}

/// Versão do núcleo.
pub fn version() -> String {
    format!("filmcraft-mobile-core {}", env!("CARGO_PKG_VERSION"))
}

/// Projeto vazio.
pub fn new_project_json() -> String {
    ok(&Project::default())
}

/// Aplica uma operação (`op_json`) ao estado (`state_json`) e devolve o novo estado e a visão.
pub fn apply_json(state_json: &str, op_json: &str) -> String {
    let mut project: Project = match serde_json::from_str(state_json) {
        Ok(p) => p,
        Err(e) => return err(format!("Estado inválido: {e}")),
    };
    let op: Op = match serde_json::from_str(op_json) {
        Ok(o) => o,
        Err(e) => return err(format!("Operação inválida: {e}")),
    };
    match project.apply(op) {
        Ok(()) => ok(&project),
        Err(e) => err(e),
    }
}

/// Timecode `HH:MM:SS:FF` para um tempo em milissegundos.
pub fn timecode_ms(ms: i64, num: i64, den: i64) -> String {
    let p = Project { frame_rate: filmcraft_time::FrameRate::new(num.max(1), den.max(1)), ..Project::default() };
    p.timecode(filmcraft_time::Tick::from_units(ms, 1000))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn envelope_flow() {
        let first = new_project_json();
        let v: serde_json::Value = serde_json::from_str(&first).unwrap();
        assert_eq!(v["ok"], true);
        let state = v["state"].to_string();
        let out = apply_json(&state, r#"{"op":"add_clip","uri":"content://x","name":"x.mp4","duration_ms":8000}"#);
        let v: serde_json::Value = serde_json::from_str(&out).unwrap();
        assert_eq!(v["ok"], true);
        assert_eq!(v["view"]["totalMs"], 8000);
        assert_eq!(v["view"]["clips"][0]["name"], "x.mp4");
    }

    #[test]
    fn errors_are_json() {
        let out = apply_json("{}", "{}");
        let v: serde_json::Value = serde_json::from_str(&out).unwrap();
        assert_eq!(v["ok"], false);
        let state = serde_json::to_string(&Project::default()).unwrap();
        let out = apply_json(&state, r#"{"op":"remove","id":7}"#);
        let v: serde_json::Value = serde_json::from_str(&out).unwrap();
        assert_eq!(v["ok"], false);
        assert!(v["error"].as_str().unwrap().contains("não encontrado"));
    }

    #[test]
    fn timecode_helper() {
        assert_eq!(timecode_ms(1_500, 30, 1), "00:00:01:15");
    }
}
