package com.haust.ailll;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.haust.ailll.dto.agenttool.CreateTicketRequest;
import com.haust.ailll.entity.SupportTicket;
import com.haust.ailll.mapper.AgentToolExecutionMapper;
import com.haust.ailll.mapper.NotificationOutboxMapper;
import com.haust.ailll.mapper.SupportTicketMapper;
import com.haust.ailll.mapper.SupportWorkflowMapper;
import com.haust.ailll.mapper.UserMapper;
import com.haust.ailll.service.AgentToolService;
import com.haust.ailll.service.AgentExecutionAuditService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;

class AgentToolServiceTests {

    @Test
    void createTicketReservesIdempotencyKeyBeforeBusinessWrite() {
        UserMapper userMapper = mock(UserMapper.class);
        SupportTicketMapper ticketMapper = mock(SupportTicketMapper.class);
        NotificationOutboxMapper notificationMapper = mock(NotificationOutboxMapper.class);
        AgentToolExecutionMapper executionMapper = mock(AgentToolExecutionMapper.class);
        SupportWorkflowMapper workflowMapper = mock(SupportWorkflowMapper.class);
        AgentExecutionAuditService auditService = new AgentExecutionAuditService(executionMapper);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        AgentToolService service = new AgentToolService(
                userMapper, ticketMapper, notificationMapper, workflowMapper, executionMapper,
                auditService, transactionManager, new ObjectMapper());

        String invocationId = "11111111-1111-1111-1111-111111111111";
        when(executionMapper.reserve(invocationId, 7L, "create_support_ticket")).thenReturn(1);
        when(executionMapper.complete(eq(invocationId), anyString())).thenReturn(1);
        doAnswer(invocation -> {
            SupportTicket ticket = invocation.getArgument(0);
            ticket.setId(42L);
            return 1;
        }).when(ticketMapper).insert(any(SupportTicket.class));
        SupportTicket stored = new SupportTicket();
        stored.setId(42L);
        stored.setUserId(7L);
        stored.setTitle("无法回充");
        stored.setDescription("机器人无法回到充电座");
        stored.setPriority("HIGH");
        stored.setStatus("OPEN");
        when(ticketMapper.findOwned(42L, 7L)).thenReturn(stored);

        LocalDateTime beforeCreate = LocalDateTime.now();
        Object result = service.createTicket(
                7L,
                new CreateTicketRequest("无法回充", "机器人无法回到充电座", "HIGH"),
                invocationId);

        assertSame(stored, result);
        verify(executionMapper).reserve(invocationId, 7L, "create_support_ticket");
        verify(ticketMapper).insert(any(SupportTicket.class));
        ArgumentCaptor<LocalDateTime> slaDueAt = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(workflowMapper).insertTicketDetail(
                eq(42L), eq("OTHER"), isNull(), slaDueAt.capture(), isNull(), isNull());
        assertTrue(slaDueAt.getValue().isAfter(beforeCreate.plusHours(23)));
        assertTrue(slaDueAt.getValue().isBefore(beforeCreate.plusHours(25)));
        verify(workflowMapper).insertTicketEvent(
                eq(42L), eq(7L), eq("CREATED"), isNull(), eq("OPEN"), anyString());
        verify(executionMapper).complete(eq(invocationId), contains("无法回充"));
    }

    @Test
    void failedBusinessTransactionIsKeptInIndependentAuditRecord() {
        UserMapper userMapper = mock(UserMapper.class);
        SupportTicketMapper ticketMapper = mock(SupportTicketMapper.class);
        NotificationOutboxMapper notificationMapper = mock(NotificationOutboxMapper.class);
        AgentToolExecutionMapper executionMapper = mock(AgentToolExecutionMapper.class);
        SupportWorkflowMapper workflowMapper = mock(SupportWorkflowMapper.class);
        AgentExecutionAuditService auditService = new AgentExecutionAuditService(executionMapper);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        AgentToolService service = new AgentToolService(
                userMapper, ticketMapper, notificationMapper, workflowMapper, executionMapper,
                auditService, transactionManager, new ObjectMapper());

        String invocationId = "22222222-2222-2222-2222-222222222222";
        when(executionMapper.reserve(invocationId, 7L, "create_support_ticket")).thenReturn(1);
        doThrow(new IllegalStateException("database unavailable"))
                .when(ticketMapper).insert(any(SupportTicket.class));

        assertThrows(IllegalStateException.class, () -> service.createTicket(
                7L,
                new CreateTicketRequest("无法回充", "机器人无法回到充电座", "HIGH"),
                invocationId));

        verify(transactionManager).rollback(any());
        verify(executionMapper).fail(invocationId, "database unavailable");
        verify(executionMapper, never()).complete(eq(invocationId), anyString());
    }
}
