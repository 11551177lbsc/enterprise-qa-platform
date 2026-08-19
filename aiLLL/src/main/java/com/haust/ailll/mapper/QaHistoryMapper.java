package com.haust.ailll.mapper;

import com.haust.ailll.entity.QaHistory;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 知识库问答历史 Mapper
 */
@Mapper
public interface QaHistoryMapper {

    void insert(QaHistory qaHistory);

    QaHistory findById(Long id);

    void updateStatus(QaHistory qaHistory);

    List<QaHistory> findBySessionId(String sessionId);

    List<QaHistory> findByUserId(Long userId);
}
