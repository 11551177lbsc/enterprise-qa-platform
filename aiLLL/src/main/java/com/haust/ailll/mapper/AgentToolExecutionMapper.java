package com.haust.ailll.mapper;

import org.apache.ibatis.annotations.*;

import java.util.Map;

@Mapper
public interface AgentToolExecutionMapper {

    @Insert("""
            INSERT IGNORE INTO agent_tool_execution(invocation_id, user_id, tool_name, status)
            VALUES(#{invocationId}, #{userId}, #{toolName}, 'PROCESSING')
            """)
    int reserve(@Param("invocationId") String invocationId,
                @Param("userId") Long userId,
                @Param("toolName") String toolName);

    @Select("SELECT invocation_id, user_id, tool_name, status, response_json, error_message FROM agent_tool_execution WHERE invocation_id = #{invocationId}")
    Map<String, Object> findById(String invocationId);

    @Update("""
            UPDATE agent_tool_execution
            SET status = 'SUCCEEDED', response_json = #{responseJson}, error_message = NULL, updated_at = NOW()
            WHERE invocation_id = #{invocationId} AND status = 'PROCESSING'
            """)
    int complete(@Param("invocationId") String invocationId, @Param("responseJson") String responseJson);

    @Update("""
            UPDATE agent_tool_execution
            SET status = 'FAILED', error_message = #{message}, updated_at = NOW()
            WHERE invocation_id = #{invocationId} AND status = 'PROCESSING'
            """)
    int fail(@Param("invocationId") String invocationId, @Param("message") String message);
}
