package com.myhive.backend.service;

import com.myhive.backend.TestDataFactory;
import com.myhive.backend.dto.PackageActivityRefDTO;
import com.myhive.backend.dto.PackageDTO;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.Package;
import com.myhive.backend.entity.PackageActivity;
import com.myhive.backend.exception.BadRequestException;
import com.myhive.backend.exception.ResourceNotFoundException;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.CategoryRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.repository.PackageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PackageServiceTest {

    @Mock private PackageRepository packageRepository;
    @Mock private DestinationRepository destinationRepository;
    @Mock private ActivityRepository activityRepository;
    @Mock private CategoryRepository categoryRepository;

    @InjectMocks private PackageService packageService;

    private Destination destination;
    private Activity activity1;
    private Activity activity2;
    private Package pkg;

    @BeforeEach
    void setUp() {
        destination = TestDataFactory.destination();
        activity1 = TestDataFactory.activity(destination);
        activity1.setPrice(new BigDecimal("100.00"));
        activity2 = TestDataFactory.activity(destination);
        activity2.setPrice(new BigDecimal("200.00"));
        pkg = TestDataFactory.pkg(destination);
        pkg.getPackageActivities().add(new PackageActivity(pkg, activity1, 0));
        pkg.getPackageActivities().add(new PackageActivity(pkg, activity2, 1));
    }

    @Test
    void getBySlugReturnsDtoWithComputedPrices() {
        when(packageRepository.findBySlug("test-package")).thenReturn(Optional.of(pkg));

        PackageDTO dto = packageService.getPackageBySlug("test-package");

        BigDecimal expectedOriginal = new BigDecimal("300.00");
        BigDecimal expectedDiscounted = new BigDecimal("255.00");
        BigDecimal expectedSavings = new BigDecimal("45.00");
        assertThat(dto.getOriginalPrice()).isEqualByComparingTo(expectedOriginal);
        assertThat(dto.getDiscountedPrice()).isEqualByComparingTo(expectedDiscounted);
        assertThat(dto.getSavings()).isEqualByComparingTo(expectedSavings);
        assertThat(dto.getActivities()).hasSize(2);
        assertThat(dto.getActivities().getFirst().getPosition()).isEqualTo(0);
    }

    @Test
    void getBySlugRoundsDiscountedPriceToWholeEuros() {
        // 523.08 - 15% = 444.618 -> must surface as 445.00, not 444.62
        activity1.setPrice(new BigDecimal("523.08"));
        pkg.getPackageActivities().clear();
        pkg.getPackageActivities().add(new PackageActivity(pkg, activity1, 0));
        pkg.setDiscountPct(new BigDecimal("15.00"));
        when(packageRepository.findBySlug("test-package")).thenReturn(Optional.of(pkg));

        PackageDTO dto = packageService.getPackageBySlug("test-package");

        assertThat(dto.getDiscountedPrice()).isEqualByComparingTo("445.00");
        assertThat(dto.getSavings())
                .isEqualByComparingTo(dto.getOriginalPrice().subtract(dto.getDiscountedPrice()));
    }

    @Test
    void getBySlugThrowsWhenMissing() {
        when(packageRepository.findBySlug("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> packageService.getPackageBySlug("missing"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getByIdThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(packageRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> packageService.getPackageById(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createRejectsActivitiesFromOtherDestination() {
        Destination other = TestDataFactory.destination();
        other.setId(UUID.randomUUID());
        Activity foreign = TestDataFactory.activity(other);

        when(destinationRepository.findById(destination.getId())).thenReturn(Optional.of(destination));
        when(activityRepository.findAllById(List.of(foreign.getId()))).thenReturn(List.of(foreign));

        PackageDTO dto = new PackageDTO();
        dto.setDestinationId(destination.getId());
        dto.setName("New");
        dto.setDiscountPct(new BigDecimal("10.00"));
        dto.setActivities(List.of(new PackageActivityRefDTO(
                foreign.getId(), 0, null, null, null, null, null)));

        assertThatThrownBy(() -> packageService.createPackage(dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("destination");
    }

    @Test
    void deleteThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(packageRepository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> packageService.deletePackage(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
