package com.myhive.backend.ai.catalog;

import com.myhive.backend.ai.model.Brief;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Category;
import com.myhive.backend.entity.Package;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.PackageRepository;
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
import java.util.stream.Collectors;

/** Compacts a destination's catalog into what the planner prompt needs; caps the size for token budget. */
@Component
@RequiredArgsConstructor
public class CatalogSnapshotter {

    public static final int MAX_ACTIVITIES = 80;
    public static final int DEFAULT_DURATION_MINUTES = 120;
    public static final int ONE_LINE_MAX = 160;
    /**
     * "What is included" goes into the planner prompt once per catalog row and into every item of every
     * stored plan, so it is cut like the one-liner: production texts run to 450 characters.
     */
    public static final int INCLUDES_MAX = 200;

    /** One prompt line each; a destination with more presets than this keeps the first per tier and name. */
    public static final int MAX_PRESETS = 15;

    private final ActivityRepository activityRepository;
    private final PackageRepository packageRepository;

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

    /**
     * The destination's ready-made packages that carry a price level, cut down to what the planner can
     * actually place: an activity that is not in {@code catalog} is left out of its preset, and a preset
     * with nothing left is left out altogether. Ordered by tier, then name.
     */
    @Transactional(readOnly = true)
    public List<CatalogPreset> presets(UUID destinationId, List<CatalogActivity> catalog) {
        Set<UUID> known = catalog.stream().map(CatalogActivity::id).collect(Collectors.toSet());
        return packageRepository.findByDestinationIdAndTierIsNotNull(destinationId).stream()
                .sorted(Comparator.comparing(Package::getTier).thenComparing(Package::getName))
                .map(p -> toPreset(p, known))
                .filter(preset -> !preset.activityIds().isEmpty())
                .limit(MAX_PRESETS)
                .toList();
    }

    private static CatalogPreset toPreset(Package p, Set<UUID> known) {
        List<UUID> activityIds = p.getPackageActivities().stream()
                .map(pa -> pa.getActivity().getId())
                .filter(known::contains)
                .distinct()
                .toList();
        return new CatalogPreset(p.getId(), p.getName(), p.getTier(), activityIds,
                p.getCategories().stream().map(Category::getSlug).sorted().toList());
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
                a.getCategories().stream().map(Category::getSlug).sorted().toList(),
                includes(Translations.pick(tr, lc, "includes", a.getIncludes())));
    }

    /** Flattened and cut like {@link #oneLine}; an activity that lists nothing has none rather than "". */
    static String includes(String text) {
        String cut = cut(text, INCLUDES_MAX);
        return cut.isEmpty() ? null : cut;
    }

    /** A word boundary found earlier than this would throw away most of the line; hard-cut instead. */
    private static final int MIN_WORD_CUT = 40;

    static String oneLine(String description) {
        return cut(description, ONE_LINE_MAX);
    }

    /** One line, at most {@code max} characters, cut at a word boundary where there is a sensible one. */
    private static String cut(String text, int max) {
        if (text == null) {
            return "";
        }
        String flat = text.replaceAll("\\s+", " ").strip();
        if (flat.length() <= max) {
            return flat;
        }
        int cut = flat.lastIndexOf(' ', max - 1);
        return flat.substring(0, cut > MIN_WORD_CUT ? cut : max - 1) + "…";
    }
}
