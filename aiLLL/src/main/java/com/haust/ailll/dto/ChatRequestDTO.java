package com.haust.ailll.dto;

import lombok.Data;

@Data
public class ChatRequestDTO {

    private String question;

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }
}