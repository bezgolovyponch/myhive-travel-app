package com.myhive.backend.service;

import com.myhive.backend.dto.ActivityDTO;
import com.myhive.backend.dto.CategoryDTO;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.exception.ActivityInUseException;
import com.myhive.backend.exception.ActivityInUseInSessionException;
import com.myhive.backend.exception.ResourceNotFoundException;
import com.myhive.backend.model.VoteSessionStatus;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.CategoryRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.repository.PackageRepository;
import com.myhive.backend.repository.VoteSessionActivityRepository;
import com.myhive.backend.util.Translations;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActivityService {

    private final ActivityRepository activityRepository;
    private final DestinationRepository destinationRepository;
    private final CategoryRepository categoryRepository;
    private final PackageRepository packageRepository;
    private final VoteSessionActivityRepository voteSessionActivityRepository;

    // Read methods come in two flavours: without a locale (admin/raw view — base
    // fields plus the translations map) and with one (public view — fields
    // resolved for that locale, map omitted). The public controllers always
    // pass the request's locale, even "en".

    public List<ActivityDTO> getAllActivities() {
        return getAllActivities(null);
    }

    public List<ActivityDTO> getAllActivities(String locale) {
        return activityRepository.findAll().stream()
                .map(a -> convertToDTO(a, locale))
                .toList();
    }

    public Page<ActivityDTO> getActivitiesPaged(Pageable pageable) {
        return activityRepository.findAll(pageable)
                .map(this::convertToDTO);
    }

    public ActivityDTO getActivityById(UUID id) {
        return getActivityById(id, null);
    }

    public ActivityDTO getActivityById(UUID id, String locale) {
        Activity activity = activityRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Activity", id));
        return convertToDTO(activity, locale);
    }

    public ActivityDTO getActivityBySlug(String slug) {
        return getActivityBySlug(slug, null);
    }

    public ActivityDTO getActivityBySlug(String slug, String locale) {
        Activity activity = activityRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Activity", slug));
        return convertToDTO(activity, locale);
    }

    public List<ActivityDTO> getActivitiesByDestination(UUID destinationId) {
        return getActivitiesByDestination(destinationId, null);
    }

    public List<ActivityDTO> getActivitiesByDestination(UUID destinationId, String locale) {
        return activityRepository.findByDestinationId(destinationId).stream()
                .map(a -> convertToDTO(a, locale))
                .toList();
    }

    public List<ActivityDTO> getActivitiesByCategorySlug(String categorySlug) {
        return getActivitiesByCategorySlug(categorySlug, null);
    }

    public List<ActivityDTO> getActivitiesByCategorySlug(String categorySlug, String locale) {
        return activityRepository.findByCategoriesSlug(categorySlug).stream()
                .map(a -> convertToDTO(a, locale))
                .toList();
    }

    public List<ActivityDTO> getActivitiesByDestinationAndCategorySlug(UUID destinationId, String categorySlug) {
        return getActivitiesByDestinationAndCategorySlug(destinationId, categorySlug, null);
    }

    public List<ActivityDTO> getActivitiesByDestinationAndCategorySlug(UUID destinationId, String categorySlug, String locale) {
        return activityRepository.findByDestinationIdAndCategoriesSlug(destinationId, categorySlug).stream()
                .map(a -> convertToDTO(a, locale))
                .toList();
    }

    public List<ActivityDTO> getFeaturedActivities(String categorySlug) {
        return getFeaturedActivities(categorySlug, null);
    }

    public List<ActivityDTO> getFeaturedActivities(String categorySlug, String locale) {
        List<Activity> featuredActivities = categorySlug == null
                ? activityRepository.findByFeaturedTrueOrderByNameAsc()
                : activityRepository.findByFeaturedTrueAndCategoriesSlugOrderByNameAsc(categorySlug);
        return featuredActivities.stream()
                .map(a -> convertToDTO(a, locale))
                .toList();
    }

    public Page<ActivityDTO> getActivitiesByDestinationPaged(UUID destinationId, Pageable pageable) {
        return getActivitiesByDestinationPaged(destinationId, pageable, null);
    }

    public Page<ActivityDTO> getActivitiesByDestinationPaged(UUID destinationId, Pageable pageable, String locale) {
        return activityRepository.findByDestinationId(destinationId, pageable)
                .map(a -> convertToDTO(a, locale));
    }

    public Page<ActivityDTO> getActivitiesByDestinationAndCategorySlugPaged(UUID destinationId, String categorySlug, Pageable pageable) {
        return getActivitiesByDestinationAndCategorySlugPaged(destinationId, categorySlug, pageable, null);
    }

    public Page<ActivityDTO> getActivitiesByDestinationAndCategorySlugPaged(UUID destinationId, String categorySlug, Pageable pageable, String locale) {
        return activityRepository.findByDestinationIdAndCategoriesSlug(destinationId, categorySlug, pageable)
                .map(a -> convertToDTO(a, locale));
    }

    @Transactional
    public ActivityDTO createActivity(ActivityDTO dto) {
        Destination destination = destinationRepository.findById(dto.getDestinationId())
                .orElseThrow(() -> new ResourceNotFoundException("Destination", dto.getDestinationId()));

        Activity activity = new Activity();
        activity.setDestination(destination);
        applyDtoToEntity(dto, activity);
        SlugAssigner.assignOnCreate(activity, dto.getSlug(), dto.getName(), activityRepository);
        return convertToDTO(activityRepository.save(activity));
    }

    @Transactional
    public ActivityDTO updateActivity(UUID id, ActivityDTO dto) {
        Activity activity = activityRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Activity", id));

        if (dto.getDestinationId() != null && !dto.getDestinationId().equals(activity.getDestination().getId())) {
            Destination destination = destinationRepository.findById(dto.getDestinationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Destination", dto.getDestinationId()));
            activity.setDestination(destination);
        }

        SlugAssigner.assignOnUpdate(activity, dto.getSlug(), dto.getName(), activity.getName(), activityRepository);
        applyDtoToEntity(dto, activity);

        return convertToDTO(activityRepository.save(activity));
    }

    @Transactional
    public void deleteActivity(UUID id) {
        if (!activityRepository.existsById(id)) {
            throw new ResourceNotFoundException("Activity", id);
        }
        if (voteSessionActivityRepository.existsByActivityIdAndSession_StatusIn(
                id, EnumSet.of(VoteSessionStatus.ACTIVE))) {
            throw new ActivityInUseInSessionException(
                    "Activity is in a non-completed vote session's curated list and cannot be deleted",
                    List.of());
        }
        List<String> usedIn = packageRepository.findPackageNamesByActivityId(id);
        if (!usedIn.isEmpty()) {
            throw new ActivityInUseException(usedIn);
        }
        activityRepository.deleteById(id);
    }

    private void applyDtoToEntity(ActivityDTO dto, Activity activity) {
        activity.setName(dto.getName());
        activity.setDescription(dto.getDescription());
        activity.setPrice(dto.getPrice());
        activity.setMinPrice(dto.getMinPrice());
        activity.setDuration(dto.getDuration());
        activity.setImageUrl(dto.getImageUrl());
        activity.setIncludes(dto.getIncludes());
        activity.setFeaturedWeight(dto.getFeaturedWeight() == null ? 0 : dto.getFeaturedWeight());
        activity.setFeatured(Boolean.TRUE.equals(dto.getFeatured()));
        activity.setSeoIndexable(Boolean.TRUE.equals(dto.getSeoIndexable()));
        activity.setCategories(CategoryResolver.resolve(dto.getCategoryIds(), categoryRepository));
        // null = "unchanged": the admin forms that don't know about translations
        // yet must not wipe them on every save. Send {} to clear.
        if (dto.getTranslations() != null) {
            activity.setTranslations(dto.getTranslations());
        }
    }

    private ActivityDTO convertToDTO(Activity activity) {
        return convertToDTO(activity, null);
    }

    private ActivityDTO convertToDTO(Activity activity, String locale) {
        String lc = Translations.normalize(locale);
        Destination destination = activity.getDestination();
        ActivityDTO dto = new ActivityDTO();
        dto.setId(activity.getId());
        dto.setSlug(activity.getSlug());
        dto.setDestinationId(destination.getId());
        dto.setDestinationName(Translations.pick(destination.getTranslations(), lc, "name", destination.getName()));
        dto.setDestinationSlug(destination.getSlug());
        dto.setName(Translations.pick(activity.getTranslations(), lc, "name", activity.getName()));
        dto.setDescription(Translations.pick(activity.getTranslations(), lc, "description", activity.getDescription()));
        dto.setPrice(activity.getPrice());
        dto.setMinPrice(activity.getMinPrice());
        dto.setDuration(activity.getDuration());
        dto.setImageUrl(activity.getImageUrl());
        dto.setIncludes(Translations.pick(activity.getTranslations(), lc, "includes", activity.getIncludes()));
        dto.setFeaturedWeight(activity.getFeaturedWeight());
        dto.setFeatured(activity.isFeatured());
        dto.setSeoIndexable(activity.isSeoIndexable());
        if (locale == null) {
            dto.setTranslations(activity.getTranslations());
        }

        List<CategoryDTO> categoryDtos = CategoryResolver.toDTOs(activity.getCategories(), lc);
        dto.setCategories(categoryDtos);
        dto.setCategoryIds(categoryDtos.stream().map(CategoryDTO::getId).toList());
        return dto;
    }
}
