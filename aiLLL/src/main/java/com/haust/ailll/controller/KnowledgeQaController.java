package com.haust.ailll.controller;

import com.haust.ailll.dto.KnowledgeQaRequest;
import com.haust.ailll.dto.KnowledgeQaResponse;
import com.haust.ailll.dto.QaAsyncResponse;
import com.haust.ailll.service.KnowledgeQaService;
import com.haust.ailll.util.ResultUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 知识库问答控制器
 *
 * 同步接口: POST /api/chat/ask     — 阻塞等待 RAG+LLM 完整流程
 * 异步接口: POST /api/chat/ask-async — 投递 MQ 后立即返回 taskId
 *
 * JWT 认证由 JwtInterceptor 自动拦截（路径匹配 /**）。
 */
@RestController
@RequestMapping("/api/chat")
public class KnowledgeQaController {

    @Autowired
    private KnowledgeQaService knowledgeQaService;

    /**
     * 同步知识库问答
     */
    @PostMapping("/ask")
    public ResultUtil ask(@RequestBody KnowledgeQaRequest request,
                          HttpServletRequest httpRequest) {
        Long userId = authenticatedUserId(httpRequest);
        KnowledgeQaResponse response = knowledgeQaService.ask(
                userId,
                request.getKnowledgeBaseId(),
                request.getSessionId(),
                request.getQuestion()
        );
        return ResultUtil.success(response);
    }

    /**
     * 异步知识库问答
     * 投递任务到 RabbitMQ 后立即返回 taskId，结果通过 WebSocket 推送。
     */
    @PostMapping("/ask-async")
    public ResultUtil askAsync(@RequestBody KnowledgeQaRequest request,
                               HttpServletRequest httpRequest) {
        Long userId = authenticatedUserId(httpRequest);
        QaAsyncResponse response = knowledgeQaService.askAsync(
                userId,
                request.getKnowledgeBaseId(),
                request.getSessionId(),
                request.getQuestion()
        );

        if ("REJECTED".equals(response.getStatus())) {
            return ResultUtil.error("请求过于频繁，请稍后再试");
        }
        return ResultUtil.success(response);
    }

    /**
     * 从 Authorization 头中提取 userId，解析失败返回 null。
     */
    private Long authenticatedUserId(HttpServletRequest request) {
        Object value = request.getAttribute("authenticatedUserId");
        if (!(value instanceof Long userId)) {
            throw new IllegalStateException("缺少已认证用户上下文");
        }
        return userId;
    }
}
