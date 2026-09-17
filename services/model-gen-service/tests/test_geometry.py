from math import isclose

import pytest

from app.geometry import (
    BRIM_MINOR_RATIO,
    HEIGHT_MM,
    RADIUS_MM,
    build_accessory,
    generate_accessory,
)


def test_default_accessory_is_a_valid_fused_solid() -> None:
    solid = build_accessory()
    bounds = solid.bounding_box()
    expected_xy = 2 * (RADIUS_MM + RADIUS_MM * BRIM_MINOR_RATIO)
    expected_z = HEIGHT_MM + RADIUS_MM * BRIM_MINOR_RATIO

    assert solid.is_valid
    assert len(solid.solids()) == 1
    assert solid.volume > 0
    assert isclose(bounds.size.X, expected_xy, abs_tol=1e-3)
    assert isclose(bounds.size.Y, expected_xy, abs_tol=1e-3)
    assert isclose(bounds.size.Z, expected_z, abs_tol=1e-3)
    assert isclose(bounds.min.Z, -(RADIUS_MM * BRIM_MINOR_RATIO), abs_tol=1e-3)
    assert isclose(bounds.max.Z, HEIGHT_MM, abs_tol=1e-3)


def test_generate_accessory_writes_a_non_empty_stl() -> None:
    stl = generate_accessory()
    try:
        assert stl.stat().st_size > 80
        header = stl.read_bytes()[:5]
        assert header == b"solid" or stl.stat().st_size > 84
    finally:
        stl.unlink(missing_ok=True)


def test_named_parameters_change_the_bounding_box() -> None:
    default = build_accessory().bounding_box().size.Z
    taller = build_accessory(height_mm=40.0, radius_mm=RADIUS_MM).bounding_box().size.Z
    assert taller > default


def test_rejects_non_positive_dimensions() -> None:
    with pytest.raises(ValueError):
        build_accessory(height_mm=0, radius_mm=RADIUS_MM)
    with pytest.raises(ValueError):
        build_accessory(height_mm=HEIGHT_MM, radius_mm=-1)
