import httpx
import asyncio
import jwt
import pytest

from ragagent.main import create_app


class UnusedTools:
    async def execute(self, *args, **kwargs):
        raise AssertionError("本测试不应执行工具")


class KnowledgeTools:
    async def execute(self, tool_name, arguments, access_token, invocation_id):
        assert tool_name == "search_knowledge_base"
        return [{
            "chunkId": "api-chunk",
            "source": "故障排除.txt",
            "score": 0.88,
            "content": "确认充电座电源和回充路径没有遮挡。",
        }]


class FeedbackClient:
    def __init__(self):
        self.calls = []

    async def submit_feedback(self, **kwargs):
        self.calls.append(kwargs)
        return {"recorded": True, "outcome": kwargs["outcome"]}


@pytest.mark.asyncio
async def test_health_and_authentication_boundary(settings):
    app = create_app(settings=settings, tools=UnusedTools())
    transport = httpx.ASGITransport(app=app)
    async with app.router.lifespan_context(app):
        async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
            health = await client.get("/health")
            assert health.status_code == 200
            assert health.json()["checkpoint"] == "memory"

            unauthorized = await client.post("/api/agent/runs", json={"message": "你好"})
            assert unauthorized.status_code == 401


@pytest.mark.asyncio
async def test_authenticated_run_completes_through_http_api(settings):
    feedback_client = FeedbackClient()
    app = create_app(settings=settings, tools=KnowledgeTools(), feedback_client=feedback_client)
    token = jwt.encode({"sub": "7", "exp": 4102444800}, settings.jwt_secret, algorithm="HS256")
    headers = {"Authorization": f"Bearer {token}"}
    transport = httpx.ASGITransport(app=app)
    async with app.router.lifespan_context(app):
        async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
            created = await client.post(
                "/api/agent/runs",
                headers=headers,
                json={"message": "机器人无法回充怎么办"},
            )
            assert created.status_code == 202
            run_id = created.json()["runId"]

            for _ in range(50):
                result = await client.get(f"/api/agent/runs/{run_id}", headers=headers)
                if result.json()["status"] == "completed":
                    break
                await asyncio.sleep(0.01)

            assert result.status_code == 200
            assert result.json()["userId"] == 7
            assert "充电座" in result.json()["answer"]
            assert result.json()["citations"][0]["source"] == "故障排除.txt"
            assert result.json()["resolution"]["outcome"] == "answered"
            assert result.json()["resolution"]["confidence"] == 0.88

            feedback = await client.post(
                f"/api/agent/runs/{run_id}/feedback",
                headers=headers,
                json={"outcome": "UNRESOLVED", "comment": "仍未解决"},
            )
            assert feedback.status_code == 200
            assert feedback.json()["recorded"] is True
            assert feedback_client.calls[0]["question"] == "机器人无法回充怎么办"
            assert feedback_client.calls[0]["confidence"] == 0.88

            other_token = jwt.encode({"sub": "8", "exp": 4102444800}, settings.jwt_secret, algorithm="HS256")
            forbidden = await client.post(
                f"/api/agent/runs/{run_id}/feedback",
                headers={"Authorization": f"Bearer {other_token}"},
                json={"outcome": "UNRESOLVED"},
            )
            assert forbidden.status_code == 404
