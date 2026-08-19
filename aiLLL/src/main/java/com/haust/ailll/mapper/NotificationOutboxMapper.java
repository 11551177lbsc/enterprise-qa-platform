package com.haust.ailll.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface NotificationOutboxMapper {

    @Insert("""
            INSERT INTO notification_outbox(user_id, ticket_id, channel, payload_json, status, idempotency_key)
            VALUES(#{userId}, #{ticketId}, #{channel}, #{payloadJson}, 'PENDING', #{idempotencyKey})
            """)
    int insert(@Param("userId") Long userId,
               @Param("ticketId") Long ticketId,
               @Param("channel") String channel,
               @Param("payloadJson") String payloadJson,
               @Param("idempotencyKey") String idempotencyKey);
}
