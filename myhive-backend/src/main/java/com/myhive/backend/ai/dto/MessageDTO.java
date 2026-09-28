package com.myhive.backend.ai.dto;

/** One stored chat turn. {@code at} is the ISO-8601 instant the graph stamped, passed through as text. */
public record MessageDTO(String role, String content, String at) {
}
