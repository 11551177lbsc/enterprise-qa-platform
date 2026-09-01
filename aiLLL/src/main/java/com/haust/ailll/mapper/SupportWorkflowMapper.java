package com.haust.ailll.mapper;

import com.haust.ailll.entity.SupportTicketEvent;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface SupportWorkflowMapper {

    @Insert("""
            INSERT INTO support_ticket_detail(
                ticket_id, category, product_model, sla_due_at,
                knowledge_confidence, escalation_reason
            ) VALUES(
                #{ticketId}, #{category}, #{productModel}, #{slaDueAt},
                #{knowledgeConfidence}, #{escalationReason}
            )
            """)
    int insertTicketDetail(@Param("ticketId") Long ticketId,
                           @Param("category") String category,
                           @Param("productModel") String productModel,
                           @Param("slaDueAt") LocalDateTime slaDueAt,
                           @Param("knowledgeConfidence") Double knowledgeConfidence,
                           @Param("escalationReason") String escalationReason);

    @Insert("""
            INSERT INTO support_ticket_event(
                ticket_id, actor_user_id, event_type, from_status, to_status, content
            ) VALUES(
                #{ticketId}, #{actorUserId}, #{eventType}, #{fromStatus}, #{toStatus}, #{content}
            )
            """)
    int insertTicketEvent(@Param("ticketId") Long ticketId,
                          @Param("actorUserId") Long actorUserId,
                          @Param("eventType") String eventType,
                          @Param("fromStatus") String fromStatus,
                          @Param("toStatus") String toStatus,
                          @Param("content") String content);

    @Select("""
            SELECT e.*
            FROM support_ticket_event e
            INNER JOIN support_ticket t ON t.id = e.ticket_id
            WHERE e.ticket_id = #{ticketId} AND t.user_id = #{userId}
            ORDER BY e.created_at ASC, e.id ASC
            """)
    List<SupportTicketEvent> findOwnedTimeline(@Param("userId") Long userId,
                                               @Param("ticketId") Long ticketId);

    @Select("SELECT outcome FROM answer_feedback WHERE run_id = #{runId} AND user_id = #{userId}")
    String findFeedbackOutcome(@Param("runId") String runId, @Param("userId") Long userId);

    @Insert("""
            INSERT INTO answer_feedback(user_id, run_id, question, outcome, comment)
            VALUES(#{userId}, #{runId}, #{question}, #{outcome}, #{comment})
            ON DUPLICATE KEY UPDATE
                question = VALUES(question), outcome = VALUES(outcome),
                comment = VALUES(comment), updated_at = NOW()
            """)
    int upsertFeedback(@Param("userId") Long userId,
                       @Param("runId") String runId,
                       @Param("question") String question,
                       @Param("outcome") String outcome,
                       @Param("comment") String comment);

    @Insert("""
            INSERT INTO knowledge_gap(
                fingerprint, sample_question, occurrence_count,
                last_confidence, last_run_id, status
            ) VALUES(
                #{fingerprint}, #{question}, 1, #{confidence}, #{runId}, 'OPEN'
            )
            ON DUPLICATE KEY UPDATE
                sample_question = VALUES(sample_question),
                occurrence_count = occurrence_count + 1,
                last_confidence = VALUES(last_confidence),
                last_run_id = VALUES(last_run_id),
                status = 'OPEN',
                last_seen_at = NOW()
            """)
    int upsertKnowledgeGap(@Param("fingerprint") String fingerprint,
                           @Param("question") String question,
                           @Param("confidence") Double confidence,
                           @Param("runId") String runId);

    @Select("SELECT role FROM user_role WHERE user_id = #{userId}")
    String findRole(@Param("userId") Long userId);

    @Select("""
            SELECT id, sample_question, occurrence_count, last_confidence,
                   last_run_id, status, first_seen_at, last_seen_at
            FROM knowledge_gap
            WHERE status = 'OPEN'
            ORDER BY occurrence_count DESC, last_seen_at DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> findOpenKnowledgeGaps(@Param("limit") int limit);

    @Update("UPDATE knowledge_gap SET status = #{status}, last_seen_at = NOW() WHERE id = #{gapId}")
    int updateKnowledgeGapStatus(@Param("gapId") Long gapId, @Param("status") String status);
}
