package com.haust.ailll.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SupportTicket {
    private Long id;
    private Long userId;
    private String title;
    private String description;
    private String priority;
    private String status;
    private Integer version;
    private String category;
    private String productModel;
    private LocalDateTime slaDueAt;
    private Double knowledgeConfidence;
    private String escalationReason;
    private String resolutionCode;
    private Long assignedTo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
