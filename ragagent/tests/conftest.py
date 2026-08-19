import os

import pytest


@pytest.fixture
def settings(monkeypatch):
    monkeypatch.setenv("AGENT_ENV", "test")
    monkeypatch.setenv("JWT_SECRET", "0123456789abcdef0123456789abcdef")
    monkeypatch.setenv("AGENT_SERVICE_TOKEN", "service-token-0123456789abcdef0123456789")
    monkeypatch.setenv("AGENT_CHECKPOINT_BACKEND", "memory")
    monkeypatch.setenv("AGENT_PLANNER_MODE", "heuristic")
    from ragagent.settings import Settings

    return Settings.from_env()
