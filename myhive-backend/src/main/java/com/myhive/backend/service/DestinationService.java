package com.myhive.backend.service;

import com.myhive.backend.dto.DestinationDTO;
import com.myhive.backend.entity.Destination;
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

    private DestinationDTO convertToDTO(Destination destination) {
        DestinationDTO dto = new DestinationDTO();
        dto.setId(destination.getId());
        dto.setName(destination.getName());
        dto.setDescription(destination.getDescription());
        dto.setCountry(destination.getCountry());
        dto.setCity(destination.getCity());
        dto.setImageUrl(destination.getImageUrl());
        dto.setRating(destination.getRating());
        return dto;
    }
}
