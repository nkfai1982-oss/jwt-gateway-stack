from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_health_check():
    assert client.get("/healthz").json() == {"status": "ok"}


def test_requires_gateway_identity():
    response = client.get("/whoami")
    assert response.status_code == 401


def test_rejects_wrong_audience():
    response = client.get(
        "/whoami",
        headers={
            "X-Authenticated-User": "alice",
            "X-Authenticated-Audience": "component-a",
            "X-Authenticated-Scopes": "component-b.read",
        },
    )
    assert response.status_code == 401


def test_requires_component_b_scope():
    response = client.get(
        "/whoami",
        headers={
            "X-Authenticated-User": "alice",
            "X-Authenticated-Audience": "component-b",
            "X-Authenticated-Scopes": "component-a.read",
        },
    )
    assert response.status_code == 403


def test_returns_authenticated_identity():
    response = client.get(
        "/whoami",
        headers={
            "X-Authenticated-User": "alice",
            "X-Authenticated-Audience": "component-b",
            "X-Authenticated-Scopes": "component-b.read",
        },
    )
    assert response.status_code == 200
    assert response.json() == {
        "component": "component-b",
        "user": "alice",
        "audience": "component-b",
        "scopes": ["component-b.read"],
    }
