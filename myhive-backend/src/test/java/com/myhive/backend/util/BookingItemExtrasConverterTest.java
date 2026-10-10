package com.myhive.backend.util;

import com.myhive.backend.entity.BookingItemExtra;
import com.myhive.backend.entity.ExtraPricing;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BookingItemExtrasConverterTest {

    private final BookingItemExtrasConverter converter = new BookingItemExtrasConverter();

    @Test
    void roundTripsTheBookedSnapshot() {
        List<BookingItemExtra> expected = List.of(
                new BookingItemExtra("private-show", "Private show", new BigDecimal("250.00"), ExtraPricing.PER_GROUP),
                new BookingItemExtra("photographer", "Photographer", new BigDecimal("15.50"), ExtraPricing.PER_PERSON));

        String column = converter.convertToDatabaseColumn(expected);
        List<BookingItemExtra> result = converter.convertToEntityAttribute(column);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void anItemWithoutExtrasIsStoredAsANullColumn() {
        assertThat(converter.convertToDatabaseColumn(List.of())).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
