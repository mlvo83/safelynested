package com.learning.learning.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * JSON body posted by the help-assistant widget. The browser keeps the running
 * conversation and replays it with each question; the server validates and trims it
 * before anything reaches the model.
 */
@Data
public class AssistantChatRequest {

    /** The new question. */
    private String message;

    /** Prior turns, oldest first. */
    private List<Turn> history = new ArrayList<>();

    @Data
    public static class Turn {
        /** "user" or "assistant". */
        private String role;
        private String content;
    }
}
