import pytest

from ragagent.persistence.run_store import MemoryRunStore, RunStateConflict
from ragagent.schemas.agent import AgentRun, RunStatus


@pytest.mark.asyncio
async def test_terminal_run_cannot_be_cancelled_or_overwritten():
    store = MemoryRunStore()
    run = AgentRun(
        runId="run-terminal",
        threadId="thread-terminal",
        userId=7,
        status=RunStatus.QUEUED,
        input="测试",
    )
    await store.create(run)
    await store.update(
        run.runId,
        expected_statuses={RunStatus.QUEUED},
        status=RunStatus.RUNNING,
    )
    await store.update(
        run.runId,
        expected_statuses={RunStatus.RUNNING},
        status=RunStatus.COMPLETED,
        answer="完成",
    )

    with pytest.raises(RunStateConflict):
        await store.cancel(run.runId)
    with pytest.raises(RunStateConflict):
        await store.update(
            run.runId,
            expected_statuses={RunStatus.RUNNING},
            status=RunStatus.FAILED,
        )

    stored = await store.get(run.runId)
    assert stored.status == RunStatus.COMPLETED
    assert stored.answer == "完成"
