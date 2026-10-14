package com.myhive.backend.service;

import com.myhive.backend.TestDataFactory;
import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.dto.PriceQuoteRequest;
import com.myhive.backend.dto.PriceQuoteResponse;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.ActivityExtra;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.ExtraPricing;
import com.myhive.backend.entity.Package;
import com.myhive.backend.entity.PackageActivity;
import com.myhive.backend.exception.BadRequestException;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.repository.PackageRepository;
import com.myhive.backend.util.FromPrice;
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

    @Test
    void quote_addsTheSelectedExtrasOnTopOfTheLine() {
        // 40 × 2 = 80, plus the 250 per-group show = 330; the margin is on the 80 only: 72 + 250 = 322
        BigDecimal expectedTotal = new BigDecimal("330");
        Activity karting = savedActivityWithExtras(new BigDecimal("40.00"), extra("private-show", "250.00"));

        PriceQuoteRequest request = new PriceQuoteRequest();
        request.setItems(List.of(item(karting, null, "private-show")));
        request.setTravelers(2);

        PriceQuoteResponse quote = priceQuoteService.quote(request);
        assertThat(quote.fromPrice()).isEqualByComparingTo(FromPrice.of(expectedTotal, new BigDecimal("250")));
    }

    @Test
    void quote_sameActivityWithDifferentExtras_isTwoLines() {
        // 40 × 2 + 250 (with the show) and 40 × 2 again without it = 80 + 250 + 80 = 410; margin on the 160
        BigDecimal expectedTotal = new BigDecimal("410");
        Activity karting = savedActivityWithExtras(new BigDecimal("40.00"), extra("private-show", "250.00"));

        PriceQuoteRequest request = new PriceQuoteRequest();
        request.setItems(List.of(item(karting, null, "private-show"), item(karting, null)));
        request.setTravelers(2);

        PriceQuoteResponse quote = priceQuoteService.quote(request);
        assertThat(quote.fromPrice()).isEqualByComparingTo(FromPrice.of(expectedTotal, new BigDecimal("250")));
    }

    @Test
    void quote_unknownExtraCode_isIgnored() {
        BigDecimal expectedTotal = new BigDecimal("80");
        Activity karting = savedActivityWithExtras(new BigDecimal("40.00"), extra("private-show", "250.00"));

        PriceQuoteRequest request = new PriceQuoteRequest();
        request.setItems(List.of(item(karting, null, "nope")));
        request.setTravelers(2);

        PriceQuoteResponse quote = priceQuoteService.quote(request);
        assertThat(quote.fromPrice()).isEqualByComparingTo(FromPrice.of(expectedTotal));
    }

    @Test
    void quote_extraOnAPackageLine_isNotDiscounted() {
        // Package 50 %: 100 × 2 = 200 → 100; the 50 per-group extra stays 50 → 150; margin on the 100: 90 + 50
        BigDecimal expectedTotal = new BigDecimal("150");
        Activity karting = savedActivityWithExtras(new BigDecimal("100.00"), extra("private-show", "50.00"));
        Package halfOff = savedPackage(karting.getDestination(), new BigDecimal("50.00"), karting);

        PriceQuoteRequest request = new PriceQuoteRequest();
        request.setItems(List.of(item(karting, halfOff, "private-show")));
        request.setTravelers(2);

        PriceQuoteResponse quote = priceQuoteService.quote(request);
        assertThat(quote.fromPrice()).isEqualByComparingTo(FromPrice.of(expectedTotal, new BigDecimal("50")));
    }

    @Test
    void quote_pricesEachLinePerPersonTheWayThePlanIsPriced() {
        Destination prague = destinationRepository.save(TestDataFactory.destination("Prague"));
        Activity shooting = activityRepository.saveAndFlush(TestDataFactory.activity(prague, "Shooting", new BigDecimal("89.00")));
        Activity boat = TestDataFactory.activity(prague, "Boat", new BigDecimal("20.00"));
        boat.setMinPrice(new BigDecimal("400.00"));
        boat.setExtras(List.of(extra("private-show", "160.00")));
        boat = activityRepository.saveAndFlush(boat);
        Package classic = savedPackage(prague, new BigDecimal("15.00"), shooting);

        PriceQuoteRequest request = new PriceQuoteRequest();
        request.setItems(List.of(item(shooting, classic), item(boat, null, "private-show")));
        request.setTravelers(10);

        // Shooting in the package: 890 × 0.85 = 756.50, less the margin = 681 → 68.1 each, rounded up;
        // the boat: its group minimum 400 less the margin = 360, plus the 160 show in full = 520 → 52 each
        PriceQuoteResponse quote = priceQuoteService.quote(request);
        assertThat(quote.lines()).extracting(PriceQuoteResponse.LinePrice::activityId)
                .containsExactly(shooting.getId(), boat.getId());
        assertThat(quote.lines().get(0).packageId()).isEqualTo(classic.getId());
        assertThat(quote.lines().get(0).fromPricePerPerson()).isEqualByComparingTo("69");
        assertThat(quote.lines().get(1).fromPricePerPerson()).isEqualByComparingTo("52");
    }

    private Activity savedActivityWithExtras(BigDecimal price, ActivityExtra... extras) {
        Destination prague = destinationRepository.save(TestDataFactory.destination("Prague"));
        Activity activity = TestDataFactory.activity(prague, "Karting", price);
        activity.setExtras(List.of(extras));
        return activityRepository.saveAndFlush(activity);
    }

    private static ActivityExtra extra(String code, String perGroupPrice) {
        return new ActivityExtra(code, code, null, new BigDecimal(perGroupPrice), ExtraPricing.PER_GROUP, null);
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

    private static PriceQuoteRequest.Item item(Activity activity, Package pkg, String... extraCodes) {
        PriceQuoteRequest.Item item = new PriceQuoteRequest.Item();
        item.setActivityId(activity.getId());
        item.setPackageId(pkg == null ? null : pkg.getId());
        item.setExtraCodes(List.of(extraCodes));
        return item;
    }
}
