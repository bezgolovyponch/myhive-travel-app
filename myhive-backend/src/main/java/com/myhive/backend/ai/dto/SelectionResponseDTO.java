package com.myhive.backend.ai.dto;

import com.myhive.backend.dto.VotePoolActivityDTO;

import java.util.List;

/**
 * A picked package resolved into cart rows. {@code tripItems} carries the same shape the quiz pool
 * returns, so the Trip Builder fills itself with the reducer it already has.
 */
public record SelectionResponseDTO(String packageKey, int groupSize, List<VotePoolActivityDTO> tripItems) {
}
