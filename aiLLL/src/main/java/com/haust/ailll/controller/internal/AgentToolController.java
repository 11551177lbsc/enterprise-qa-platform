package com.haust.ailll.controller.internal;

import com.haust.ailll.dto.agenttool.CreateTicketRequest;
import com.haust.ailll.dto.agenttool.NotificationRequest;
import com.haust.ailll.dto.agenttool.UpdateTicketRequest;
import com.haust.ailll.service.AgentToolService;
import com.haust.ailll.util.ResultUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/agent-tools")
public class AgentToolController {

    private final AgentToolService service;

    public AgentToolController(AgentToolService service) {
        this.service = service;
    }

    @GetMapping("/users/me")
    public ResultUtil currentUser(HttpServletRequest request,
                                  @RequestHeader("X-Agent-Invocation-Id") String invocationId) {
        return ResultUtil.success(service.currentUser(userId(request), invocationId));
    }

    @GetMapping("/tickets")
    public ResultUtil listTickets(HttpServletRequest request,
                                  @RequestHeader("X-Agent-Invocation-Id") String invocationId) {
        return ResultUtil.success(service.listTickets(userId(request), invocationId));
    }

    @GetMapping("/tickets/{ticketId}")
    public ResultUtil getTicket(HttpServletRequest request,
                                @PathVariable Long ticketId,
                                @RequestHeader("X-Agent-Invocation-Id") String invocationId) {
        return ResultUtil.success(service.getTicket(userId(request), ticketId, invocationId));
    }

    @GetMapping("/tickets/{ticketId}/timeline")
    public ResultUtil ticketTimeline(HttpServletRequest request,
                                     @PathVariable Long ticketId,
                                     @RequestHeader("X-Agent-Invocation-Id") String invocationId) {
        return ResultUtil.success(service.ticketTimeline(userId(request), ticketId, invocationId));
    }

    @PostMapping("/tickets")
    public ResultUtil createTicket(HttpServletRequest request,
                                   @Valid @RequestBody CreateTicketRequest payload,
                                   @RequestHeader("X-Agent-Invocation-Id") String invocationId) {
        return ResultUtil.success(service.createTicket(userId(request), payload, invocationId));
    }

    @PatchMapping("/tickets/{ticketId}")
    public ResultUtil updateTicket(HttpServletRequest request,
                                   @PathVariable Long ticketId,
                                   @Valid @RequestBody UpdateTicketRequest payload,
                                   @RequestHeader("X-Agent-Invocation-Id") String invocationId) {
        return ResultUtil.success(service.updateTicket(userId(request), ticketId, payload, invocationId));
    }

    @PostMapping("/notifications")
    public ResultUtil enqueueNotification(HttpServletRequest request,
                                          @Valid @RequestBody NotificationRequest payload,
                                          @RequestHeader("X-Agent-Invocation-Id") String invocationId) {
        return ResultUtil.success(service.enqueueNotification(userId(request), payload, invocationId));
    }

    private static Long userId(HttpServletRequest request) {
        Object value = request.getAttribute("authenticatedUserId");
        if (!(value instanceof Long userId)) {
            throw new IllegalStateException("缺少已认证用户上下文");
        }
        return userId;
    }
}
