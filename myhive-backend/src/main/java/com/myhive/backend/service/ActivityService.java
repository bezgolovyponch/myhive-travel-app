package com.myhive.backend.service;

import com.myhive.backend.dto.ActivityDTO;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.exception.ResourceNotFoundException;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.DestinationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActivityService {

    private final ActivityRepository activityRepository;
    private final DestinationRepository destinationRepository;

    public List<ActivityDTO> getAllActivities() {
        return activityRepository.findAll().stream()
                .map(this::convertToDTO)
                .toList();
    }

    public ActivityDTO getActivityById(UUID id) {
        Activity activity = activityRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Activity", id));
        return convertToDTO(activity);
    }

    public List<ActivityDTO> getActivitiesByDestination(UUID destinationId) {
        return activityRepository.findByDestinationId(destinationId).stream()
                .map(this::convertToDTO)
                .toList();
    }

    public List<ActivityDTO> getActivitiesByCategory(String category) {
        return activityRepository.findByCategory(category).stream()
                .map(this::convertToDTO)
                .toList();
    }

    public List<ActivityDTO> getActivitiesByDestinationAndCategory(UUID destinationId, String category) {
        return activityRepository.findByDestinationIdAndCategory(destinationId, category).stream()
                .map(this::convertToDTO)
                .toList();
    }

    public Page<ActivityDTO> getActivitiesByDestinationPaged(UUID destinationId, Pageable pageable) {
        return activityRepository.findByDestinationId(destinationId, pageable)
                .map(this::convertToDTO);
    }

    public Page<ActivityDTO> getActivitiesByDestinationAndCategoryPaged(UUID destinationId, String category, Pageable pageable) {
        return activityRepository.findByDestinationIdAndCategory(destinationId, category, pageable)
                .map(this::convertToDTO);
    }

    @Transactional
    public ActivityDTO createActivity(ActivityDTO dto) {
        Destination destination = destinationRepository.findById(dto.getDestinationId())
                .orElseThrow(() -> new ResourceNotFoundException("Destination", dto.getDestinationId()));

        Activity activity = new Activity();
        activity.setDestination(destination);
        activity.setName(dto.getName());
        activity.setDescription(dto.getDescription());
        activity.setPrice(dto.getPrice());
        activity.setDuration(dto.getDuration());
        activity.setCategory(dto.getCategory());
        activity.setImageUrl(dto.getImageUrl());

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

        activity.setName(dto.getName());
        activity.setDescription(dto.getDescription());
        activity.setPrice(dto.getPrice());
        activity.setDuration(dto.getDuration());
        activity.setCategory(dto.getCategory());
        activity.setImageUrl(dto.getImageUrl());

        return convertToDTO(activityRepository.save(activity));
    }

    @Transactional
    public void deleteActivity(UUID id) {
        if (!activityRepository.existsById(id)) {
            throw new ResourceNotFoundException("Activity", id);
        }
        activityRepository.deleteById(id);
    }

    private ActivityDTO convertToDTO(Activity activity) {
        ActivityDTO dto = new ActivityDTO();
        dto.setId(activity.getId());
        dto.setDestinationId(activity.getDestination().getId());
        dto.setDestinationName(activity.getDestination().getName());
        dto.setName(activity.getName());
        dto.setDescription(activity.getDescription());
        dto.setPrice(activity.getPrice());
        dto.setDuration(activity.getDuration());
        dto.setCategory(activity.getCategory());
        dto.setImageUrl(activity.getImageUrl());
        return dto;
    }
}
