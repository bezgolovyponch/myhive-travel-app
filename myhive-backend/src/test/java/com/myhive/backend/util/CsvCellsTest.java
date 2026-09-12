package com.myhive.backend.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CsvCellsTest {

    @Test
    void nullSafe_mapsNullToEmpty() {
        assertThat(CsvCells.nullSafe(null)).isEmpty();
        assertThat(CsvCells.nullSafe("x")).isEqualTo("x");
    }

    @Test
    void sanitize_prefixesFormulaTriggersWithApostrophe() {
        assertThat(CsvCells.sanitize("=1+1")).isEqualTo("'=1+1");
        assertThat(CsvCells.sanitize("+49 170")).isEqualTo("'+49 170");
        assertThat(CsvCells.sanitize("-x")).isEqualTo("'-x");
        assertThat(CsvCells.sanitize("@me")).isEqualTo("'@me");
        assertThat(CsvCells.sanitize("\tx")).isEqualTo("'\tx");
        assertThat(CsvCells.sanitize("\rx")).isEqualTo("'\rx");
        assertThat(CsvCells.sanitize("plain")).isEqualTo("plain");
        assertThat(CsvCells.sanitize("")).isEmpty();
    }
}
