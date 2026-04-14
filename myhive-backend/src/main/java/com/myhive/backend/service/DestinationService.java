package com.myhive.backend.service;

import com.myhive.backend.dto.DestinationDTO;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.exception.BadRequestException;
import com.myhive.backend.exception.ResourceNotFoundException;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.util.SlugUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DestinationService {

    private final DestinationRepository destinationRepository;

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
        return convertToDTO(destination);
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
        destination.setSlug(SlugUtils.resolveSlug(dto.getSlug(), dto.getName(), destinationRepository::existsBySlug));
        try {
            return convertToDTO(destinationRepository.save(destination));
        } catch (DataIntegrityViolationException e) {
            destination.setSlug(SlugUtils.resolveSlug(dto.getSlug(), dto.getName(), destinationRepository::existsBySlug));
            return convertToDTO(destinationRepository.save(destination));
        }
    }

    @Transactional
    public DestinationDTO updateDestination(UUID id, DestinationDTO dto) {
        Destination destination = destinationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Destination", id));
        boolean updateSlug = SlugUtils.needsUpdate(dto.getSlug(), destination.getSlug(), dto.getName(), destination.getName());
        applyDtoToEntity(dto, destination);
        if (updateSlug) {
            destination.setSlug(SlugUtils.resolveForUpdate(dto.getSlug(), dto.getName(), destination.getSlug(),
                    slug -> destinationRepository.findBySlug(slug)
                            .filter(d -> !d.getId().equals(id))
                            .isPresent()));
        }
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
