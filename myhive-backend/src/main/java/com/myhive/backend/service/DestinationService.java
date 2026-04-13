package com.myhive.backend.service;

import com.myhive.backend.dto.DestinationDTO;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.exception.BadRequestException;
import com.myhive.backend.exception.ResourceNotFoundException;
import com.myhive.backend.repository.DestinationRepository;
import lombok.RequiredArgsConstructor;
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

    public DestinationDTO getDestinationById(UUID id) {
        Destination destination = destinationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Destination", id));
        return convertToDTO(destination);
    }

    @Transactional
    public DestinationDTO createDestination(DestinationDTO dto) {
        Destination destination = new Destination();
        applyDtoToEntity(dto, destination);
        return convertToDTO(destinationRepository.save(destination));
    }

    @Transactional
    public DestinationDTO updateDestination(UUID id, DestinationDTO dto) {
        Destination destination = destinationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Destination", id));
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

    private DestinationDTO convertToDTO(Destination destination) {
        DestinationDTO dto = new DestinationDTO();
        dto.setId(destination.getId());
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
