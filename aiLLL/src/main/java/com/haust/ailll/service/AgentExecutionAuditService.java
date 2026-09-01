package com.haust.ailll.service;

import com.haust.ailll.mapper.AgentToolExecutionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class AgentExecutionAuditService {

    private final AgentToolExecutionMapper mapper;

    public AgentExecutionAuditService(AgentToolExecutionMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean reserve(String invocationId, Long userId, String toolName) {
        return mapper.reserve(invocationId, userId, toolName) == 1;
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Map<String, Object> find(String invocationId) {
        return mapper.findById(invocationId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(String invocationId, String message) {
        mapper.fail(invocationId, message);
    }
}
