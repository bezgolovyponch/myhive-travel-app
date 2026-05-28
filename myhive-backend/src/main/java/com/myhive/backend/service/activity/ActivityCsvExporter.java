package com.myhive.backend.service.activity;

import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Category;
import com.myhive.backend.repository.ActivityRepository;
import com.opencsv.CSVWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ActivityCsvExporter {

    static final String[] HEADER = {
            "id", "slug", "destination_slug", "name", "description",
            "price", "duration", "category_slugs", "image_url", "includes",
            "featured_weight"
    };

    private static final String BOM = "\uFEFF";

    private final ActivityRepository activityRepository;

    @Transactional(readOnly = true)
    public String exportAll() {
        return export(activityRepository.findAll());
    }

    @Transactional(readOnly = true)
    public String exportByDestination(UUID destinationId) {
        return export(activityRepository.findByDestinationId(destinationId));
    }

    private String export(List<Activity> activities) {
        StringWriter out = new StringWriter();
        out.write(BOM);
        try (CSVWriter writer = new CSVWriter(out,
                CSVWriter.DEFAULT_SEPARATOR,
                CSVWriter.DEFAULT_QUOTE_CHARACTER,
                CSVWriter.DEFAULT_ESCAPE_CHARACTER,
                CSVWriter.DEFAULT_LINE_END)) {
            writer.writeNext(HEADER, false);
            for (Activity a : activities) {
                writer.writeNext(toRow(a), false);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write CSV", e);
        }
        return out.toString();
    }

    private String[] toRow(Activity a) {
        return new String[]{
                String.valueOf(a.getId()),
                nullSafe(a.getSlug()),
                a.getDestination() == null ? "" : nullSafe(a.getDestination().getSlug()),
                sanitize(nullSafe(a.getName())),
                sanitize(nullSafe(a.getDescription())),
                formatPrice(a.getPrice()),
                a.getDuration() == null ? "" : a.getDuration().toString(),
                joinCategorySlugs(a),
                nullSafe(a.getImageUrl()),
                sanitize(nullSafe(a.getIncludes())),
                String.valueOf(a.getFeaturedWeight())
        };
    }

    private String joinCategorySlugs(Activity a) {
        if (a.getCategories() == null || a.getCategories().isEmpty()) {
            return "";
        }
        return a.getCategories().stream()
                .sorted(Comparator.comparing(Category::getSlug))
                .map(Category::getSlug)
                .collect(Collectors.joining(";"));
    }

    private String formatPrice(BigDecimal price) {
        if (price == null) {
            return "";
        }
        return price.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private String sanitize(String s) {
        if (s.isEmpty()) {
            return s;
        }
        char c = s.charAt(0);
        if (c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r') {
            return "'" + s;
        }
        return s;
    }
}
