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
import com.myhive.backend.util.SlugUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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

    public List<ActivityDTO> getAllActivities() {
        return activityRepository.findAll().stream()
                .map(this::convertToDTO)
                .toList();
    }

    public Page<ActivityDTO> getActivitiesPaged(Pageable pageable) {
        return activityRepository.findAll(pageable)
                .map(this::convertToDTO);
    }

    public ActivityDTO getActivityById(UUID id) {
        Activity activity = activityRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Activity", id));
        return convertToDTO(activity);
    }

    public ActivityDTO getActivityBySlug(String slug) {
        Activity activity = activityRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Activity", slug));
        return convertToDTO(activity);
    }

    public List<ActivityDTO> getActivitiesByDestination(UUID destinationId) {
        return activityRepository.findByDestinationId(destinationId).stream()
                .map(this::convertToDTO)
                .toList();
    }

    public List<ActivityDTO> getActivitiesByCategorySlug(String categorySlug) {
        return activityRepository.findByCategoriesSlug(categorySlug).stream()
                .map(this::convertToDTO)
                .toList();
    }

    public List<ActivityDTO> getActivitiesByDestinationAndCategorySlug(UUID destinationId, String categorySlug) {
        return activityRepository.findByDestinationIdAndCategoriesSlug(destinationId, categorySlug).stream()
                .map(this::convertToDTO)
                .toList();
    }

    public Page<ActivityDTO> getActivitiesByDestinationPaged(UUID destinationId, Pageable pageable) {
        return activityRepository.findByDestinationId(destinationId, pageable)
                .map(this::convertToDTO);
    }

    public Page<ActivityDTO> getActivitiesByDestinationAndCategorySlugPaged(UUID destinationId, String categorySlug, Pageable pageable) {
        return activityRepository.findByDestinationIdAndCategoriesSlug(destinationId, categorySlug, pageable)
                .map(this::convertToDTO);
    }

    @Transactional
    public ActivityDTO createActivity(ActivityDTO dto) {
        Destination destination = destinationRepository.findById(dto.getDestinationId())
                .orElseThrow(() -> new ResourceNotFoundException("Destination", dto.getDestinationId()));

        Activity activity = new Activity();
        activity.setDestination(destination);
        applyDtoToEntity(dto, activity);
        activity.setSlug(SlugUtils.resolveSlug(dto.getSlug(), dto.getName(), activityRepository::existsBySlug));

        try {
            return convertToDTO(activityRepository.save(activity));
        } catch (DataIntegrityViolationException e) {
            activity.setSlug(SlugUtils.resolveSlug(dto.getSlug(), dto.getName(), activityRepository::existsBySlug));
            return convertToDTO(activityRepository.save(activity));
        }
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

        boolean updateSlug = SlugUtils.needsUpdate(dto.getSlug(), activity.getSlug(), dto.getName(), activity.getName());
        applyDtoToEntity(dto, activity);
        if (updateSlug) {
            activity.setSlug(SlugUtils.resolveForUpdate(dto.getSlug(), dto.getName(), activity.getSlug(),
                    slug -> activityRepository.findBySlug(slug)
                            .filter(a -> !a.getId().equals(id))
                            .isPresent()));
        }

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
        activity.setDuration(dto.getDuration());
        activity.setImageUrl(dto.getImageUrl());
        activity.setIncludes(dto.getIncludes());
        activity.setFeaturedWeight(dto.getFeaturedWeight() == null ? 0 : dto.getFeaturedWeight());
        activity.setCategories(CategoryResolver.resolve(dto.getCategoryIds(), categoryRepository));
    }

    private ActivityDTO convertToDTO(Activity activity) {
        ActivityDTO dto = new ActivityDTO();
        dto.setId(activity.getId());
        dto.setSlug(activity.getSlug());
        dto.setDestinationId(activity.getDestination().getId());
        dto.setDestinationName(activity.getDestination().getName());
        dto.setDestinationSlug(activity.getDestination().getSlug());
        dto.setName(activity.getName());
        dto.setDescription(activity.getDescription());
        dto.setPrice(activity.getPrice());
        dto.setDuration(activity.getDuration());
        dto.setImageUrl(activity.getImageUrl());
        dto.setIncludes(activity.getIncludes());
        dto.setFeaturedWeight(activity.getFeaturedWeight());

        List<CategoryDTO> categoryDtos = CategoryResolver.toDTOs(activity.getCategories());
        dto.setCategories(categoryDtos);
        dto.setCategoryIds(categoryDtos.stream().map(CategoryDTO::getId).toList());
        return dto;
    }
}
