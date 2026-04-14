package com.myhive.backend.service;

import com.myhive.backend.TestDataFactory;
import com.myhive.backend.dto.DestinationDTO;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.exception.BadRequestException;
import com.myhive.backend.exception.ResourceNotFoundException;
import com.myhive.backend.repository.DestinationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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
    void getDestinationsPaged_returnsPagedDTOs() {
        Destination dest = TestDataFactory.destination();
        PageRequest pageRequest = PageRequest.of(0, 10);
        Page<Destination> page = new PageImpl<>(List.of(dest), pageRequest, 1);
        when(destinationRepository.findAll(pageRequest)).thenReturn(page);

        Page<DestinationDTO> result = destinationService.getDestinationsPaged(pageRequest);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().getFirst().getName()).isEqualTo(dest.getName());
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

    @Test
    void getDestinationBySlug_found_returnsDTO() {
        Destination dest = TestDataFactory.destination();
        when(destinationRepository.findBySlug("test-destination")).thenReturn(Optional.of(dest));

        DestinationDTO result = destinationService.getDestinationBySlug("test-destination");

        assertThat(result.getId()).isEqualTo(dest.getId());
        assertThat(result.getSlug()).isEqualTo("test-destination");
    }

    @Test
    void getDestinationBySlug_notFound_throwsResourceNotFound() {
        when(destinationRepository.findBySlug("nonexistent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> destinationService.getDestinationBySlug("nonexistent"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createDestination_validDTO_savesAndReturns() {
        String expectedName = "Bali";
        String expectedCountry = "Indonesia";
        String expectedCity = "Denpasar";
        BigDecimal expectedRating = new BigDecimal("4.85");

        DestinationDTO dto = new DestinationDTO();
        dto.setName(expectedName);
        dto.setCountry(expectedCountry);
        dto.setCity(expectedCity);
        dto.setRating(expectedRating);

        when(destinationRepository.existsBySlug("bali")).thenReturn(false);
        when(destinationRepository.save(any(Destination.class))).thenAnswer(inv -> {
            Destination d = inv.getArgument(0);
            d.setId(UUID.randomUUID());
            return d;
        });

        DestinationDTO result = destinationService.createDestination(dto);

        assertThat(result.getName()).isEqualTo(expectedName);
        assertThat(result.getCountry()).isEqualTo(expectedCountry);
        assertThat(result.getCity()).isEqualTo(expectedCity);
        assertThat(result.getRating()).isEqualByComparingTo(expectedRating);
        assertThat(result.getSlug()).isEqualTo("bali");
        verify(destinationRepository).save(any(Destination.class));
    }

    @Test
    void createDestination_slugCollision_appendsSuffix() {
        DestinationDTO dto = new DestinationDTO();
        dto.setName("Bali");
        dto.setCountry("Indonesia");

        when(destinationRepository.existsBySlug("bali")).thenReturn(true);
        when(destinationRepository.existsBySlug("bali-2")).thenReturn(false);
        when(destinationRepository.save(any(Destination.class))).thenAnswer(inv -> {
            Destination d = inv.getArgument(0);
            d.setId(UUID.randomUUID());
            return d;
        });

        DestinationDTO result = destinationService.createDestination(dto);

        assertThat(result.getSlug()).isEqualTo("bali-2");
    }

    @Test
    void updateDestination_found_updatesAndReturns() {
        String expectedName = "Updated Name";
        String expectedCountry = "Updated Country";

        Destination existing = TestDataFactory.destination();
        DestinationDTO dto = TestDataFactory.destinationDTO();
        dto.setName(expectedName);
        dto.setCountry(expectedCountry);

        when(destinationRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(destinationRepository.findBySlug("updated-name")).thenReturn(Optional.empty());
        when(destinationRepository.save(any(Destination.class))).thenAnswer(inv -> inv.getArgument(0));

        DestinationDTO result = destinationService.updateDestination(existing.getId(), dto);

        assertThat(result.getName()).isEqualTo(expectedName);
        assertThat(result.getCountry()).isEqualTo(expectedCountry);
        assertThat(result.getSlug()).isEqualTo("updated-name");
        verify(destinationRepository).save(existing);
    }

    @Test
    void updateDestination_sameNameKeepsSlug() {
        Destination existing = TestDataFactory.destination();
        DestinationDTO dto = TestDataFactory.destinationDTO();
        dto.setName(existing.getName());

        when(destinationRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(destinationRepository.save(any(Destination.class))).thenAnswer(inv -> inv.getArgument(0));

        DestinationDTO result = destinationService.updateDestination(existing.getId(), dto);

        assertThat(result.getSlug()).isEqualTo("test-destination");
    }

    @Test
    void updateDestination_notFound_throwsResourceNotFound() {
        UUID id = UUID.randomUUID();
        DestinationDTO dto = TestDataFactory.destinationDTO();
        when(destinationRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> destinationService.updateDestination(id, dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Destination");
    }

    @Test
    void deleteDestination_noActivities_deletes() {
        Destination dest = TestDataFactory.destination();
        dest.setActivities(new ArrayList<>());
        when(destinationRepository.findById(dest.getId())).thenReturn(Optional.of(dest));

        destinationService.deleteDestination(dest.getId());

        verify(destinationRepository).deleteById(dest.getId());
    }

    @Test
    void deleteDestination_withActivities_throwsBadRequest() {
        Destination dest = TestDataFactory.destination();
        Activity activity = TestDataFactory.activity(dest);
        dest.setActivities(List.of(activity));
        when(destinationRepository.findById(dest.getId())).thenReturn(Optional.of(dest));

        assertThatThrownBy(() -> destinationService.deleteDestination(dest.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Cannot delete destination")
                .hasMessageContaining("1 associated activities");
    }

    @Test
    void deleteDestination_notFound_throwsResourceNotFound() {
        UUID id = UUID.randomUUID();
        when(destinationRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> destinationService.deleteDestination(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Destination");
    }

    @Test
    void deleteDestination_nullActivitiesList_deletes() {
        Destination dest = TestDataFactory.destination();
        dest.setActivities(null);
        when(destinationRepository.findById(dest.getId())).thenReturn(Optional.of(dest));

        destinationService.deleteDestination(dest.getId());

        verify(destinationRepository).deleteById(dest.getId());
    }
}
