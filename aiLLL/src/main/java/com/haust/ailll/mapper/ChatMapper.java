package com.haust.ailll.mapper;

import com.haust.ailll.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ChatMapper {

    void insert(ChatMessage chatMessage);

    List<ChatMessage> findByUserId(Long userId);

}