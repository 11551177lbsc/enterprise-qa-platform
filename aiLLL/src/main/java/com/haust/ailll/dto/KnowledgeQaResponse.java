package com.haust.ailll.dto;

import lombok.Data;
import java.util.List;

/**
 * 知识库问答响应
 */
@Data
public class KnowledgeQaResponse {
    private String sessionId;
    private String answer;
    private List<ReferenceDTO> references;
}
