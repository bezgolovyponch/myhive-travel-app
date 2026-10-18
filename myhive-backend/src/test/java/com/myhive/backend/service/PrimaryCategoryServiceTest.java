package com.myhive.backend.service;

import com.myhive.backend.TestDataFactory;
import com.myhive.backend.dto.PrimaryCategoryDTO;
import com.myhive.backend.entity.PrimaryCategory;
import com.myhive.backend.repository.PrimaryCategoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrimaryCategoryServiceTest {

    @Mock
    private PrimaryCategoryRepository primaryCategoryRepository;

    @InjectMocks
    private PrimaryCategoryService primaryCategoryService;

    @Test
    void getAll_ordersBySortOrderAndLocalizesName() {
        String expectedGermanName = "Schießen";
        PrimaryCategory shooting = TestDataFactory.primaryCategory("shooting");
        shooting.setId(UUID.randomUUID());
        shooting.setName("Shooting");
        shooting.setSlugDe("schiessen");
        shooting.setTranslations(Map.of("de", Map.of("name", expectedGermanName)));
        PrimaryCategory boats = TestDataFactory.primaryCategory("boats");
        boats.setId(UUID.randomUUID());
        boats.setName("Boats");
        when(primaryCategoryRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of(shooting, boats));

        List<PrimaryCategoryDTO> german = primaryCategoryService.getAll("de");

        assertThat(german).extracting(PrimaryCategoryDTO::getSlug).containsExactly("shooting", "boats");
        assertThat(german.getFirst().getName()).isEqualTo(expectedGermanName);
        assertThat(german.getFirst().getSlugDe()).isEqualTo("schiessen");
        assertThat(german.get(1).getName()).isEqualTo("Boats");

        List<PrimaryCategoryDTO> english = primaryCategoryService.getAll(null);

        assertThat(english.getFirst().getName()).isEqualTo("Shooting");
    }
}
