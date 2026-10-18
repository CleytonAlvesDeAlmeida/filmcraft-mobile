//! Funções JNI chamadas por `app.filmcraft.mobile.core.NativeCore` (Kotlin).
//!
//! Regra: nenhuma exceção Rust pode atravessar a fronteira JNI. Todo corpo roda dentro de
//! `catch_unwind` e erros viram JSON `{"ok":false,"error":"..."}`.

use jni::objects::{JObject, JString};
use jni::sys::{jint, jlong, jstring};
use jni::JNIEnv;
use std::panic::{catch_unwind, AssertUnwindSafe};

fn read(env: &mut JNIEnv, s: &JString) -> String {
    env.get_string(s).map(|v| v.into()).unwrap_or_default()
}

fn give(env: &mut JNIEnv, s: String) -> jstring {
    env.new_string(s).map(|v| v.into_raw()).unwrap_or(std::ptr::null_mut())
}

fn guarded(f: impl FnOnce() -> String) -> String {
    catch_unwind(AssertUnwindSafe(f)).unwrap_or_else(|_| r#"{"ok":false,"error":"falha interna no núcleo"}"#.to_string())
}

#[no_mangle]
pub extern "system" fn Java_app_filmcraft_mobile_core_NativeCore_version(mut env: JNIEnv, _this: JObject) -> jstring {
    let out = guarded(crate::version);
    give(&mut env, out)
}

#[no_mangle]
pub extern "system" fn Java_app_filmcraft_mobile_core_NativeCore_newProject(mut env: JNIEnv, _this: JObject) -> jstring {
    let out = guarded(crate::new_project_json);
    give(&mut env, out)
}

#[no_mangle]
pub extern "system" fn Java_app_filmcraft_mobile_core_NativeCore_applyOp(
    mut env: JNIEnv,
    _this: JObject,
    state: JString,
    op: JString,
) -> jstring {
    let state = read(&mut env, &state);
    let op = read(&mut env, &op);
    let out = guarded(|| crate::apply_json(&state, &op));
    give(&mut env, out)
}

#[no_mangle]
pub extern "system" fn Java_app_filmcraft_mobile_core_NativeCore_timecode(
    mut env: JNIEnv,
    _this: JObject,
    ms: jlong,
    num: jint,
    den: jint,
) -> jstring {
    let out = guarded(|| crate::timecode_ms(ms, i64::from(num), i64::from(den)));
    give(&mut env, out)
}
