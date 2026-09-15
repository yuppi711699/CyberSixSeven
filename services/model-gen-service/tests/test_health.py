"""Automated checks for the v0.1 HTTP surface."""

from app.main import app, health


def test_health_route_is_registered_for_get() -> None:
    """The service exposes the one permitted user-facing endpoint."""
    health_route = next(route for route in app.routes if route.path == "/health")

    assert health_route.methods == {"GET"}
    assert health() == {"status": "ok"}
