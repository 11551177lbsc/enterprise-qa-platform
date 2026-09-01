package com.haust.ailll.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SupportTicketEvent {
    private Long id;
    private Long ticketId;
    private Long actorUserId;
    private String eventType;
    private String fromStatus;
    private String toStatus;
    private String content;
    private LocalDateTime createdAt;
}
