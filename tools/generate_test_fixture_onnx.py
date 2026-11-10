#!/usr/bin/env python3
"""
Reference-only script that builds the tiny FRAME test-fixture ONNX graphs.

The committed files under app/src/main/assets/test_fixture/ are the source of
truth. Re-run this only if you intentionally change the fixture; then update
the unit test expectations / SHA-256 values.

Requires: pip install onnx numpy
"""
from __future__ import annotations

import hashlib
import json
from pathlib import Path

import numpy as np
from onnx import TensorProto, helper, numpy_helper, save, checker

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "app" / "src" / "main" / "assets" / "test_fixture"


def make_model(dtype: int, path: Path) -> tuple[int, str]:
    w = np.eye(4, dtype=np.float32)
    b = np.zeros(4, dtype=np.float32)
    if dtype == TensorProto.FLOAT16:
        w = w.astype(np.float16)
        b = b.astype(np.float16)
        elem = TensorProto.FLOAT16
    else:
        elem = TensorProto.FLOAT

    graph = helper.make_graph(
        [
            helper.make_node("MatMul", ["x", "W"], ["mm"]),
            helper.make_node("Add", ["mm", "b"], ["y"]),
        ],
        "frame_test_matmul_add",
        [helper.make_tensor_value_info("x", elem, [1, 4])],
        [helper.make_tensor_value_info("y", elem, [1, 4])],
        [
            numpy_helper.from_array(w, name="W"),
            numpy_helper.from_array(b, name="b"),
        ],
    )
    model = helper.make_model(graph, opset_imports=[helper.make_opsetid("", 13)])
    model.ir_version = 8
    checker.check_model(model)
    save(model, str(path))
    data = path.read_bytes()
    return len(data), hashlib.sha256(data).hexdigest()


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    files = []
    for name, dtype, role in [
        ("denoiser_fp32.onnx", TensorProto.FLOAT, "denoiser"),
        ("denoiser_fp16.onnx", TensorProto.FLOAT16, "denoiser_fp16"),
    ]:
        size, sha = make_model(dtype, OUT / name)
        files.append(
            {"name": name, "sizeBytes": size, "sha256": sha, "role": role}
        )
        print(f"{name}: {size} bytes  sha256={sha}")

    manifest = {
        "modelId": "frame_test_fixture",
        "version": 1,
        "files": files,
        "graphs": [
            {
                "file": "denoiser_fp32.onnx",
                "inputs": [{"name": "x", "dtype": "float32", "shape": [1, 4]}],
                "outputs": [{"name": "y", "dtype": "float32", "shape": [1, 4]}],
            },
            {
                "file": "denoiser_fp16.onnx",
                "inputs": [{"name": "x", "dtype": "float16", "shape": [1, 4]}],
                "outputs": [{"name": "y", "dtype": "float16", "shape": [1, 4]}],
            },
        ],
    }
    (OUT / "models_manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print("Wrote models_manifest.json")


if __name__ == "__main__":
    main()
