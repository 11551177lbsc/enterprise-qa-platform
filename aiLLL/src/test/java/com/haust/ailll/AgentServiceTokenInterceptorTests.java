package com.haust.ailll;

import com.haust.ailll.interceptor.AgentServiceTokenInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentServiceTokenInterceptorTests {

    private static final String TOKEN = "abcdef0123456789abcdef0123456789";

    @Test
    void acceptsExactServiceToken() throws Exception {
        AgentServiceTokenInterceptor interceptor = new AgentServiceTokenInterceptor(TOKEN);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/agent-tools/users/me");
        request.addHeader("X-Agent-Service-Token", TOKEN);

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
    }

    @Test
    void rejectsWrongServiceToken() throws Exception {
        AgentServiceTokenInterceptor interceptor = new AgentServiceTokenInterceptor(TOKEN);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/agent-tools/users/me");
        request.addHeader("X-Agent-Service-Token", "wrong");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(403, response.getStatus());
    }
}
