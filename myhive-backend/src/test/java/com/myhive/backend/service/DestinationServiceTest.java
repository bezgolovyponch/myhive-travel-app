package com.myhive.backend.service;

import com.myhive.backend.TestDataFactory;
import com.myhive.backend.dto.DestinationDTO;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.exception.ResourceNotFoundException;
import com.myhive.backend.repository.DestinationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DestinationServiceTest {

    @Mock
    private DestinationRepository destinationRepository;

    @InjectMocks
    private DestinationService destinationService;

    @Test
    void getAllDestinations_returnsDTOList() {
        Destination dest = TestDataFactory.destination();
        when(destinationRepository.findAll()).thenReturn(List.of(dest));

        List<DestinationDTO> result = destinationService.getAllDestinations();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getName()).isEqualTo(dest.getName());
        assertThat(result.getFirst().getCountry()).isEqualTo(dest.getCountry());
    }

    @Test
    void getDestinationById_found_returnsDTO() {
        Destination dest = TestDataFactory.destination();
        when(destinationRepository.findById(dest.getId())).thenReturn(Optional.of(dest));

        DestinationDTO result = destinationService.getDestinationById(dest.getId());

        assertThat(result.getId()).isEqualTo(dest.getId());
    }

    @Test
    void getDestinationById_notFound_throwsResourceNotFound() {
        UUID id = UUID.randomUUID();
        when(destinationRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> destinationService.getDestinationById(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Destination");
    }
}
