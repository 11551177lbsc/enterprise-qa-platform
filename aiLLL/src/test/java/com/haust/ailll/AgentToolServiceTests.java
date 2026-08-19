package com.haust.ailll;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.haust.ailll.dto.agenttool.CreateTicketRequest;
import com.haust.ailll.entity.SupportTicket;
import com.haust.ailll.mapper.AgentToolExecutionMapper;
import com.haust.ailll.mapper.NotificationOutboxMapper;
import com.haust.ailll.mapper.SupportTicketMapper;
import com.haust.ailll.mapper.UserMapper;
import com.haust.ailll.service.AgentToolService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentToolServiceTests {

    @Test
    void createTicketReservesIdempotencyKeyBeforeBusinessWrite() {
        UserMapper userMapper = mock(UserMapper.class);
        SupportTicketMapper ticketMapper = mock(SupportTicketMapper.class);
        NotificationOutboxMapper notificationMapper = mock(NotificationOutboxMapper.class);
        AgentToolExecutionMapper executionMapper = mock(AgentToolExecutionMapper.class);
        AgentToolService service = new AgentToolService(
                userMapper, ticketMapper, notificationMapper, executionMapper, new ObjectMapper());

        String invocationId = "11111111-1111-1111-1111-111111111111";
        when(executionMapper.reserve(invocationId, 7L, "create_support_ticket")).thenReturn(1);
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

        Object result = service.createTicket(
                7L,
                new CreateTicketRequest("无法回充", "机器人无法回到充电座", "HIGH"),
                invocationId);

        assertSame(stored, result);
        verify(executionMapper).reserve(invocationId, 7L, "create_support_ticket");
        verify(ticketMapper).insert(any(SupportTicket.class));
        verify(executionMapper).complete(eq(invocationId), contains("无法回充"));
    }
}
