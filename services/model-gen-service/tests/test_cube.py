"""Real OpenCascade checks for the v0.1 cube generator."""

from math import isclose

from build123d import export_stl

from scripts.test_generate import CUBE_MM, build_cube


def test_cube_is_valid_ten_millimetre_solid() -> None:
    """The generated model is a valid 10 mm cube with positive volume."""
    cube = build_cube().part
    bounds = cube.bounding_box()

    assert cube.is_valid
    assert isclose(cube.volume, CUBE_MM**3)
    assert isclose(bounds.size.X, CUBE_MM)
    assert isclose(bounds.size.Y, CUBE_MM)
    assert isclose(bounds.size.Z, CUBE_MM)


def test_cube_exports_to_non_empty_stl(tmp_path) -> None:
    """The CAD kernel writes an actual non-empty STL artifact."""
    output = tmp_path / "cube.stl"

    assert export_stl(build_cube().part, output)
    assert output.stat().st_size > 0
