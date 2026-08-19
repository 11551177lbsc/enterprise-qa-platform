package com.haust.ailll.dto;

import lombok.Data;
import java.util.List;

/**
 * Python RAG 服务检索响应
 */
@Data
public class RagSearchResponse {
    private List<RagChunkDTO> chunks;
    private String error;
}
