package com.myhive.backend.ai.llm;

import com.myhive.backend.ai.catalog.CatalogActivity;
import com.myhive.backend.ai.model.Brief;

import java.util.List;

public record PlanRequest(String locale, String destinationName, Brief brief, List<CatalogActivity> catalog,
                          List<ChatMessage> recentHistory) {
}
