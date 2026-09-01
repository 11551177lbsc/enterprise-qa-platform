package com.haust.ailll.controller;

import com.haust.ailll.dto.agenttool.KnowledgeGapStatusRequest;
import com.haust.ailll.service.SupportWorkflowService;
import com.haust.ailll.util.ResultUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/support")
public class SupportWorkflowController {

    private final SupportWorkflowService service;

    public SupportWorkflowController(SupportWorkflowService service) {
        this.service = service;
    }

    @GetMapping("/me")
    public ResultUtil currentRole(HttpServletRequest request) {
        return ResultUtil.success(service.currentRole(userId(request)));
    }

    @GetMapping("/knowledge-gaps")
    public ResultUtil listKnowledgeGaps(HttpServletRequest request,
                                        @RequestParam(defaultValue = "20") int limit) {
        return ResultUtil.success(service.listKnowledgeGaps(userId(request), limit));
    }

    @PatchMapping("/knowledge-gaps/{gapId}")
    public ResultUtil updateKnowledgeGap(HttpServletRequest request,
                                         @PathVariable Long gapId,
                                         @Valid @RequestBody KnowledgeGapStatusRequest payload) {
        return ResultUtil.success(service.updateKnowledgeGapStatus(userId(request), gapId, payload.status()));
    }

    private static Long userId(HttpServletRequest request) {
        Object value = request.getAttribute("authenticatedUserId");
        if (!(value instanceof Long userId)) {
            throw new IllegalStateException("缺少已认证用户上下文");
        }
        return userId;
    }
}
