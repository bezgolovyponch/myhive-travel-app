package com.myhive.backend.ai.llm;

/** One turn of the chat, stored as JSON in graph state; roles are plain strings on purpose. */
public record ChatMessage(String role, String content, String at) {

    public static final String USER = "USER";
    public static final String ASSISTANT = "ASSISTANT";
}
