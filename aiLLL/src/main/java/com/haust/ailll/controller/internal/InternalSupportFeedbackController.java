package com.haust.ailll.controller.internal;

import com.haust.ailll.dto.agenttool.AnswerFeedbackRequest;
import com.haust.ailll.service.SupportWorkflowService;
import com.haust.ailll.util.ResultUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/agent-tools/support-feedback")
public class InternalSupportFeedbackController {

    private final SupportWorkflowService service;

    public InternalSupportFeedbackController(SupportWorkflowService service) {
        this.service = service;
    }

    @PostMapping
    public ResultUtil recordFeedback(HttpServletRequest request,
                                     @Valid @RequestBody AnswerFeedbackRequest payload) {
        return ResultUtil.success(service.recordFeedback(userId(request), payload));
    }

    private static Long userId(HttpServletRequest request) {
        Object value = request.getAttribute("authenticatedUserId");
        if (!(value instanceof Long userId)) {
            throw new IllegalStateException("缺少已认证用户上下文");
        }
        return userId;
    }
}
