package com.haust.ailll.dto;

import lombok.Data;

/**
 * 回答中引用的知识库来源
 */
@Data
public class ReferenceDTO {
    private String source;
    private String chunkId;
    private Double score;
}
