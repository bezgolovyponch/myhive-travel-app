package com.myhive.backend.util;

import com.myhive.backend.entity.ActivityExtra;
import com.myhive.backend.entity.ExtraPricing;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityExtrasConverterTest {

    private final ActivityExtrasConverter converter = new ActivityExtrasConverter();

    @Test
    void roundTripsAListOfExtras() {
        List<ActivityExtra> expected = List.of(
                new ActivityExtra("show", "Show", "15 min", new BigDecimal("250.00"), ExtraPricing.PER_GROUP,
                        Map.of("de", Map.of("name", "Vorfuehrung"))),
                new ActivityExtra("photo", "Photo", null, new BigDecimal("15.00"), ExtraPricing.PER_PERSON, null));

        String column = converter.convertToDatabaseColumn(expected);
        List<ActivityExtra> result = converter.convertToEntityAttribute(column);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void nullAndEmptyListsAreStoredAsNullColumn() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToDatabaseColumn(List.of())).isNull();
    }

    @Test
    void emptyJsonArrayColumnReadsAsEmptyList() {
        assertThat(converter.convertToEntityAttribute("[]")).isEmpty();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
        assertThat(converter.convertToEntityAttribute("  ")).isNull();
    }
}
