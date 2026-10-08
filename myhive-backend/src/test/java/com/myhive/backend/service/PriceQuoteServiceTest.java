package com.myhive.backend.service;

import com.myhive.backend.TestDataFactory;
import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.dto.PriceQuoteRequest;
import com.myhive.backend.dto.PriceQuoteResponse;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.Package;
import com.myhive.backend.entity.PackageActivity;
import com.myhive.backend.exception.BadRequestException;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.repository.PackageRepository;
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
    @Autowired private PackageRepository packageRepository;

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

    @Test
    void quote_linesOfOnePackage_getItsCatalogDiscountAfterTheGroupMinimums() {
        Destination prague = destinationRepository.save(TestDataFactory.destination("Prague"));
        Activity shooting = activityRepository.saveAndFlush(TestDataFactory.activity(prague, "Shooting", new BigDecimal("89.00")));
        Activity steak = activityRepository.saveAndFlush(TestDataFactory.activity(prague, "Steak", new BigDecimal("59.00")));
        Activity boat = TestDataFactory.activity(prague, "Boat", new BigDecimal("20.00"));
        boat.setMinPrice(new BigDecimal("400.00"));
        boat = activityRepository.saveAndFlush(boat);
        Package classic = savedPackage(prague, new BigDecimal("15.00"), shooting, steak);

        PriceQuoteRequest request = new PriceQuoteRequest();
        request.setItems(List.of(item(shooting, classic), item(steak, classic), item(boat, null)));
        request.setTravelers(10);

        // Package: (89 × 10 + 59 × 10) × 0.85 = 1258; standalone: max(20 × 10, 400) = 400 → 1658,
        // less the margin = 1492.2 → 1492 → 149.2 each, rounded up
        PriceQuoteResponse quote = priceQuoteService.quote(request);
        assertThat(quote.fromPrice()).isEqualByComparingTo("1492");
        assertThat(quote.fromPricePerPerson()).isEqualByComparingTo("150");
    }

    @Test
    void quote_activityOutsideThePackageItNames_isRejectedAsTheBookingWouldBe() {
        Destination prague = destinationRepository.save(TestDataFactory.destination("Prague"));
        Activity shooting = activityRepository.saveAndFlush(TestDataFactory.activity(prague, "Shooting", new BigDecimal("89.00")));
        Activity boat = activityRepository.saveAndFlush(TestDataFactory.activity(prague, "Boat", new BigDecimal("20.00")));
        Package classic = savedPackage(prague, new BigDecimal("15.00"), shooting);

        PriceQuoteRequest request = new PriceQuoteRequest();
        request.setItems(List.of(item(shooting, classic), item(boat, classic)));
        request.setTravelers(4);

        assertThatThrownBy(() -> priceQuoteService.quote(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("does not belong to package");
    }

    @Test
    void quote_unknownPackage_isRejected() {
        Destination prague = destinationRepository.save(TestDataFactory.destination("Prague"));
        Activity shooting = activityRepository.saveAndFlush(TestDataFactory.activity(prague, "Shooting", new BigDecimal("89.00")));
        Package missing = TestDataFactory.pkg(prague);

        PriceQuoteRequest request = new PriceQuoteRequest();
        request.setItems(List.of(item(shooting, missing)));
        request.setTravelers(4);

        assertThatThrownBy(() -> priceQuoteService.quote(request)).isInstanceOf(BadRequestException.class);
    }

    private Package savedPackage(Destination destination, BigDecimal discountPct, Activity... activities) {
        Package pkg = TestDataFactory.pkg(destination);
        pkg.setId(null); // generated on insert; the factory's preset id would be merged as a phantom row
        pkg.setDiscountPct(discountPct);
        for (int position = 0; position < activities.length; position++) {
            pkg.getPackageActivities().add(new PackageActivity(pkg, activities[position], position));
        }
        return packageRepository.saveAndFlush(pkg);
    }

    private static PriceQuoteRequest.Item item(Activity activity, Package pkg) {
        PriceQuoteRequest.Item item = new PriceQuoteRequest.Item();
        item.setActivityId(activity.getId());
        item.setPackageId(pkg == null ? null : pkg.getId());
        return item;
    }
}
