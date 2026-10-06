package com.myhive.backend.ai.dto;

/**
 * One theme the trip draft lacks next to the ready-made packages of its tier: a catalog category, with
 * its name in the session's language. The chat shows it as a "what next" tag; tapping it asks for it.
 */
public record DraftGapDTO(String categorySlug, String name) {
}
