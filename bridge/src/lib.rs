//! ove-android — JNI bridge over the `ove-engine` headless session.
//!
//! DESIGN CONTRACT (see docs/ENGINE_INTEGRATION_AUDIT.md):
//! * This crate is a CLIENT of the engine library — the same session surface
//!   `ove-cli` consumes. It contains ZERO engine behavior.
//! * Every JNI method returns a JSON string:
//!     `{"ok":true, ...}` | `{"ok":false,"kind":"<typed-kind>","message":"..."}`
//!   The typed `EngineError` taxonomy is mapped 1:1 at the boundary — the UI
//!   never parses engine message strings for control flow.
//! * Single-writer: one engine session per process (engine v1 model); the
//!   Kotlin side serializes all calls through one dispatcher thread.
//! * All times cross the boundary as exact rationals (num, den) — never floats.
//! * The engine's own security budgets (ADR-022, RLW-9) run inside the
//!   session; this bridge adds no bypass and no pre-validation shortcuts.

use std::sync::Mutex;

use jni::objects::{JByteArray, JClass, JString};
use jni::sys::{jbyteArray, jint, jlong, jstring};
use jni::JNIEnv;
use serde_json::{json, Value};

use ove_engine::{Engine, EngineError};
use ove_render::OutputSpec;
use ove_time::Rational;

// ---------------------------------------------------------------------------
// Engine pin (recorded; fetch-engine.sh enforces the checkout)
// ---------------------------------------------------------------------------
pub const ENGINE_PIN: &str = "06c92496f7051f15069663296ec51e88e170fe18";
pub const BRIDGE_VERSION: &str = "0.1.0";

// ---------------------------------------------------------------------------
// Session (single-writer v1)
// ---------------------------------------------------------------------------
static SESSION: Mutex<Option<Engine>> = Mutex::new(None);

fn with_engine(f: impl FnOnce(&mut Engine) -> Result<Value, EngineError>) -> Value {
    let mut guard = SESSION.lock().unwrap_or_else(|p| p.into_inner());
    match guard.as_mut() {
        Some(e) => match f(e) {
            Ok(v) => v,
            Err(err) => {
                let (kind, message) = error_parts(&err);
                json!({"ok": false, "kind": kind, "message": message})
            }
        },
        None => json!({"ok": false, "kind": "NoSession", "message": "no project is open"}),
    }
}

fn with_engine_locked<T>(f: impl FnOnce(&mut Option<Engine>) -> Result<Value, EngineError>) -> Value {
    let mut guard = SESSION.lock().unwrap_or_else(|p| p.into_inner());
    match f(&mut guard) {
        Ok(v) => v,
        Err(err) => {
            let (kind, message) = error_parts(&err);
            json!({"ok": false, "kind": kind, "message": message})
        }
    }
}

// ---------------------------------------------------------------------------
// Typed error mapping (EngineError -> {kind, message}) — audit §8
// ---------------------------------------------------------------------------
fn error_parts(e: &EngineError) -> (&'static str, String) {
    match e {
        EngineError::Project(d) => ("Project", d.to_string()),
        EngineError::Import(d) => ("ImportRejected", d.clone()),
        EngineError::UnknownAsset(d) => ("UnknownAsset", d.clone()),
        EngineError::Timeline(d) => ("TimelineError", format!("{d:?}")),
        EngineError::Render(d) => ("RenderFailed", format!("{d:?}")),
        EngineError::Compile(d) => ("RenderFailed", format!("{d:?}")),
        EngineError::Encode(d) => ("ExportFailed", format!("{d:?}")),
        EngineError::Mux(d) => ("ExportFailed", format!("{d:?}")),
        EngineError::Seam(d) => ("SeamError", format!("{d:?}")),
        EngineError::NoPlacement { at } => ("NothingAtTime", format!("no placement covers t={at}")),
        EngineError::NoAudioStream => ("NoAudioStream", "no audio stream in the imported sources".into()),
        EngineError::NonExactSampleCut { at, rate } => (
            "NonSampleExactCut",
            format!("audio cut t={at} is not sample-exact at {rate} Hz"),
        ),
        EngineError::AudioRetimeUnsupported { speed } => (
            "RetimeUnsupported",
            format!("audio retime x{speed} unsupported in v1 (named gap)"),
        ),
        EngineError::KeyframeValueOutOfRange { clip_id, property, value } => (
            "KeyframeRange",
            format!("clip {clip_id} keyframed {property} value {value} leaves i32 pixel range"),
        ),
        EngineError::Internal(d) => ("EngineInternal", d.clone()),
    }
}

// ---------------------------------------------------------------------------
// JSON helpers
// ---------------------------------------------------------------------------
fn rat(r: &Rational) -> Value {
    json!([r.num(), r.den()])
}

fn opt_rat(r: &Option<Rational>) -> Value {
    match r {
        Some(v) => rat(v),
        None => Value::Null,
    }
}

/// Probe JSON straight from the engine's in-memory probe record
/// (`SourceMedia.probe`) — the same data the engine persists as the
/// documented `assets/<hash>/probe.json` sidecar.
fn probe_json(p: &ove_media::ProbeInfo) -> Value {
    let streams: Vec<Value> = p
        .streams
        .iter()
        .map(|s| {
            json!({
                "kind": format!("{:?}", s.kind),
                "codec": s.codec,
                "time_base": rat(&s.time_base),
                "duration": opt_rat(&s.duration),
                "avg_frame_rate": opt_rat(&s.avg_frame_rate),
                "video": s.video.as_ref().map(|v| json!({
                    "width": v.width,
                    "height": v.height,
                })),
                "audio": s.audio.as_ref().map(|a| json!({
                    "sample_rate": a.sample_rate,
                    "channels": a.channels,
                })),
            })
        })
        .collect();
    json!({
        "duration": opt_rat(&p.duration),
        "streams": streams,
    })
}

fn first_video_geometry(e: &Engine) -> Option<(u32, u32, Option<Rational>, bool)> {
    // first imported source (the render-binding source, ADR-017), its video
    // stream geometry + duration, and whether any audio stream exists.
    let hashes: Vec<String> = e
        .project()
        .assets()
        .iter()
        .map(|a| a.content_hash.clone())
        .collect();
    let render_hash = hashes.iter().min()?.clone();
    let media = e.source(&render_hash)?;
    let mut video = None;
    let mut audio = false;
    for s in &media.probe.streams {
        match s.kind {
            ove_media::StreamKind::Video => {
                if video.is_none() {
                    if let Some(v) = &s.video {
                        video = Some((v.width, v.height, s.duration));
                    }
                }
            }
            ove_media::StreamKind::Audio => audio = true,
            _ => {}
        }
    }
    let (w, h, dur) = video?;
    Some((w, h, dur, audio))
}

/// UI projection of the live document. The engine remains authoritative —
/// `state_hash` is verified after every mutation batch.
fn shape(e: &Engine) -> Value {
    let assets: Vec<Value> = e
        .project()
        .assets()
        .iter()
        .map(|a| {
            let probe = e.source(&a.content_hash).map(|m| probe_json(&m.probe));
            json!({"id": a.id, "hash": a.content_hash, "probe": probe})
        })
        .collect();
    let mut tracks = Vec::new();
    let mut span = Rational::new(0, 1);
    for tid in e.project().timeline().track_ids() {
        let tref = match e.project().timeline().track_ref(tid) {
            Ok(t) => t,
            Err(_) => continue,
        };
        let mut clips = Vec::new();
        tref.walk(&mut |_pos, start, clip| {
            let end = start.add(clip.duration);
            // exact cross-denominator max via i128 cross-multiply
            let a = i128::from(end.num()) * i128::from(span.den());
            let b = i128::from(span.num()) * i128::from(end.den());
            if a > b {
                span = end;
            }
            clips.push(json!({
                "id": clip.id,
                "start": rat(&start),
                "duration": rat(&clip.duration),
                "source_in": rat(&clip.source_in),
            }));
        });
        tracks.push(json!({"id": tid, "clips": clips}));
    }
    let hashes: Vec<String> = e
        .project()
        .assets()
        .iter()
        .map(|a| a.content_hash.clone())
        .collect();
    let render_source = hashes.iter().min().cloned();
    let multi_source = hashes.len() > 1;
    json!({
        "state_hash": e.state_hash(),
        "undo_depth": e.project().undo_depth(),
        "assets": assets,
        "tracks": tracks,
        "timeline_span": rat(&span),
        "render_source": render_source,
        // ADR-017 v1 note: multi-source timelines render the FIRST imported
        // source. Surfaced so the UI can state the limitation truthfully.
        "multi_source_limit": multi_source,
    })
}

fn ok_shape(e: &mut Engine) -> Value {
    let s = shape(e);
    let mut out = json!({"ok": true});
    if let (Some(o), Some(s)) = (out.as_object_mut(), s.as_object()) {
        for (k, v) in s {
            o.insert(k.clone(), v.clone());
        }
    }
    out
}

// ---------------------------------------------------------------------------
// Core operations (library-level; unit-testable without a JVM)
// ---------------------------------------------------------------------------
pub mod ops {
    use super::*;

    pub fn version() -> Value {
        json!({"ok": true, "bridge": BRIDGE_VERSION, "engine_pin": ENGINE_PIN, "engine_version": "0.1.0"})
    }

    pub fn create(dir: &str, tick_num: i64, tick_den: i64) -> Value {
        match Engine::create(std::path::Path::new(dir), (tick_num, tick_den)) {
            Ok(e) => {
                *SESSION.lock().unwrap_or_else(|p| p.into_inner()) = Some(e);
                with_engine_locked_ok()
            }
            Err(e) => ops_err_json(e),
        }
    }

    pub fn open(dir: &str) -> Value {
        match Engine::open(std::path::Path::new(dir)) {
            Ok(e) => {
                *SESSION.lock().unwrap_or_else(|p| p.into_inner()) = Some(e);
                with_engine_locked_ok()
            }
            Err(e) => ops_err_json(e),
        }
    }

    fn with_engine_locked_ok() -> Value {
        let mut guard = SESSION.lock().unwrap_or_else(|p| p.into_inner());
        match guard.as_mut() {
            Some(e) => ok_shape(e),
            None => json!({"ok": false, "kind": "NoSession", "message": "no project is open"}),
        }
    }

    pub fn close() -> Value {
        *SESSION.lock().unwrap_or_else(|p| p.into_inner()) = None;
        json!({"ok": true, "closed": true})
    }

    pub fn import_media(path: &str) -> Value {
        with_engine(|e| {
            let hex = e.import_media(std::path::Path::new(path))?;
            let mut out = ok_shape(e);
            out["hash"] = Value::String(hex);
            Ok(out)
        })
    }

    pub fn add_track(id: i64) -> Value {
        with_engine(|e| {
            use ove_timeline::{GapTrack, TrackKind};
            e.add_track(id as u64, TrackKind::Gap(GapTrack::new()))?;
            Ok(ok_shape(e))
        })
    }

    pub fn add_clip(track: i64, hash: &str, dur: Rational, src_in: Rational) -> Value {
        with_engine(|e| {
            let clip_id = e.add_clip(track as u64, hash, dur, src_in)?;
            let mut out = ok_shape(e);
            out["clip_id"] = json!(clip_id);
            Ok(out)
        })
    }

    pub fn split(track: i64, clip: i64, at: Rational) -> Value {
        with_engine(|e| {
            let new_id = e.split(track as u64, clip as u64, at)?;
            let mut out = ok_shape(e);
            out["new_clip_id"] = json!(new_id);
            Ok(out)
        })
    }

    pub fn resize(track: i64, clip: i64, duration: Rational) -> Value {
        with_engine(|e| {
            e.resize(track as u64, clip as u64, duration)?;
            Ok(ok_shape(e))
        })
    }

    pub fn move_clip(clip: i64, from: i64, to: i64, index: i64) -> Value {
        with_engine(|e| {
            e.move_clip(clip as u64, from as u64, to as u64, index as usize)?;
            Ok(ok_shape(e))
        })
    }

    pub fn remove(track: i64, clip: i64) -> Value {
        with_engine(|e| {
            e.remove(track as u64, clip as u64)?;
            Ok(ok_shape(e))
        })
    }

    pub fn undo() -> Value {
        with_engine(|e| {
            let did = e.undo()?;
            let mut out = ok_shape(e);
            out["did"] = json!(did);
            Ok(out)
        })
    }

    pub fn redo() -> Value {
        with_engine(|e| {
            let did = e.redo()?;
            let mut out = ok_shape(e);
            out["did"] = json!(did);
            Ok(out)
        })
    }

    pub fn render_frame(t: Rational, w: u32, h: u32) -> Result<(Vec<u8>, u32, u32), EngineError> {
        let output = OutputSpec {
            width: w,
            height: h,
            rate_num: 24,
            rate_den: 1,
            working_space: bt709_limited(),
        };
        with_engine_inner(|e| {
            let frame = e.render_frame(&output, t)?;
            let (fw, fh) = (frame.width, frame.height);
            let bytes = frame
                .cpu_bytes()
                .ok_or_else(|| EngineError::Internal("render produced no CPU frame".into()))?
                .data
                .clone();
            Ok((bytes, fw, fh, w, h))
        })
        .and_then(|(bytes, fw, fh, ew, eh)| {
            if fw != ew || fh != eh {
                return Err(EngineError::Internal(format!(
                    "rendered frame {fw}x{fh} does not match requested {ew}x{eh}"
                )));
            }
            let stride_ok = bytes.len() >= (ew as usize) * (eh as usize) * 4;
            if !stride_ok {
                return Err(EngineError::Internal("render frame payload too small".into()));
            }
            // row copy honoring the source stride, output tightly packed
            let stride = bytes.len() / eh as usize;
            let mut packed = vec![0u8; (ew as usize) * (eh as usize) * 4];
            for row in 0..eh as usize {
                let src = row * stride;
                let dst = row * (ew as usize) * 4;
                packed[dst..dst + (ew as usize) * 4]
                    .copy_from_slice(&bytes[src..src + (ew as usize) * 4]);
            }
            Ok((packed, ew, eh))
        })
    }

    fn with_engine_inner<T>(
        f: impl FnOnce(&mut Engine) -> Result<T, EngineError>,
    ) -> Result<T, EngineError> {
        let mut guard = SESSION.lock().unwrap_or_else(|p| p.into_inner());
        match guard.as_mut() {
            Some(e) => f(e),
            None => Err(EngineError::Internal("no project is open".into())),
        }
    }

    pub fn export_reencode(out: &str, rate_num: i64, rate_den: i64) -> Value {
        with_engine(|e| {
            let (w, h, _dur, _audio) = first_video_geometry(e)
                .ok_or_else(|| EngineError::Import("no video stream in the first source".into()))?;
            let output = OutputSpec {
                width: w,
                height: h,
                rate_num,
                rate_den,
                working_space: bt709_limited(),
            };
            // n_frames from the exact timeline span: ceil(span * rate)
            let s = shape(e);
            let span = s["timeline_span"].as_array().unwrap();
            let sn = span[0].as_i64().unwrap();
            let sd = span[1].as_i64().unwrap();
            let num = i128::from(sn) * i128::from(rate_num);
            let den = i128::from(sd) * i128::from(rate_den);
            let n_frames = num.div_euclid(den) + if num.rem_euclid(den) > 0 { 1 } else { 0 };
            if n_frames <= 0 || n_frames > i64::MAX as i128 {
                return Err(EngineError::Internal("computed frame count out of range".into()));
            }
            let info = e.export_reencode(
                std::path::Path::new(out),
                &output,
                n_frames as i64,
            )?;
            Ok(json!({
                "ok": true,
                "path": info.path.to_string_lossy(),
                "sha256": info.file_sha256,
                "size": info.file_size,
                "frames": n_frames,
                "state_hash": e.state_hash(),
            }))
        })
    }

    pub fn export_copy(out: &str, hash: &str, start: Rational, end: Rational) -> Value {
        with_engine(|e| {
            let (info, snaps) =
                e.export_copy(hash, start, end, std::path::Path::new(out))?;
            Ok(json!({
                "ok": true,
                "path": info.path.to_string_lossy(),
                "sha256": info.file_sha256,
                "size": info.file_size,
                "snaps": snaps.len(),
            }))
        })
    }

    pub fn export_wav(out: &str) -> Value {
        with_engine(|e| {
            let samples = e.export_wav(std::path::Path::new(out))?;
            Ok(json!({"ok": true, "samples": samples, "path": out}))
        })
    }

    fn bt709_limited() -> ove_media::ColorTags {
        ove_media::ColorTags {
            primaries: ove_media::Primaries::Bt709,
            transfer: ove_media::Transfer::Bt709,
            matrix: ove_media::MatrixCoeffs::Bt709,
            range: ove_media::Range::Limited,
            chroma_loc: Some(ove_media::ChromaLoc::Left),
        }
    }
}

// ---------------------------------------------------------------------------
// JNI shim
// ---------------------------------------------------------------------------
fn to_string(env: &mut JNIEnv, s: &JString) -> String {
    env.get_string(s)
        .map(|v| v.to_string_lossy().into_owned())
        .unwrap_or_default()
}

fn reply(env: &mut JNIEnv, v: Value) -> jstring {
    match env.new_string(v.to_string()) {
        Ok(s) => s.into_raw(),
        Err(_) => match env.new_string(
            "{\"ok\":false,\"kind\":\"EngineInternal\",\"message\":\"jni string creation failed\"}",
        ) {
            Ok(s) => s.into_raw(),
            Err(_) => std::ptr::null_mut(),
        },
    }
}

fn jrat(num: jlong, den: jlong) -> Rational {
    Rational::new(num, den)
}

#[no_mangle]
pub extern "system" fn Java_app_ove_studio_engine_OveJni_nativeVersion(
    env: &mut JNIEnv, _c: JClass,
) -> jstring {
    reply(env, ops::version())
}

#[no_mangle]
pub extern "system" fn Java_app_ove_studio_engine_OveJni_nativeCreateProject(
    env: &mut JNIEnv, _c: JClass, dir: JString, tick_num: jlong, tick_den: jlong,
) -> jstring {
    let d = to_string(env, &dir);
    reply(env, ops::create(&d, tick_num, tick_den))
}

#[no_mangle]
pub extern "system" fn Java_app_ove_studio_engine_OveJni_nativeOpenProject(
    env: &mut JNIEnv, _c: JClass, dir: JString,
) -> jstring {
    let d = to_string(env, &dir);
    reply(env, ops::open(&d))
}

#[no_mangle]
pub extern "system" fn Java_app_ove_studio_engine_OveJni_nativeCloseProject(
    env: &mut JNIEnv, _c: JClass,
) -> jstring {
    reply(env, ops::close())
}

#[no_mangle]
pub extern "system" fn Java_app_ove_studio_engine_OveJni_nativeImportMedia(
    env: &mut JNIEnv, _c: JClass, path: JString,
) -> jstring {
    let p = to_string(env, &path);
    reply(env, ops::import_media(&p))
}

#[no_mangle]
pub extern "system" fn Java_app_ove_studio_engine_OveJni_nativeAddTrack(
    env: &mut JNIEnv, _c: JClass, id: jlong,
) -> jstring {
    reply(env, ops::add_track(id))
}

#[no_mangle]
pub extern "system" fn Java_app_ove_studio_engine_OveJni_nativeAddClip(
    env: &mut JNIEnv, _c: JClass, track: jlong, hash: JString,
    dur_num: jlong, dur_den: jlong, in_num: jlong, in_den: jlong,
) -> jstring {
    let h = to_string(env, &hash);
    reply(
        env,
        ops::add_clip(track, &h, jrat(dur_num, dur_den), jrat(in_num, in_den))
            ,
    )
}

#[no_mangle]
pub extern "system" fn Java_app_ove_studio_engine_OveJni_nativeSplit(
    env: &mut JNIEnv, _c: JClass, track: jlong, clip: jlong, at_num: jlong, at_den: jlong,
) -> jstring {
    reply(
        env,
        ops::split(track, clip, jrat(at_num, at_den)),
    )
}

#[no_mangle]
pub extern "system" fn Java_app_ove_studio_engine_OveJni_nativeResize(
    env: &mut JNIEnv, _c: JClass, track: jlong, clip: jlong, dur_num: jlong, dur_den: jlong,
) -> jstring {
    reply(
        env,
        ops::resize(track, clip, jrat(dur_num, dur_den)),
    )
}

#[no_mangle]
pub extern "system" fn Java_app_ove_studio_engine_OveJni_nativeMoveClip(
    env: &mut JNIEnv, _c: JClass, clip: jlong, from: jlong, to: jlong, index: jlong,
) -> jstring {
    reply(
        env,
        ops::move_clip(clip, from, to, index),
    )
}

#[no_mangle]
pub extern "system" fn Java_app_ove_studio_engine_OveJni_nativeRemoveClip(
    env: &mut JNIEnv, _c: JClass, track: jlong, clip: jlong,
) -> jstring {
    reply(env, ops::remove(track, clip))
}

#[no_mangle]
pub extern "system" fn Java_app_ove_studio_engine_OveJni_nativeUndo(
    env: &mut JNIEnv, _c: JClass,
) -> jstring {
    reply(env, ops::undo())
}

#[no_mangle]
pub extern "system" fn Java_app_ove_studio_engine_OveJni_nativeRedo(
    env: &mut JNIEnv, _c: JClass,
) -> jstring {
    reply(env, ops::redo())
}

#[no_mangle]
pub extern "system" fn Java_app_ove_studio_engine_OveJni_nativeExportReencode(
    env: &mut JNIEnv, _c: JClass, out: JString, rate_num: jlong, rate_den: jlong,
) -> jstring {
    let o = to_string(env, &out);
    reply(
        env,
        ops::export_reencode(&o, rate_num, rate_den),
    )
}

#[no_mangle]
pub extern "system" fn Java_app_ove_studio_engine_OveJni_nativeExportCopy(
    env: &mut JNIEnv, _c: JClass, out: JString, hash: JString,
    s_num: jlong, s_den: jlong, e_num: jlong, e_den: jlong,
) -> jstring {
    let o = to_string(env, &out);
    let h = to_string(env, &hash);
    reply(
        env,
        ops::export_copy(&o, &h, jrat(s_num, s_den), jrat(e_num, e_den))
            ,
    )
}

#[no_mangle]
pub extern "system" fn Java_app_ove_studio_engine_OveJni_nativeExportWav(
    env: &mut JNIEnv, _c: JClass, out: JString,
) -> jstring {
    let o = to_string(env, &out);
    reply(env, ops::export_wav(&o))
}

#[no_mangle]
pub extern "system" fn Java_app_ove_studio_engine_OveJni_nativeRenderFrame(
    env: &mut JNIEnv, _c: JClass, t_num: jlong, t_den: jlong, w: jint, h: jint,
    out: jbyteArray,
) -> jstring {
    let res = ops::render_frame(jrat(t_num, t_den), w as u32, h as u32);
    match res {
        Ok((bytes, fw, fh)) => {
            let len = bytes.len() as i32;
            let arr = unsafe { JByteArray::from_raw(out) };
            unsafe {
                let ptr = bytes.as_ptr() as *const jni::sys::jbyte;
                env.set_byte_array_region(&arr, 0, std::slice::from_raw_parts(ptr, len as usize))
                    .ok();
            }
            reply(env, json!({"ok": true, "width": fw, "height": fh, "bytes": len}))
        }
        Err(e) => {
            let (kind, message) = error_parts(&e);
            reply(env, json!({"ok": false, "kind": kind, "message": message}))
        }
    }
}

fn ops_err_json(e: EngineError) -> Value {
    let (kind, message) = error_parts(&e);
    json!({"ok": false, "kind": kind, "message": message})
}


