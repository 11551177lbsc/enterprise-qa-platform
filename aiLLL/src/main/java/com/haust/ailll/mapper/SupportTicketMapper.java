package com.haust.ailll.mapper;

import com.haust.ailll.entity.SupportTicket;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface SupportTicketMapper {

    @Select("""
            SELECT t.*, d.category, d.product_model, d.sla_due_at, d.knowledge_confidence,
                   d.escalation_reason, d.resolution_code, d.assigned_to
            FROM support_ticket t
            LEFT JOIN support_ticket_detail d ON d.ticket_id = t.id
            WHERE t.user_id = #{userId}
            ORDER BY t.updated_at DESC
            LIMIT 100
            """)
    List<SupportTicket> findByUserId(Long userId);

    @Select("""
            SELECT t.*, d.category, d.product_model, d.sla_due_at, d.knowledge_confidence,
                   d.escalation_reason, d.resolution_code, d.assigned_to
            FROM support_ticket t
            LEFT JOIN support_ticket_detail d ON d.ticket_id = t.id
            WHERE t.id = #{id} AND t.user_id = #{userId}
            """)
    SupportTicket findOwned(@Param("id") Long id, @Param("userId") Long userId);

    @Insert("""
            INSERT INTO support_ticket(user_id, title, description, priority, status)
            VALUES(#{userId}, #{title}, #{description}, #{priority}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SupportTicket ticket);

    @Update("""
            UPDATE support_ticket
            SET title = #{title}, description = #{description}, priority = #{priority},
                status = #{status}, version = version + 1, updated_at = NOW()
            WHERE id = #{id} AND user_id = #{userId} AND version = #{version}
            """)
    int updateOwned(SupportTicket ticket);
}
