package com.myhive.backend.ai.catalog;

import com.myhive.backend.ai.model.Brief;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Category;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.util.Translations;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Compacts a destination's catalog into what the planner prompt needs; caps the size for token budget. */
@Component
@RequiredArgsConstructor
public class CatalogSnapshotter {

    public static final int MAX_ACTIVITIES = 80;
    public static final int DEFAULT_DURATION_MINUTES = 120;
    public static final int ONE_LINE_MAX = 160;

    private final ActivityRepository activityRepository;

    @Transactional(readOnly = true)
    public List<CatalogActivity> snapshot(UUID destinationId, Brief brief, String locale) {
        String lc = Translations.normalize(locale);
        Set<String> wanted = new HashSet<>(brief.categorySlugs());
        Comparator<Activity> byOverlapThenWeight = Comparator
                .comparingInt((Activity a) -> overlap(a, wanted)).reversed()
                .thenComparing(Comparator.comparingInt(Activity::getFeaturedWeight).reversed())
                .thenComparing(Activity::getName);
        return activityRepository.findByDestinationId(destinationId).stream()
                .sorted(byOverlapThenWeight)
                .limit(MAX_ACTIVITIES)
                .map(a -> toCatalogActivity(a, lc))
                .toList();
    }

    private static int overlap(Activity activity, Set<String> wanted) {
        if (wanted.isEmpty()) {
            return 0;
        }
        return (int) activity.getCategories().stream().map(Category::getSlug).filter(wanted::contains).count();
    }

    private static CatalogActivity toCatalogActivity(Activity a, String lc) {
        Map<String, Map<String, String>> tr = a.getTranslations();
        String description = Translations.pick(tr, lc, "description", a.getDescription());
        boolean durationKnown = a.getDuration() != null && a.getDuration() > 0;
        return new CatalogActivity(
                a.getId(),
                a.getSlug(),
                Translations.pick(tr, lc, "name", a.getName()),
                oneLine(description),
                durationKnown ? a.getDuration() : DEFAULT_DURATION_MINUTES,
                durationKnown,
                a.getPrice(),
                a.getMinPrice(),
                a.getImageUrl(),
                a.getCategories().stream().map(Category::getSlug).sorted().toList());
    }

    static String oneLine(String description) {
        if (description == null) {
            return "";
        }
        String flat = description.replaceAll("\\s+", " ").strip();
        if (flat.length() <= ONE_LINE_MAX) {
            return flat;
        }
        int cut = flat.lastIndexOf(' ', ONE_LINE_MAX - 1);
        return flat.substring(0, cut > 40 ? cut : ONE_LINE_MAX - 1) + "…";
    }
}
