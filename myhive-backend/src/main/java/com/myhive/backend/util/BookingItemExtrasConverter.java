package com.myhive.backend.util;

import com.myhive.backend.entity.BookingItemExtra;
import jakarta.persistence.Converter;
import tools.jackson.core.type.TypeReference;

import java.util.List;

@Converter
public class BookingItemExtrasConverter extends JsonListConverter<BookingItemExtra> {

    public BookingItemExtrasConverter() {
        super(new TypeReference<List<BookingItemExtra>>() {});
    }
}
