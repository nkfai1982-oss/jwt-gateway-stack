from pathlib import Path

import yaml


ROOT = Path(__file__).resolve().parents[1]


def load_compose():
    return yaml.safe_load((ROOT / "docker-compose.yml").read_text(encoding="utf-8"))


def test_only_gateway_publishes_a_host_port():
    services = load_compose()["services"]
    published = [name for name, service in services.items() if service.get("ports")]
    assert published == ["gateway"]


def test_auth_and_backends_are_only_on_internal_backend_network():
    compose = load_compose()
    services = compose["services"]

    assert compose["networks"]["backend"]["internal"] is True
    assert set(services["gateway"]["networks"]) == {"edge", "backend"}
    for name in ("auth-service", "spring-api", "python-api"):
        assert set(services[name]["networks"]) == {"backend"}
        assert "ports" not in services[name]
