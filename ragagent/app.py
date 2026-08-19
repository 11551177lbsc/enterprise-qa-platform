"""LangGraph Agent 的轻量 Streamlit 演示页。"""

import time

import httpx
import streamlit as st

st.set_page_config(page_title="Enterprise QA Agent", page_icon="🤖")
st.title("企业知识库与工单 Agent")

api_base = st.sidebar.text_input("Agent API", "http://127.0.0.1:8001").rstrip("/")
token = st.sidebar.text_input("登录 JWT", type="password")
st.sidebar.caption("JWT 只用于本次页面请求，不会显示在聊天记录中。")

if "messages" not in st.session_state:
    st.session_state.messages = []
if "pending" not in st.session_state:
    st.session_state.pending = None

for message in st.session_state.messages:
    with st.chat_message(message["role"]):
        st.markdown(message["content"])


def headers():
    return {"Authorization": f"Bearer {token}"}


def wait_for_run(run_id: str):
    for _ in range(120):
        response = httpx.get(f"{api_base}/api/agent/runs/{run_id}", headers=headers(), timeout=10)
        response.raise_for_status()
        run = response.json()
        if run["status"] in {"completed", "failed", "waiting_approval", "cancelled"}:
            return run
        time.sleep(0.25)
    raise TimeoutError("Agent 运行超时")


def render_result(run):
    if run["status"] == "waiting_approval":
        st.session_state.pending = run
        return "写操作正在等待你的确认。"
    if run["status"] == "failed":
        return f"执行失败：{run.get('error', '未知错误')}"
    citations = run.get("citations") or []
    suffix = ""
    if citations:
        suffix = "\n\n来源：" + "、".join(sorted({item["source"] for item in citations}))
    return (run.get("answer") or "任务已结束。") + suffix


if prompt := st.chat_input("询问知识库，或创建/查询/修改工单"):
    if not token:
        st.error("请先在侧边栏粘贴登录 JWT")
    else:
        st.session_state.messages.append({"role": "user", "content": prompt})
        with st.chat_message("user"):
            st.markdown(prompt)
        with st.chat_message("assistant"):
            with st.spinner("Agent 正在规划并执行……"):
                try:
                    response = httpx.post(
                        f"{api_base}/api/agent/runs",
                        headers=headers(),
                        json={"message": prompt},
                        timeout=10,
                    )
                    response.raise_for_status()
                    answer = render_result(wait_for_run(response.json()["runId"]))
                except Exception as exc:
                    answer = f"请求失败：{exc}"
                st.markdown(answer)
                st.session_state.messages.append({"role": "assistant", "content": answer})

pending = st.session_state.pending
if pending:
    approval = pending["pendingApproval"]
    st.warning(f"待确认：{approval['summary']}\n\n参数：{approval['arguments']}")
    approve_col, reject_col = st.columns(2)
    if approve_col.button("批准执行", type="primary"):
        response = httpx.post(
            f"{api_base}/api/agent/runs/{pending['runId']}/approvals/{approval['approvalId']}",
            headers=headers(),
            json={"approved": True, "reason": "用户在演示页确认"},
            timeout=10,
        )
        response.raise_for_status()
        result = wait_for_run(pending["runId"])
        st.session_state.pending = None
        st.success(render_result(result))
        st.rerun()
    if reject_col.button("拒绝"):
        response = httpx.post(
            f"{api_base}/api/agent/runs/{pending['runId']}/approvals/{approval['approvalId']}",
            headers=headers(),
            json={"approved": False, "reason": "用户拒绝"},
            timeout=10,
        )
        response.raise_for_status()
        st.session_state.pending = None
        st.rerun()
