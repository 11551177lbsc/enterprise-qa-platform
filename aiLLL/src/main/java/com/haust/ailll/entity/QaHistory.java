package com.haust.ailll.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库问答历史记录实体
 */
@Data
public class QaHistory {

    private Long id;

    private Long userId;

    private String sessionId;

    private String question;

    private String answer;

    private String referencesJson;

    private String status;

    private String errorMessage;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    // ===== 状态常量 =====
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_NO_CONTEXT = "NO_CONTEXT";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_FAILED = "FAILED";

    // ===== Lombok @Data 生成 getter/setter，以下为 IDE 兼容保留 =====

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public String getReferencesJson() {
        return referencesJson;
    }

    public void setReferencesJson(String referencesJson) {
        this.referencesJson = referencesJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
