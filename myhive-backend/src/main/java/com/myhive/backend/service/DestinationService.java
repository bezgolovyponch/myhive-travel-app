package com.myhive.backend.service;

import com.myhive.backend.dto.DestinationDTO;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.repository.DestinationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DestinationService {

    private final DestinationRepository destinationRepository;

    public List<DestinationDTO> getAllDestinations() {
        return destinationRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public DestinationDTO getDestinationById(UUID id) {
        Destination destination = destinationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Destination not found with id: " + id));
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
