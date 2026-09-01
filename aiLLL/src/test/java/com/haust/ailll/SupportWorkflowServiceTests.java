package com.haust.ailll;

import com.haust.ailll.dto.agenttool.AnswerFeedbackRequest;
import com.haust.ailll.mapper.SupportWorkflowMapper;
import com.haust.ailll.service.SupportWorkflowService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SupportWorkflowServiceTests {

    @Test
    void unresolvedFeedbackCreatesKnowledgeGapOnlyOnce() {
        SupportWorkflowMapper mapper = mock(SupportWorkflowMapper.class);
        SupportWorkflowService service = new SupportWorkflowService(mapper);
        AnswerFeedbackRequest request = new AnswerFeedbackRequest(
                "11111111-1111-1111-1111-111111111111",
                "机器出现未知故障",
                "UNRESOLVED",
                "知识库没有对应步骤",
                0.41
        );

        when(mapper.findFeedbackOutcome(request.runId(), 7L)).thenReturn(null);
        var first = service.recordFeedback(7L, request);
        assertTrue((Boolean) first.get("knowledgeGapRecorded"));
        verify(mapper).upsertKnowledgeGap(anyString(), eq(request.question()), eq(0.41), eq(request.runId()));

        when(mapper.findFeedbackOutcome(request.runId(), 7L)).thenReturn("UNRESOLVED");
        var repeated = service.recordFeedback(7L, request);
        assertFalse((Boolean) repeated.get("knowledgeGapRecorded"));
    }

    @Test
    void ordinaryUserCannotReadKnowledgeGapDashboard() {
        SupportWorkflowMapper mapper = mock(SupportWorkflowMapper.class);
        SupportWorkflowService service = new SupportWorkflowService(mapper);
        when(mapper.findRole(7L)).thenReturn(null);

        assertThrows(SecurityException.class, () -> service.listKnowledgeGaps(7L, 20));
        verify(mapper, never()).findOpenKnowledgeGaps(20);
    }
}
