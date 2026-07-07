package com.myhive.backend.dto;

/**
 * Outcome of one recompression batch over the R2 bucket.
 *
 * @param processed objects rewritten with a smaller web-sized JPEG
 * @param skipped   objects left untouched (already small, undecodable, or recompression would not shrink them)
 * @param bytesBefore combined size of the processed objects before recompression
 * @param bytesAfter  combined size of the processed objects after recompression
 * @param truncated  true when the batch limit stopped the scan — call again to continue
 */
public record ImageRecompressResult(int processed, int skipped, long bytesBefore, long bytesAfter,
                                    boolean truncated) {
}
