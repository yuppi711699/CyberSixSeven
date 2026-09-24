#!/usr/bin/env python3
"""CAD toolchain smoke test: build a 10 mm cube and export it to ``out/cube.stl``.

This is not a unit test and not product code. It exists so the whole native
stack — build123d -> cadquery-ocp-novtk -> OpenCascade — is proven to install
and run on a machine *before* any real modelling work starts. If this script
writes a non-empty STL, the hard part of the Python environment is done.

Usage (from anywhere; paths resolve relative to this file, not the cwd):

    python scripts/test_generate.py
"""

from pathlib import Path

from build123d import BuildPart, Box, export_stl

#: Edge length of the test cube, in millimetres.
CUBE_MM = 10.0

SERVICE_ROOT = Path(__file__).resolve().parents[1]
OUT_DIR = SERVICE_ROOT / "out"
OUT_FILE = OUT_DIR / "cube.stl"


def build_cube(edge_mm: float = CUBE_MM) -> BuildPart:
    """Build a solid cube of ``edge_mm`` on every side."""
    with BuildPart() as cube:
        Box(edge_mm, edge_mm, edge_mm)
    return cube


def main() -> int:
    cube = build_cube()

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    if not export_stl(cube.part, OUT_FILE):
        print(f"FAILED: build123d did not write {OUT_FILE}")
        return 1

    size = OUT_FILE.stat().st_size
    print(f"OK: wrote {OUT_FILE} ({size} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
