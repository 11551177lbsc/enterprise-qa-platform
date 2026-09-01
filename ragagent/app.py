"""企业支持解决中心的 Streamlit 客户端。"""

from __future__ import annotations

import json
import os
import time
from typing import Any

import httpx
import streamlit as st


AGENT_API_BASE = os.getenv("UI_AGENT_BASE_URL", "http://127.0.0.1:8001").rstrip("/")
JAVA_API_BASE = os.getenv("UI_JAVA_BASE_URL", "http://127.0.0.1:8080").rstrip("/")
TERMINAL_STATUSES = {"completed", "failed", "waiting_approval", "cancelled"}


def _error_message(response: httpx.Response) -> str:
    try:
        payload = response.json()
    except ValueError:
        return response.text.strip() or f"HTTP {response.status_code}"
    if isinstance(payload, dict):
        return str(payload.get("detail") or payload.get("message") or payload.get("error") or payload)
    return str(payload)


def _headers() -> dict[str, str]:
    token = st.session_state.get("token", "")
    return {"Authorization": f"Bearer {token}"}


def _wait_for_run(run_id: str) -> dict[str, Any]:
    for _ in range(180):
        response = httpx.get(
            f"{AGENT_API_BASE}/api/agent/runs/{run_id}",
            headers=_headers(),
            timeout=10,
        )
        response.raise_for_status()
        run = response.json()
        if run["status"] in TERMINAL_STATUSES:
            return run
        time.sleep(0.5)
    raise TimeoutError("Agent 运行超过 90 秒，请稍后重试")


def _start_run(message: str) -> dict[str, Any]:
    response = httpx.post(
        f"{AGENT_API_BASE}/api/agent/runs",
        headers=_headers(),
        json={"message": message},
        timeout=10,
    )
    response.raise_for_status()
    return _wait_for_run(response.json()["runId"])


def _decide_approval(run: dict[str, Any], approved: bool, reason: str) -> dict[str, Any]:
    approval = run["pendingApproval"]
    response = httpx.post(
        f"{AGENT_API_BASE}/api/agent/runs/{run['runId']}/approvals/{approval['approvalId']}",
        headers=_headers(),
        json={"approved": approved, "reason": reason},
        timeout=10,
    )
    response.raise_for_status()
    return _wait_for_run(run["runId"])


def build_ticket_creation_prompt(draft: dict[str, Any]) -> str:
    """把升级建议转换为可审计的显式写操作请求。"""

    lines = [
        "请创建支持工单：",
        f"标题：{draft['title']}",
        f"优先级：{draft['priority']}",
        f"分类：{draft.get('category') or 'OTHER'}",
    ]
    if draft.get("productModel"):
        lines.append(f"产品型号：{draft['productModel']}")
    if draft.get("knowledgeConfidence") is not None:
        lines.append(f"知识置信度：{float(draft['knowledgeConfidence']):.4f}")
    if draft.get("escalationReason"):
        lines.append(f"升级原因：{draft['escalationReason']}")
    lines.append(f"描述：{draft['description']}")
    return "\n".join(lines)


def _assistant_message(run: dict[str, Any]) -> dict[str, Any]:
    if run["status"] == "waiting_approval":
        content = "已经生成写操作方案，等待你的明确确认后才会执行。"
    elif run["status"] == "failed":
        content = f"处理失败：{run.get('error') or '未知错误'}"
    elif run["status"] == "cancelled":
        content = "本次处理已取消。"
    else:
        content = run.get("answer") or "任务已完成。"
    return {
        "role": "assistant",
        "content": content,
        "citations": run.get("citations") or [],
        "resolution": run.get("resolution"),
    }


def _render_citations(citations: list[dict[str, Any]]) -> None:
    if not citations:
        return
    with st.expander(f"查看知识证据（{len(citations)} 条）"):
        for index, item in enumerate(citations, start=1):
            st.markdown(
                f"**{index}. {item['source']}** · 匹配度 `{float(item['score']):.2f}`\n\n"
                f"> {item['excerpt']}"
            )


def _render_resolution(resolution: dict[str, Any] | None) -> None:
    if not resolution:
        return
    outcome = resolution["outcome"]
    confidence = float(resolution.get("confidence", 0))
    labels = {
        "answered": "知识库已解决",
        "needs_clarification": "需要补充信息",
        "escalation_recommended": "建议升级人工支持",
        "action_completed": "业务操作已完成",
    }
    if outcome == "escalation_recommended":
        st.warning(f"{labels[outcome]} · 证据置信度 {confidence:.0%}")
    else:
        st.caption(f"{labels.get(outcome, outcome)} · 证据置信度 {confidence:.0%}")
    st.caption(resolution.get("reason", ""))
    steps = resolution.get("nextSteps") or []
    if steps:
        st.markdown("**下一步**")
        for step in steps:
            st.markdown(f"- {step}")


def _render_message(message: dict[str, Any]) -> None:
    with st.chat_message(message["role"]):
        st.markdown(message["content"])
        if message["role"] == "assistant":
            _render_resolution(message.get("resolution"))
            _render_citations(message.get("citations") or [])


def _append_run(run: dict[str, Any]) -> None:
    st.session_state.messages.append(_assistant_message(run))
    st.session_state.pending = run if run["status"] == "waiting_approval" else None
    st.session_state.last_resolution = run.get("resolution")
    st.session_state.last_run = run


def _reset_session() -> None:
    keys = (
        "token", "user_id", "username", "role", "messages", "pending",
        "last_resolution", "last_run", "last_question", "feedback_recorded",
    )
    for key in keys:
        st.session_state.pop(key, None)


def _render_authentication() -> None:
    st.title("企业支持解决中心")
    st.write("登录后可查询企业知识、获得可核验的排障建议，并将未解决问题安全升级为工单。")
    login_tab, register_tab = st.tabs(["登录", "注册本地账号"])

    with login_tab:
        with st.form("login-form"):
            username = st.text_input("用户名", autocomplete="username")
            password = st.text_input("密码", type="password", autocomplete="current-password")
            submitted = st.form_submit_button("登录", type="primary", use_container_width=True)
        if submitted:
            try:
                response = httpx.post(
                    f"{JAVA_API_BASE}/auth/login",
                    json={"username": username.strip(), "password": password},
                    timeout=10,
                )
                if response.status_code >= 400:
                    st.error(_error_message(response))
                else:
                    payload = response.json()
                    st.session_state.token = payload["token"]
                    st.session_state.user_id = payload["userId"]
                    st.session_state.username = username.strip()
                    st.rerun()
            except httpx.HTTPError as exc:
                st.error(f"登录服务不可用：{exc}")

    with register_tab:
        with st.form("register-form"):
            new_username = st.text_input("新用户名", autocomplete="username")
            email = st.text_input("邮箱（可选）")
            new_password = st.text_input("设置密码", type="password", autocomplete="new-password")
            confirm = st.text_input("确认密码", type="password", autocomplete="new-password")
            registered = st.form_submit_button("创建账号", use_container_width=True)
        if registered:
            if len(new_username.strip()) < 3:
                st.error("用户名至少 3 个字符")
            elif len(new_password) < 6:
                st.error("密码至少 6 个字符")
            elif new_password != confirm:
                st.error("两次输入的密码不一致")
            else:
                try:
                    response = httpx.post(
                        f"{JAVA_API_BASE}/auth/register",
                        json={"username": new_username.strip(), "password": new_password, "email": email.strip() or None},
                        timeout=10,
                    )
                    if response.status_code >= 400:
                        st.error(_error_message(response))
                    else:
                        st.success("账号已创建，请切换到“登录”。")
                except httpx.HTTPError as exc:
                    st.error(f"注册服务不可用：{exc}")


def _render_pending_approval() -> None:
    pending = st.session_state.get("pending")
    if not pending:
        return
    approval = pending["pendingApproval"]
    st.divider()
    st.subheader("写操作确认")
    st.warning("以下操作会写入业务数据库。系统尚未执行，只有你批准后才会继续。")
    st.code(
        json.dumps(
            {"tool": approval["toolName"], "arguments": approval["arguments"]},
            ensure_ascii=False,
            indent=2,
        ),
        language="json",
    )
    approve_col, reject_col = st.columns(2)
    if approve_col.button("批准并执行", type="primary", use_container_width=True):
        try:
            with st.spinner("正在执行并记录审计结果……"):
                result = _decide_approval(pending, True, "用户在支持中心明确批准")
            st.session_state.pending = None
            _append_run(result)
            st.rerun()
        except Exception as exc:
            st.error(f"审批执行失败：{exc}")
    if reject_col.button("拒绝操作", use_container_width=True):
        try:
            result = _decide_approval(pending, False, "用户拒绝本次写操作")
            st.session_state.pending = None
            _append_run(result)
            st.rerun()
        except Exception as exc:
            st.error(f"拒绝操作失败：{exc}")


def _render_support_center() -> None:
    st.title("智能支持")
    st.caption("先检索受控知识证据；证据不足时生成升级建议；任何工单写入都需要人工确认。")

    if not st.session_state.messages:
        st.info("可以描述产品型号、故障现象、指示灯状态，以及你已经尝试过的排查步骤。")

    for message in st.session_state.messages:
        _render_message(message)

    resolution = st.session_state.get("last_resolution")
    if resolution and resolution.get("outcome") == "escalation_recommended" and not st.session_state.get("pending"):
        draft = resolution.get("ticketDraft")
        if draft:
            with st.expander("查看建议的工单草稿", expanded=True):
                st.markdown(f"**标题：** {draft['title']}")
                st.markdown(f"**优先级：** {draft['priority']}")
                st.text(draft["description"])
                if st.button("将问题升级为工单", type="primary"):
                    st.session_state.queued_prompt = build_ticket_creation_prompt(draft)
                    st.rerun()

    last_run = st.session_state.get("last_run")
    if (
        last_run
        and last_run.get("status") == "completed"
        and (last_run.get("resolution") or {}).get("outcome") == "answered"
        and st.session_state.get("feedback_recorded") != last_run.get("runId")
    ):
        st.divider()
        st.markdown("**这次建议是否解决了问题？**")
        feedback_comment = st.text_input("补充反馈（可选）", key=f"feedback-{last_run['runId']}")
        solved_col, unresolved_col = st.columns(2)
        if solved_col.button("已解决", type="primary", use_container_width=True):
            _submit_feedback(last_run, "RESOLVED", feedback_comment)
        if unresolved_col.button("未解决，升级工单", use_container_width=True):
            _submit_feedback(last_run, "UNRESOLVED", feedback_comment)
            draft = {
                "title": last_run["input"][:120],
                "description": (
                    f"用户问题：{last_run['input']}\n"
                    f"知识库建议未能解决问题。用户反馈：{feedback_comment or '未补充'}"
                ),
                "priority": "MEDIUM",
                "category": "DEVICE",
                "knowledgeConfidence": (last_run.get("resolution") or {}).get("confidence"),
                "escalationReason": "用户确认知识库建议未解决问题。",
            }
            st.session_state.queued_prompt = build_ticket_creation_prompt(draft)
            st.rerun()

    queued = st.session_state.pop("queued_prompt", None)
    prompt = queued or st.chat_input("描述问题，或查询/更新我的支持工单")
    if prompt:
        st.session_state.messages.append({"role": "user", "content": prompt})
        st.session_state.last_question = prompt
        with st.spinner("正在检索证据并规划处理路径……"):
            try:
                run = _start_run(prompt)
                _append_run(run)
            except Exception as exc:
                st.session_state.messages.append({"role": "assistant", "content": f"请求失败：{exc}"})
        st.rerun()

    _render_pending_approval()


def _render_ticket_center() -> None:
    st.title("我的工单")
    st.write("通过同一 Agent 工具网关查询当前账号的工单，Java 后端会再次校验用户身份。")
    if st.button("刷新工单列表", type="primary"):
        try:
            with st.spinner("正在查询……"):
                run = _start_run("列出我的工单")
            st.session_state.ticket_result = run.get("answer") or "暂无结果"
        except Exception as exc:
            st.error(f"查询失败：{exc}")
    if result := st.session_state.get("ticket_result"):
        st.markdown(result)


def _submit_feedback(run: dict[str, Any], outcome: str, comment: str) -> None:
    response = httpx.post(
        f"{AGENT_API_BASE}/api/agent/runs/{run['runId']}/feedback",
        headers=_headers(),
        json={
            "outcome": outcome,
            "comment": comment.strip() or None,
        },
        timeout=10,
    )
    if response.status_code >= 400:
        st.error(_error_message(response))
        return
    st.session_state.feedback_recorded = run["runId"]
    st.success("反馈已记录，将用于改进知识库。")


def _render_knowledge_governance() -> None:
    st.title("知识缺口")
    st.caption("这里只汇总用户明确标记为“未解决”的问题，不展示模型隐藏推理。")
    try:
        response = httpx.get(
            f"{JAVA_API_BASE}/api/support/knowledge-gaps",
            headers=_headers(),
            params={"limit": 50},
            timeout=10,
        )
        if response.status_code >= 400:
            st.error(_error_message(response))
            return
        gaps = response.json().get("data") or []
        if not gaps:
            st.info("当前没有待处理的知识缺口。")
            return
        st.dataframe(gaps, use_container_width=True, hide_index=True)
        gap_id = st.selectbox("选择知识缺口", [item["id"] for item in gaps])
        status = st.selectbox("处理状态", ["IN_REVIEW", "RESOLVED", "OPEN"])
        if st.button("更新状态", type="primary"):
            updated = httpx.patch(
                f"{JAVA_API_BASE}/api/support/knowledge-gaps/{gap_id}",
                headers=_headers(),
                json={"status": status},
                timeout=10,
            )
            if updated.status_code >= 400:
                st.error(_error_message(updated))
            else:
                st.success("知识缺口状态已更新。")
                st.rerun()
    except httpx.HTTPError as exc:
        st.error(f"知识治理服务不可用：{exc}")


def _render_system_status() -> None:
    st.title("系统状态")
    st.caption("这里只展示非敏感运行状态，服务地址来自环境变量，不需要用户手工填写。")
    try:
        agent = httpx.get(f"{AGENT_API_BASE}/health", timeout=5).json()
        rag = httpx.get(f"{AGENT_API_BASE}/api/rag/health", timeout=10).json()
        col1, col2, col3 = st.columns(3)
        col1.metric("Agent API", "正常" if agent.get("status") == "ok" else "异常")
        col2.metric("状态存储", str(agent.get("checkpoint", "unknown")))
        col3.metric("知识库", str(rag.get("collection", "unknown")))
    except Exception as exc:
        st.error(f"状态检查失败：{exc}")


st.set_page_config(page_title="企业支持解决中心", page_icon="🛠️", layout="wide")

for key, default in {"messages": [], "pending": None, "last_resolution": None, "last_run": None}.items():
    if key not in st.session_state:
        st.session_state[key] = default

if not st.session_state.get("token"):
    _render_authentication()
    st.stop()

st.sidebar.title("支持工作台")
st.sidebar.success(f"已登录：{st.session_state.get('username', '当前用户')}")
if "role" not in st.session_state:
    try:
        role_response = httpx.get(f"{JAVA_API_BASE}/api/support/me", headers=_headers(), timeout=5)
        st.session_state.role = (
            role_response.json().get("data", {}).get("role", "USER")
            if role_response.status_code < 400 else "USER"
        )
    except httpx.HTTPError:
        st.session_state.role = "USER"
pages = ["智能支持", "我的工单", "系统状态"]
if st.session_state.role in {"SUPPORT_AGENT", "KNOWLEDGE_ADMIN", "ADMIN"}:
    pages.insert(2, "知识缺口")
page = st.sidebar.radio("功能", pages)
if st.sidebar.button("退出登录", use_container_width=True):
    _reset_session()
    st.rerun()

if page == "智能支持":
    _render_support_center()
elif page == "我的工单":
    _render_ticket_center()
elif page == "知识缺口":
    _render_knowledge_governance()
else:
    _render_system_status()
