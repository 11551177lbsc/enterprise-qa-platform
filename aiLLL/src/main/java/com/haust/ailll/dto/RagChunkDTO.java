package com.haust.ailll.dto;

import lombok.Data;

/**
 * RAG 检索到的单个文档片段
 */
@Data
public class RagChunkDTO {
    private String chunkId;
    private String content;
    private Double score;
    private String source;
}
