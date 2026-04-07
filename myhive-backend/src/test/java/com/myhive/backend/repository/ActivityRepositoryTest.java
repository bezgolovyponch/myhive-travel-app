package com.myhive.backend.repository;

import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Destination;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ActivityRepositoryTest {

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private DestinationRepository destinationRepository;

    private UUID destinationId;

    @BeforeEach
    void setUp() {
        Destination dest = new Destination();
        dest.setName("Rome");
        dest.setCountry("Italy");
        dest = destinationRepository.save(dest);
        destinationId = dest.getId();

        Activity a1 = new Activity();
        a1.setDestination(dest);
        a1.setName("Colosseum");
        a1.setPrice(new BigDecimal("20.00"));
        a1.setCategory("History");
        activityRepository.save(a1);

        Activity a2 = new Activity();
        a2.setDestination(dest);
        a2.setName("Pasta Class");
        a2.setPrice(new BigDecimal("40.00"));
        a2.setCategory("Food");
        activityRepository.save(a2);
    }

    @Test
    void findByDestinationId_returnsActivities() {
        var result = activityRepository.findByDestinationId(destinationId);
        assertThat(result).hasSize(2);
    }

    @Test
    void findByCategory_returnsMatchingActivities() {
        var result = activityRepository.findByCategory("History");
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getName()).isEqualTo("Colosseum");
    }

    @Test
    void findByDestinationIdAndCategory_returnsCombinedFilter() {
        var result = activityRepository.findByDestinationIdAndCategory(destinationId, "Food");
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getName()).isEqualTo("Pasta Class");
    }
}
