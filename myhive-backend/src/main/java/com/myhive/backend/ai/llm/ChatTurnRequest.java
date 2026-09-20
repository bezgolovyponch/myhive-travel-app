package com.myhive.backend.ai.llm;

import com.myhive.backend.ai.model.Brief;

import java.util.List;

public record ChatTurnRequest(String locale, String destinationName, List<String> categorySlugs, Brief brief,
                              List<ChatMessage> history) {
}
