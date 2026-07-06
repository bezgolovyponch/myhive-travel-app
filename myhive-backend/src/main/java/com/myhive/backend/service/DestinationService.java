package com.myhive.backend.service;

import com.myhive.backend.dto.CategoryDTO;
import com.myhive.backend.dto.DestinationDTO;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Category;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.exception.BadRequestException;
import com.myhive.backend.exception.ResourceNotFoundException;
import com.myhive.backend.repository.CategoryRepository;
import com.myhive.backend.repository.DestinationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DestinationService {

    private final DestinationRepository destinationRepository;
    private final CategoryRepository categoryRepository;

    public List<DestinationDTO> getAllDestinations() {
        return destinationRepository.findAll().stream()
                .map(this::convertToDTO)
                .toList();
    }

    public Page<DestinationDTO> getDestinationsPaged(Pageable pageable) {
        return destinationRepository.findAll(pageable)
                .map(this::convertToDTO);
    }

    public DestinationDTO getDestinationById(UUID id) {
        Destination destination = destinationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Destination", id));
        DestinationDTO dto = convertToDTO(destination);
        dto.setAssignedCategories(
                destination.getCategories().stream()
                        .sorted(Comparator.comparing(c -> c.getName().toLowerCase()))
                        .map(this::categoryToDTO)
                        .toList()
        );
        return dto;
    }

    public DestinationDTO getDestinationBySlug(String slug) {
        Destination destination = destinationRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Destination not found"));
        return convertToDTO(destination);
    }

    @Transactional
    public DestinationDTO createDestination(DestinationDTO dto) {
        Destination destination = new Destination();
        applyDtoToEntity(dto, destination);
        SlugAssigner.assignOnCreate(destination, dto.getSlug(), dto.getName(), destinationRepository);
        return convertToDTO(destinationRepository.save(destination));
    }

    @Transactional
    public DestinationDTO updateDestination(UUID id, DestinationDTO dto) {
        Destination destination = destinationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Destination", id));
        SlugAssigner.assignOnUpdate(destination, dto.getSlug(), dto.getName(), destination.getName(), destinationRepository);
        applyDtoToEntity(dto, destination);
        return convertToDTO(destinationRepository.save(destination));
    }

    private void applyDtoToEntity(DestinationDTO dto, Destination destination) {
        destination.setName(dto.getName());
        destination.setDescription(dto.getDescription());
        destination.setCountry(dto.getCountry());
        destination.setCity(dto.getCity());
        destination.setImageUrl(dto.getImageUrl());
        destination.setRating(dto.getRating());
    }

    @Transactional
    public void deleteDestination(UUID id) {
        Destination destination = destinationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Destination", id));
        if (destination.getActivities() != null && !destination.getActivities().isEmpty()) {
            throw new BadRequestException(
                    "Cannot delete destination with " + destination.getActivities().size() + " associated activities. Remove them first.");
        }
        destinationRepository.deleteById(id);
    }

    @Transactional
    public void updateDestinationCategories(UUID id, List<UUID> categoryIds) {
        Destination destination = destinationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Destination", id));
        List<UUID> uniqueIds = categoryIds.stream().distinct().toList();
        List<Category> categories = categoryRepository.findAllById(uniqueIds);
        if (categories.size() != uniqueIds.size()) {
            throw new BadRequestException("One or more category IDs are invalid.");
        }
        destination.setCategories(new HashSet<>(categories));
        destinationRepository.save(destination);
    }

    public List<CategoryDTO> getCategoriesForDestination(UUID id) {
        Destination destination = destinationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Destination", id));

        Set<Category> explicit = destination.getCategories();
        if (!explicit.isEmpty()) {
            return explicit.stream()
                    .sorted(Comparator.comparing(c -> c.getName().toLowerCase()))
                    .map(this::categoryToDTO)
                    .toList();
        }

        List<Activity> activities = destination.getActivities();
        if (activities == null || activities.isEmpty()) {
            return List.of();
        }

        return activities.stream()
                .flatMap(a -> a.getCategories().stream())
                .distinct()
                .sorted(Comparator.comparing(c -> c.getName().toLowerCase()))
                .map(this::categoryToDTO)
                .toList();
    }

    private CategoryDTO categoryToDTO(Category category) {
        CategoryDTO dto = new CategoryDTO();
        dto.setId(category.getId());
        dto.setName(category.getName());
        dto.setSlug(category.getSlug());
        return dto;
    }

    private DestinationDTO convertToDTO(Destination destination) {
        DestinationDTO dto = new DestinationDTO();
        dto.setId(destination.getId());
        dto.setSlug(destination.getSlug());
        dto.setName(destination.getName());
        dto.setDescription(destination.getDescription());
        dto.setCountry(destination.getCountry());
        dto.setCity(destination.getCity());
        dto.setImageUrl(destination.getImageUrl());
        dto.setRating(destination.getRating());
        dto.setActivityCount(destination.getActivities() != null ? destination.getActivities().size() : 0);
        return dto;
    }
}
