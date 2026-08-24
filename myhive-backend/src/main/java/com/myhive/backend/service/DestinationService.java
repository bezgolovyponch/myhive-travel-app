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
import com.myhive.backend.util.Translations;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DestinationService {

    private final DestinationRepository destinationRepository;
    private final CategoryRepository categoryRepository;

    // Without a locale: admin/raw view (base fields + translations map). With
    // one: public view, fields resolved for that locale. See ActivityService.

    public List<DestinationDTO> getAllDestinations() {
        return getAllDestinations(null);
    }

    public List<DestinationDTO> getAllDestinations(String locale) {
        return destinationRepository.findAll().stream()
                .map(d -> convertToDTO(d, locale))
                .toList();
    }

    public Page<DestinationDTO> getDestinationsPaged(Pageable pageable) {
        return destinationRepository.findAll(pageable)
                .map(this::convertToDTO);
    }

    public DestinationDTO getDestinationById(UUID id) {
        return getDestinationById(id, null);
    }

    public DestinationDTO getDestinationById(UUID id, String locale) {
        Destination destination = destinationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Destination", id));
        DestinationDTO dto = convertToDTO(destination, locale);
        dto.setAssignedCategories(CategoryResolver.toDTOs(destination.getCategories(), Translations.normalize(locale)));
        return dto;
    }

    public DestinationDTO getDestinationBySlug(String slug) {
        return getDestinationBySlug(slug, null);
    }

    public DestinationDTO getDestinationBySlug(String slug, String locale) {
        Destination destination = destinationRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Destination not found"));
        return convertToDTO(destination, locale);
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
        destination.setSeoIndexable(Boolean.TRUE.equals(dto.getSeoIndexable()));
        // null = "unchanged" (see ActivityService.applyDtoToEntity).
        if (dto.getTranslations() != null) {
            destination.setTranslations(dto.getTranslations());
        }
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
        return getCategoriesForDestination(id, null);
    }

    public List<CategoryDTO> getCategoriesForDestination(UUID id, String locale) {
        Destination destination = destinationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Destination", id));
        String lc = Translations.normalize(locale);

        Set<Category> explicit = destination.getCategories();
        if (!explicit.isEmpty()) {
            return CategoryResolver.toDTOs(explicit, lc);
        }

        List<Activity> activities = destination.getActivities();
        if (activities == null || activities.isEmpty()) {
            return List.of();
        }

        return CategoryResolver.toDTOs(
                activities.stream().flatMap(a -> a.getCategories().stream()).distinct().toList(), lc);
    }

    private DestinationDTO convertToDTO(Destination destination) {
        return convertToDTO(destination, null);
    }

    private DestinationDTO convertToDTO(Destination destination, String locale) {
        String lc = Translations.normalize(locale);
        Map<String, Map<String, String>> tr = destination.getTranslations();
        DestinationDTO dto = new DestinationDTO();
        dto.setId(destination.getId());
        dto.setSlug(destination.getSlug());
        dto.setName(Translations.pick(tr, lc, "name", destination.getName()));
        dto.setDescription(Translations.pick(tr, lc, "description", destination.getDescription()));
        dto.setCountry(Translations.pick(tr, lc, "country", destination.getCountry()));
        dto.setCity(Translations.pick(tr, lc, "city", destination.getCity()));
        dto.setImageUrl(destination.getImageUrl());
        dto.setRating(destination.getRating());
        dto.setActivityCount(destination.getActivities() != null ? destination.getActivities().size() : 0);
        dto.setSeoIndexable(destination.isSeoIndexable());
        if (locale == null) {
            dto.setTranslations(tr);
        }
        return dto;
    }
}
