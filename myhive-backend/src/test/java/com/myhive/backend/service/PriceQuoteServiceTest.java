package com.myhive.backend.service;

import com.myhive.backend.TestDataFactory;
import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.dto.PriceQuoteRequest;
import com.myhive.backend.dto.PriceQuoteResponse;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.exception.BadRequestException;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.DestinationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@Import(TestSecurityConfig.class)
class PriceQuoteServiceTest {

    @Autowired private PriceQuoteService priceQuoteService;
    @Autowired private DestinationRepository destinationRepository;
    @Autowired private ActivityRepository activityRepository;

    @Test
    void quote_isTheGroupTotalWithGroupMinimumsLessTheMarginInWholeEuros() {
        Destination prague = destinationRepository.save(TestDataFactory.destination("Prague"));
        Activity shooting = activityRepository.saveAndFlush(TestDataFactory.activity(prague, "Shooting", new BigDecimal("89.00")));
        Activity boat = TestDataFactory.activity(prague, "Boat", new BigDecimal("20.00"));
        boat.setMinPrice(new BigDecimal("400.00"));
        boat = activityRepository.saveAndFlush(boat);

        PriceQuoteRequest request = new PriceQuoteRequest();
        request.setActivityIds(List.of(shooting.getId(), boat.getId(), shooting.getId()));
        request.setTravelers(10);

        // 89 × 10 + max(20 × 10, 400) = 1290 → less the margin = 1161 → 116.1 each, rounded up
        PriceQuoteResponse quote = priceQuoteService.quote(request);
        assertThat(quote.fromPrice()).isEqualByComparingTo("1161");
        assertThat(quote.fromPricePerPerson()).isEqualByComparingTo("117");
    }

    @Test
    void quote_unknownActivity_isRejected() {
        PriceQuoteRequest request = new PriceQuoteRequest();
        request.setActivityIds(List.of(UUID.randomUUID()));
        request.setTravelers(4);

        assertThatThrownBy(() -> priceQuoteService.quote(request)).isInstanceOf(BadRequestException.class);
    }
}
