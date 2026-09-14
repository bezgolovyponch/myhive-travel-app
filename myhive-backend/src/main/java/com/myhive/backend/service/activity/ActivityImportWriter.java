package com.myhive.backend.service.activity;

import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Category;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.exception.CsvImportException;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.CategoryRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.service.SlugAssigner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The transactional half of a CSV import: takes rows that already passed preview and
 * writes them, all-or-nothing. Kept separate from {@link ActivityCsvImporter} so that the
 * slow, non-transactional work (downloading remote images) never holds a DB connection.
 *
 * <p>Every catalog reference (activity, destination, category) is re-resolved here: the
 * preview may be up to ten minutes old and anything can have been deleted since.
 */
@Service
public class ActivityImportWriter {

    /** Ids touched by one import, in write order. */
    public record Result(List<UUID> updatedIds, List<UUID> createdIds) {
    }

    private final ActivityRepository activityRepository;
    private final CategoryRepository categoryRepository;
    private final DestinationRepository destinationRepository;

    public ActivityImportWriter(ActivityRepository activityRepository,
                                CategoryRepository categoryRepository,
                                DestinationRepository destinationRepository) {
        this.activityRepository = activityRepository;
        this.categoryRepository = categoryRepository;
        this.destinationRepository = destinationRepository;
    }

    /**
     * @param rows          validated rows; updates (with id) are written before creates (blank id)
     * @param imageUrlByRow final image URL per new row's CSV row number (absent = no image)
     */
    @Transactional
    public Result write(List<ValidatedRow> rows, Map<Integer, String> imageUrlByRow) {
        List<UUID> updatedIds = new ArrayList<>();
        List<UUID> createdIds = new ArrayList<>();
        for (ValidatedRow row : rows) {
            if (!row.isNew()) {
                updatedIds.add(update(row).getId());
            }
        }
        for (ValidatedRow row : rows) {
            if (row.isNew()) {
                createdIds.add(create(row, imageUrlByRow.get(row.csvRowNumber())).getId());
            }
        }
        return new Result(updatedIds, createdIds);
    }

    private Activity update(ValidatedRow row) {
        Activity activity = activityRepository.findById(row.activityId())
                .orElseThrow(() -> stateChanged("Activity " + row.activityId() + " no longer exists", row));
        applyMutableFields(row, activity);
        return activityRepository.save(activity);
    }

    private Activity create(ValidatedRow row, String imageUrl) {
        Destination destination = destinationRepository.findBySlug(row.csvDestinationSlug())
                .orElseThrow(() -> stateChanged(
                        "Destination '" + row.csvDestinationSlug() + "' no longer exists", row));
        Activity activity = new Activity();
        activity.setDestination(destination);
        applyMutableFields(row, activity);
        activity.setImageUrl(imageUrl);
        // Same slug rules as the admin form: custom slug if given (already normalised by the
        // validator), otherwise from the name, suffixed on collision. The uniqueness check
        // auto-flushes earlier rows of this import, so in-file duplicates get suffixed too.
        SlugAssigner.assignOnCreate(activity, row.csvSlug(), row.name(), activityRepository);
        return activityRepository.save(activity);
    }

    /** Fields that both updates and creates take from the CSV; optional columns follow the validator's contract. */
    private void applyMutableFields(ValidatedRow row, Activity activity) {
        activity.setName(row.name());
        activity.setDescription(row.description().isEmpty() ? null : row.description());
        activity.setPrice(row.price());
        activity.setDuration(row.duration());
        activity.setIncludes(row.includes().isEmpty() ? null : row.includes());
        activity.setCategories(resolveCategories(row));
        // Optional columns: null means "column absent from CSV — do not touch" (a fresh entity
        // keeps its defaults); a blank cell arrives as 0 and means "no minimum" / default weight.
        if (row.featuredWeight() != null) {
            activity.setFeaturedWeight(row.featuredWeight());
        }
        if (row.minPrice() != null) {
            activity.setMinPrice(row.minPrice().signum() == 0 ? null : row.minPrice());
        }
    }

    private Set<Category> resolveCategories(ValidatedRow row) {
        Set<Category> categories = new HashSet<>();
        for (String slug : row.categorySlugs()) {
            Category category = categoryRepository.findBySlug(slug)
                    .orElseThrow(() -> stateChanged("Category '" + slug + "' no longer exists", row));
            categories.add(category);
        }
        return categories;
    }

    private static CsvImportException stateChanged(String what, ValidatedRow row) {
        return new CsvImportException(CsvImportException.Code.STATE_CHANGED,
                what + " (row " + row.csvRowNumber() + ")");
    }
}
