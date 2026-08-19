package com.haust.ailll.controller;

import com.haust.ailll.dto.ChatRequestDTO;
import com.haust.ailll.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/chat")
public class ChatController {

    @Autowired
    private ChatService chatService;

    @PostMapping(value = "/stream", produces = "text/event-stream")
    public SseEmitter chat(@RequestBody ChatRequestDTO dto,
                           @RequestHeader("userId") Long userId,@RequestHeader("Authorization") String token) {

        System.out.println("进入流式chat接口");
        System.out.println("token="+token);
        System.out.println("userId="+userId);
        // 超时时间 60秒
        SseEmitter emitter = new SseEmitter(60000L);

        new Thread(() -> {
            try {

                String answer = chatService.chat(userId, dto.getQuestion());

                // 模拟AI打字效果
                for (char c : answer.toCharArray()) {

                    emitter.send(String.valueOf(c));

                    Thread.sleep(30);
                }

                emitter.complete();

            } catch (Exception e) {

                emitter.completeWithError(e);
            }

        }).start();

        return emitter;
    }
}