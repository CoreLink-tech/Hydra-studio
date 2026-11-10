# Model install and smoke test (phone-only)

FRAME is built so the owner can install model packs and run a **smoke test** on a real phone without Android Studio. Builds come from GitHub Actions; install the debug APK from the workflow artifact.

This document covers:

1. Installing the **test fixture** (tiny MatMul+Add ONNX — not an AI model)
2. Installing **real weights** via browser download + in-app import (or manifest URL)
3. Running the smoke test and sharing the report

**Warnings**

- Model packs can be **hundreds of MB to multi-GB**. Prefer **Wi‑Fi only** (default ON in the Models screen).
- Mobile data can incur high cost. Confirm size before large downloads (≥100 MB requires an in-app confirmation).
- A **smoke PASS is not a quality or generation validation**. It only means graphs loaded and ran once with zero tensors. Never claim VALIDATED from a smoke test alone.

---

## A) Install test fixture and smoke-test (no file picking)

Debug builds only:

1. Open **Models**.
2. Tap **Install test fixture** (visible when `BuildConfig.DEBUG` is true).
3. Wait for the status message: files are copied into `FRAME/Models/frame_test_fixture/`.
4. On the **FRAME test fixture** card, tap **Test model**.
5. Read the report card. Header always says:  
   `smoke test only: not a quality or generation validation`
6. Use **Share**, **Copy**, or **Save** (system document picker → e.g. Downloads).

Catalogue entry id: `frame_test_fixture`.  
Files: `denoiser_fp32.onnx`, `denoiser_fp16.onnx` (+ `models_manifest.json` in the folder).

Generator script (reference only, not required on the phone): `tools/generate_test_fixture_onnx.py`.

---

## B) Install real weights (browser → import)

Real MobileI2V / other packs are **not** bundled. When conversion publishes files:

1. On the phone browser, open the direct Hugging Face (or other) links for each weight file.
2. Save into **Downloads** (or any folder the system file picker can see).
3. In FRAME → **Models** → choose the catalogue model → **Import files**.
4. Select the matching files. FRAME copies them into `FRAME/Models/<modelId>/` and checks **size** and **SHA-256** against the catalogue `files[]` when those fields are set.
5. Optionally use **Manifest file** or paste a **Manifest URL** to validate a `models_manifest.json` plan before importing.

**Wi‑Fi only** stays ON unless you explicitly turn it off. Download buttons appear only when every catalogue file has both `url` and `sha256`.

---

## C) Smoke test and share

1. Model must be **PARTIAL** or **INSTALLED** (files present; checksums preferred).
2. Tap **Test model**.
3. The harness:
   - Re-checks install state
   - Refuses to run if thermal policy says pause
   - Loads **one ONNX graph at a time** and closes it before the next
   - Builds zero tensors from the manifest (or session shapes)
   - Records load/run ms, PSS, native heap, thermal before/after
4. On failure, the report shows the error and a truncated stack.
5. Share the short text into chat, or Save the JSON.

After a **full PASS**, the UI may offer a **Mark as EXPERIMENTAL (notes only)** message.  
**VALIDATED is never set from a smoke test.** That requires a measured end-to-end MP4 on device.

---

## Storage layout

```
filesDir/FRAME/
  Models/<modelId>/
    *.onnx
    *.part          # partial downloads (kept for resume)
    models_manifest.json   # optional
```

Do not rely on paths under `Android/data/...` for hand-off to Termux or file managers; use Share / Save.

---

## CI note

Unit tests cover the fixture SHA-256/size match, download resume logic, and tensor buffer converters. They do **not** replace on-device smoke tests.
