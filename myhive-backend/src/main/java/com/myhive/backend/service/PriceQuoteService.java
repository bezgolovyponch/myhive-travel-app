package com.myhive.backend.service;

import com.myhive.backend.ai.plan.PlanPricer;
import com.myhive.backend.dto.PriceQuoteRequest;
import com.myhive.backend.dto.PriceQuoteResponse;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Package;
import com.myhive.backend.exception.BadRequestException;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.PackageRepository;
import com.myhive.backend.util.ActivityExtras;
import com.myhive.backend.util.FromPrice;
import com.myhive.backend.util.MoneyMath;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Prices a Trip Builder plan from the catalog, never from the browser, the way BookingService bills it:
 * each line is price × travelers with the activity's group minimum as the floor, the lines of one package
 * are then discounted together by the package's catalog percentage, the selected extras of every line are
 * added at list price (never floored, never discounted), and the plan shows the {@link FromPrice} of the
 * sum, in total and per traveller, and per traveller for each line (the organiser's dashboard shows those).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PriceQuoteService {

    private final ActivityRepository activityRepository;
    private final PackageRepository packageRepository;

    /**
     * One line of the plan; the same activity twice in one package (or twice standalone) with the same extras
     * is one line. {@code extraCodes} is sorted and deduplicated so the order the browser sends them in does
     * not split a line.
     */
    private record Line(UUID activityId, UUID packageId, List<String> extraCodes) {

        static Line of(UUID activityId, UUID packageId, List<String> codes) {
            List<String> normalized = codes == null ? List.of() : codes.stream()
                    .filter(Objects::nonNull)
                    .map(String::strip)
                    .filter(code -> !code.isEmpty())
                    .distinct()
                    .sorted()
                    .toList();
            return new Line(activityId, packageId, normalized);
        }
    }

    public PriceQuoteResponse quote(PriceQuoteRequest request) {
        Set<Line> lines = linesOf(request);
        Map<UUID, Activity> activitiesById = activitiesOf(lines);
        Map<UUID, Package> packagesById = packagesOf(lines);
        int travelers = request.getTravelers();
        // Floor each line first, then discount the package's sum (floor-before-discount), keyed by
        // package id with null for the standalone lines. Extras are summed apart: no discount touches them.
        Map<UUID, BigDecimal> basesByPackage = new LinkedHashMap<>();
        BigDecimal extras = BigDecimal.ZERO;
        List<PriceQuoteResponse.LinePrice> linePrices = new ArrayList<>();
        for (Line line : lines) {
            Activity activity = activitiesById.get(line.activityId());
            if (line.packageId() != null && !packagesById.get(line.packageId()).containsActivity(activity.getId())) {
                // The booking refuses this pairing (BookingService), so no price is quoted for it either.
                throw new BadRequestException("Activity " + activity.getId()
                        + " does not belong to package " + line.packageId());
            }
            BigDecimal base = PlanPricer.lineTotal(activity.getPrice(), activity.getMinPrice(), travelers);
            basesByPackage.merge(line.packageId(), base, BigDecimal::add);
            BigDecimal lineExtras = MoneyMath.extrasTotal(
                    ActivityExtras.select(activity.getExtras(), line.extraCodes()), travelers);
            extras = extras.add(lineExtras);
            // The line's share of the plan's price: its package's discount on its own base, then the margin.
            BigDecimal discounted = line.packageId() == null ? base
                    : MoneyMath.applyDiscountPct(base, packagesById.get(line.packageId()).getDiscountPct());
            linePrices.add(new PriceQuoteResponse.LinePrice(line.activityId(), line.packageId(),
                    FromPrice.perPerson(discounted.add(lineExtras), lineExtras, travelers)));
        }
        BigDecimal total = extras;
        for (Map.Entry<UUID, BigDecimal> group : basesByPackage.entrySet()) {
            total = total.add(group.getKey() == null
                    ? group.getValue()
                    : MoneyMath.applyDiscountPct(group.getValue(), packagesById.get(group.getKey()).getDiscountPct()));
        }
        // The margin is on the activities; the extras go on top at their full price.
        return new PriceQuoteResponse(FromPrice.of(total, extras), FromPrice.perPerson(total, extras, travelers),
                linePrices);
    }

    private static Set<Line> linesOf(PriceQuoteRequest request) {
        Set<Line> lines = new LinkedHashSet<>();
        if (request.getItems() != null) {
            request.getItems().forEach(item ->
                    lines.add(Line.of(item.getActivityId(), item.getPackageId(), item.getExtraCodes())));
        } else if (request.getActivityIds() != null) {
            request.getActivityIds().forEach(activityId -> lines.add(Line.of(activityId, null, null)));
        }
        if (lines.isEmpty()) {
            throw new BadRequestException("Nothing to price: items must not be empty");
        }
        return lines;
    }

    private Map<UUID, Activity> activitiesOf(Set<Line> lines) {
        Set<UUID> ids = lines.stream().map(Line::activityId).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, Activity> byId = activityRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Activity::getId, Function.identity()));
        for (UUID id : ids) {
            if (!byId.containsKey(id)) {
                throw new BadRequestException("activityId " + id + " does not exist");
            }
        }
        return byId;
    }

    private Map<UUID, Package> packagesOf(Set<Line> lines) {
        List<UUID> ids = lines.stream().map(Line::packageId).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Package> byId = packageRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Package::getId, Function.identity()));
        for (UUID id : ids) {
            if (!byId.containsKey(id)) {
                throw new BadRequestException("packageId " + id + " does not exist");
            }
        }
        return byId;
    }
}
