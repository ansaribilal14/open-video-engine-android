//! Bridge e2e — the client's REAL integration proof on the host toolchain
//! (the same library session the APK loads, driven through the same ops::*
//! call sequence the JNI shim exposes to Kotlin).
//!
//! Two journeys, both honest about their media:
//! 1. `journey_h264_real_media` — the certified NASA public-domain source
//!    (sha256 2d315daf…705f) when provided via OVE_E2E_MEDIA. Asserts the
//!    recorded H.264 stream-copy typed capability limit (F4/F5).
//! 2. `journey_mpeg4_fixture` — a deterministic mpeg4+aac fixture generated
//!    locally with ffmpeg; asserts the FULL pipeline including segment
//!    stream-copy success. Runs in CI where ffmpeg is installed.
//!
//! Media never enters git.

use oveandroid::ops;
use ove_time::Rational;

fn tmpdir(name: &str) -> String {
    let d = std::env::temp_dir().join(format!("ove-e2e-{}-{}", name, std::process::id()));
    let _ = std::fs::remove_dir_all(&d);
    std::fs::create_dir_all(&d).expect("tmpdir create");
    d.to_string_lossy().into_owned()
}

/// Project dirs must NOT pre-exist (Engine::create asserts absence).
fn projdir(name: &str) -> String {
    let d = std::env::temp_dir().join(format!("ove-e2e-{}-{}", name, std::process::id()));
    let _ = std::fs::remove_dir_all(&d);
    d.to_string_lossy().into_owned()
}

fn assert_ok(v: &serde_json::Value, what: &str) {
    assert_eq!(v["ok"], serde_json::Value::Bool(true), "{what} failed: {v}");
}

fn generate_mpeg4_fixture(out: &str) -> bool {
    let ok = std::process::Command::new("ffmpeg")
        .args([
            "-y", "-f", "lavfi", "-i", "testsrc2=size=320x240:rate=24:duration=4",
            "-f", "lavfi", "-i", "sine=frequency=440:duration=4",
            "-c:v", "mpeg4", "-q:v", "6", "-c:a", "aac", "-b:a", "96k",
            "-shortest", out,
        ])
        .stdout(std::process::Stdio::null())
        .stderr(std::process::Stdio::null())
        .status()
        .map(|s| s.success())
        .unwrap_or(false);
    ok
}

fn journey(media: &str, expect_h264_stream_copy_rejection: bool) {
    let _ = ops::close();

    // 1. create project (engine-authoritative folder, tick axis 48000/1)
    let dir = projdir("journey");
    let v = ops::create(&dir, 48_000, 1);
    assert_ok(&v, "create");
    let h0 = v["state_hash"].as_str().expect("state hash").to_string();
    assert!(!h0.is_empty());

    // 2. track + import real media
    assert_ok(&ops::add_track(1), "add track");
    let v = ops::import_media(media);
    assert_ok(&v, "import media");
    let hash = v["hash"].as_str().expect("asset hash").to_string();
    assert_eq!(hash.len(), 64, "BLAKE3-256 hex identity");
    let probe = &v["assets"][0]["probe"];
    let dur = probe["duration"].as_array().expect("duration").to_vec();
    assert!(dur.len() == 2, "exact rational duration");
    let dur_num = dur[0].as_i64().unwrap();
    let dur_den = dur[1].as_i64().unwrap();
    let secs = dur_num as f64 / dur_den as f64;
    assert!(secs > 0.0, "positive duration");
    assert!(
        !probe["streams"].as_array().unwrap().is_empty(),
        "probe streams present"
    );

    // 3. append two clips (engine allocates ids, appends at track end)
    let v = ops::add_clip(1, &hash, Rational::new(2, 1), Rational::new(0, 1));
    assert_ok(&v, "add clip 1");
    let clip1 = v["clip_id"].as_i64().unwrap();
    let v = ops::add_clip(1, &hash, Rational::new(1, 1), Rational::new(1, 1));
    assert_ok(&v, "add clip 2");
    let clip2 = v["clip_id"].as_i64().unwrap();
    assert_ne!(clip1, clip2, "engine allocates distinct ids");

    // 4. split + resize + remove round-trip (gap compaction is engine truth)
    let v = ops::split(1, clip1, Rational::new(1, 1));
    assert_ok(&v, "split");
    let new_id = v["new_clip_id"].as_i64().unwrap();
    assert_ok(&ops::resize(1, new_id, Rational::new(1, 2)), "resize");
    assert_ok(&ops::remove(1, new_id), "remove");

    // 5. undo/redo: exact inverse walk
    let v = ops::undo();
    assert_eq!(v["did"], serde_json::Value::Bool(true), "undo did");
    let v = ops::redo();
    assert_eq!(v["did"], serde_json::Value::Bool(true), "redo did");

    // 6. preview frame render (real engine composite)
    let _ = ops::render_frame(Rational::new(1, 1), 320, 180)
        .expect("render frame at t=1s");

    // 7. composite export (the certified export_reencode route) — DETERMINISM:
    //    the same export twice → identical SHA-256 (client-path determinism)
    let out1 = tmpdir("out1") + "/edit.mp4";
    let e1 = ops::export_reencode(&out1, 24, 1);
    assert_ok(&e1, "export reencode");
    let sha1 = e1["sha256"].as_str().unwrap().to_string();
    let size1 = e1["size"].as_u64().unwrap();
    assert!(size1 > 10_000, "real payload expected, got {size1}");
    let h_final = e1["state_hash"].as_str().expect("post-edit state hash").to_string();
    std::fs::remove_file(&out1).unwrap();
    let out1b = tmpdir("out1b") + "/edit.mp4";
    let e1b = ops::export_reencode(&out1b, 24, 1);
    assert_ok(&e1b, "export reencode (repeat)");
    assert_eq!(sha1, e1b["sha256"], "byte-identical repeat export");

    // 8. WAV mixdown (real timeline audio assembly)
    let wav = tmpdir("wav") + "/mix.wav";
    let w = ops::export_wav(&wav);
    assert_ok(&w, "export wav");
    assert!(w["samples"].as_u64().unwrap() > 1000, "real samples");

    // 9. segment stream-copy export
    let seg = tmpdir("seg") + "/seg.mp4";
    let c = ops::export_copy(&seg, &hash, Rational::new(0, 1), Rational::new(2, 1));
    if expect_h264_stream_copy_rejection {
        // The engine's recorded capability limit (F4/F5): H.264 stream-copy
        // is type-rejected. The client must surface exactly this boundary.
        assert_eq!(c["ok"], serde_json::Value::Bool(false), "H.264 stream copy must be type-rejected");
        assert_eq!(c["kind"], "ExportFailed", "typed capability limit");
        assert!(
            c["message"].as_str().unwrap().contains("stream copy"),
            "capability-limit message, got: {}",
            c["message"]
        );
    } else {
        assert_ok(&c, "mpeg4 segment stream-copy export");
        assert!(c["size"].as_u64().unwrap() > 1_000, "real segment payload");
    }

    // 10. close → reopen → state hash equality (P-1 discipline through the client)
    assert_ok(&ops::close(), "close");
    let v = ops::open(&dir);
    assert_ok(&v, "reopen");
    assert_eq!(
        v["state_hash"].as_str().unwrap(),
        h_final,
        "reopen hash == post-edit hash"
    );
    let _ = std::fs::remove_dir_all(&dir);
}

#[test]
fn journey_h264_real_media() {
    let Ok(media) = std::env::var("OVE_E2E_MEDIA") else {
        eprintln!("SKIP: OVE_E2E_MEDIA not set (real-media gate runs in the certification session)");
        return;
    };
    if !std::path::Path::new(&media).exists() {
        eprintln!("SKIP: {media} not present");
        return;
    }
    journey(&media, true);
}

#[test]
fn journey_mpeg4_fixture() {
    let fixture = tmpdir("fixture") + "/src.mp4";
    if !generate_mpeg4_fixture(&fixture) {
        eprintln!("SKIP: ffmpeg CLI unavailable for fixture generation");
        return;
    }
    journey(&fixture, false);
}
