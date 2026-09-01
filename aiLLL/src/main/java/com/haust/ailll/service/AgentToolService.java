package com.haust.ailll.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haust.ailll.dto.agenttool.CreateTicketRequest;
import com.haust.ailll.dto.agenttool.NotificationRequest;
import com.haust.ailll.dto.agenttool.UpdateTicketRequest;
import com.haust.ailll.dto.agenttool.UserProfileResponse;
import com.haust.ailll.entity.SupportTicket;
import com.haust.ailll.entity.User;
import com.haust.ailll.mapper.NotificationOutboxMapper;
import com.haust.ailll.mapper.AgentToolExecutionMapper;
import com.haust.ailll.mapper.SupportTicketMapper;
import com.haust.ailll.mapper.SupportWorkflowMapper;
import com.haust.ailll.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.time.LocalDateTime;

@Service
public class AgentToolService {

    private final UserMapper userMapper;
    private final SupportTicketMapper ticketMapper;
    private final NotificationOutboxMapper notificationMapper;
    private final SupportWorkflowMapper workflowMapper;
    private final AgentToolExecutionMapper executionMapper;
    private final AgentExecutionAuditService auditService;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    public AgentToolService(UserMapper userMapper,
                            SupportTicketMapper ticketMapper,
                            NotificationOutboxMapper notificationMapper,
                            SupportWorkflowMapper workflowMapper,
                            AgentToolExecutionMapper executionMapper,
                            AgentExecutionAuditService auditService,
                            PlatformTransactionManager transactionManager,
                            ObjectMapper objectMapper) {
        this.userMapper = userMapper;
        this.ticketMapper = ticketMapper;
        this.notificationMapper = notificationMapper;
        this.workflowMapper = workflowMapper;
        this.executionMapper = executionMapper;
        this.auditService = auditService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.objectMapper = objectMapper;
    }

    public Object currentUser(Long userId, String invocationId) {
        return idempotent(invocationId, userId, "get_current_user_profile", () -> {
            User user = requireUser(userId);
            return new UserProfileResponse(user.getId(), user.getUsername(), user.getEmail());
        });
    }

    public Object listTickets(Long userId, String invocationId) {
        return idempotent(invocationId, userId, "list_support_tickets", () -> ticketMapper.findByUserId(userId));
    }

    public Object getTicket(Long userId, Long ticketId, String invocationId) {
        return idempotent(invocationId, userId, "get_support_ticket", () -> requireTicket(userId, ticketId));
    }

    public Object ticketTimeline(Long userId, Long ticketId, String invocationId) {
        return idempotent(invocationId, userId, "get_ticket_timeline", () -> {
            requireTicket(userId, ticketId);
            return workflowMapper.findOwnedTimeline(userId, ticketId);
        });
    }

    public Object createTicket(Long userId, CreateTicketRequest request, String invocationId) {
        return idempotent(invocationId, userId, "create_support_ticket", () -> {
            SupportTicket ticket = new SupportTicket();
            ticket.setUserId(userId);
            ticket.setTitle(request.title().trim());
            ticket.setDescription(request.description().trim());
            ticket.setPriority(request.priority() == null ? "MEDIUM" : request.priority());
            ticket.setStatus("OPEN");
            ticketMapper.insert(ticket);
            workflowMapper.insertTicketDetail(
                    ticket.getId(),
                    normalizeCategory(request.category()),
                    trimToNull(request.productModel()),
                    LocalDateTime.now().plusHours(slaHours(ticket.getPriority())),
                    request.knowledgeConfidence(),
                    trimToNull(request.escalationReason())
            );
            workflowMapper.insertTicketEvent(
                    ticket.getId(), userId, "CREATED", null, "OPEN", "用户创建支持工单"
            );
            return ticketMapper.findOwned(ticket.getId(), userId);
        });
    }

    public Object updateTicket(Long userId, Long ticketId, UpdateTicketRequest request, String invocationId) {
        return idempotent(invocationId, userId, "update_support_ticket", () -> {
            SupportTicket ticket = requireTicket(userId, ticketId);
            String previousStatus = ticket.getStatus();
            if (request.title() == null && request.description() == null
                    && request.priority() == null && request.status() == null) {
                throw new IllegalArgumentException("至少提供一个要修改的字段");
            }
            if (request.title() != null) ticket.setTitle(request.title().trim());
            if (request.description() != null) ticket.setDescription(request.description().trim());
            if (request.priority() != null) ticket.setPriority(request.priority());
            if (request.status() != null) ticket.setStatus(request.status());
            if (ticketMapper.updateOwned(ticket) != 1) {
                throw new IllegalStateException("工单已被其他请求修改，请重新查询后再试");
            }
            String eventType = Objects.equals(previousStatus, ticket.getStatus()) ? "UPDATED" : "STATUS_CHANGED";
            workflowMapper.insertTicketEvent(
                    ticketId,
                    userId,
                    eventType,
                    previousStatus,
                    ticket.getStatus(),
                    "用户通过支持 Agent 更新工单"
            );
            return ticketMapper.findOwned(ticketId, userId);
        });
    }

    public Object enqueueNotification(Long userId, NotificationRequest request, String invocationId) {
        return idempotent(invocationId, userId, "send_ticket_notification", () -> {
            SupportTicket ticket = requireTicket(userId, request.ticketId());
            String channel = request.channel() == null ? "IN_APP" : request.channel();
            String payload = writeJson(Map.of(
                    "ticketId", ticket.getId(),
                    "title", ticket.getTitle(),
                    "status", ticket.getStatus()
            ));
            notificationMapper.insert(userId, ticket.getId(), channel, payload, invocationId);
            return Map.of(
                    "queued", true,
                    "channel", channel,
                    "ticketId", ticket.getId(),
                    "note", "通知仅写入 outbox，当前版本不会直接发送外部邮件"
            );
        });
    }

    private Object idempotent(String invocationId, Long userId, String toolName, Supplier<Object> action) {
        validateInvocationId(invocationId);
        if (!auditService.reserve(invocationId, userId, toolName)) {
            Map<String, Object> existing = auditService.find(invocationId);
            if (existing == null
                    || !Objects.equals(((Number) existing.get("user_id")).longValue(), userId)
                    || !Objects.equals(existing.get("tool_name"), toolName)) {
                throw new IllegalStateException("幂等键已被其他调用使用");
            }
            if ("SUCCEEDED".equals(existing.get("status"))) {
                return readJson(String.valueOf(existing.get("response_json")));
            }
            throw new IllegalStateException("相同工具调用正在执行或此前已失败，请使用新的运行发起重试");
        }

        try {
            return transactionTemplate.execute(status -> {
                Object result = action.get();
                if (executionMapper.complete(invocationId, writeJson(result)) != 1) {
                    throw new IllegalStateException("工具审计记录完成状态更新失败");
                }
                return result;
            });
        } catch (RuntimeException ex) {
            try {
                auditService.fail(invocationId, abbreviate(ex.getMessage()));
            } catch (RuntimeException auditException) {
                ex.addSuppressed(auditException);
            }
            throw ex;
        }
    }

    private User requireUser(Long userId) {
        User user = userMapper.findById(userId);
        if (user == null) throw new IllegalArgumentException("用户不存在");
        return user;
    }

    private SupportTicket requireTicket(Long userId, Long ticketId) {
        SupportTicket ticket = ticketMapper.findOwned(ticketId, userId);
        if (ticket == null) throw new IllegalArgumentException("工单不存在或无权访问");
        return ticket;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("结果序列化失败", ex);
        }
    }

    private Object readJson(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<Object>() { });
        } catch (Exception ex) {
            throw new IllegalStateException("历史幂等结果解析失败", ex);
        }
    }

    private static void validateInvocationId(String value) {
        if (value == null || !value.matches("[A-Za-z0-9-]{16,64}")) {
            throw new IllegalArgumentException("X-Agent-Invocation-Id 格式无效");
        }
    }

    private static String abbreviate(String value) {
        String safe = value == null ? "未知错误" : value;
        return safe.length() <= 500 ? safe : safe.substring(0, 500);
    }

    private static String normalizeCategory(String value) {
        return value == null || value.isBlank() ? "OTHER" : value;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static long slaHours(String priority) {
        return switch (priority) {
            case "URGENT" -> 4;
            case "HIGH" -> 24;
            case "LOW" -> 120;
            default -> 72;
        };
    }
}
