"""Parametric accessory: cone frustum fused to a torus brim.

Dimensions are millimetres. build123d is unitless; the ``_mm`` suffix is the
only thing that keeps a 10× scale error from reaching a printer.
"""

from __future__ import annotations

import tempfile
from pathlib import Path

from build123d import Align, BuildPart, Cone, Torus, export_stl

HEIGHT_MM = 24.0
RADIUS_MM = 10.0
TOP_RADIUS_RATIO = 0.35
BRIM_MINOR_RATIO = 0.22


def build_accessory(
    height_mm: float = HEIGHT_MM, radius_mm: float = RADIUS_MM
):
    """Return the fused solid used for both export and geometry tests."""
    if height_mm <= 0 or radius_mm <= 0:
        raise ValueError("height_mm and radius_mm must be positive")
    top_radius_mm = radius_mm * TOP_RADIUS_RATIO
    brim_minor_mm = radius_mm * BRIM_MINOR_RATIO
    with BuildPart() as accessory:
        Cone(
            radius_mm,
            top_radius_mm,
            height_mm,
            align=(Align.CENTER, Align.CENTER, Align.MIN),
        )
        Torus(radius_mm, brim_minor_mm)
    return accessory.part


def generate_accessory(
    height_mm: float = HEIGHT_MM, radius_mm: float = RADIUS_MM
) -> Path:
    """Export one non-degenerate STL. Pure: no boto3, env, or logging."""
    part = build_accessory(height_mm=height_mm, radius_mm=radius_mm)
    handle = tempfile.NamedTemporaryFile(prefix="c67-accessory-", suffix=".stl", delete=False)
    path = Path(handle.name)
    handle.close()
    if not export_stl(part, path):
        path.unlink(missing_ok=True)
        raise RuntimeError("build123d failed to export the accessory STL")
    return path
