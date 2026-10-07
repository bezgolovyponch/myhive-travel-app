package com.myhive.backend.service;

import com.myhive.backend.ai.plan.PlanPricer;
import com.myhive.backend.dto.PriceQuoteRequest;
import com.myhive.backend.dto.PriceQuoteResponse;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Package;
import com.myhive.backend.exception.BadRequestException;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.PackageRepository;
import com.myhive.backend.util.FromPrice;
import com.myhive.backend.util.MoneyMath;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
 * are then discounted together by the package's catalog percentage, and the plan shows the
 * {@link FromPrice} of the sum, in total and per traveller.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PriceQuoteService {

    private final ActivityRepository activityRepository;
    private final PackageRepository packageRepository;

    /** One line of the plan; the same activity twice in one package (or twice standalone) is one line. */
    private record Line(UUID activityId, UUID packageId) {
    }

    public PriceQuoteResponse quote(PriceQuoteRequest request) {
        Set<Line> lines = linesOf(request);
        Map<UUID, Activity> activitiesById = activitiesOf(lines);
        Map<UUID, Package> packagesById = packagesOf(lines);
        int travelers = request.getTravelers();
        // Floor each line first, then discount the package's sum (floor-before-discount), keyed by
        // package id with null for the standalone lines.
        Map<UUID, BigDecimal> totalsByPackage = new LinkedHashMap<>();
        for (Line line : lines) {
            Activity activity = activitiesById.get(line.activityId());
            if (line.packageId() != null && !packagesById.get(line.packageId()).containsActivity(activity.getId())) {
                // The booking refuses this pairing (BookingService), so no price is quoted for it either.
                throw new BadRequestException("Activity " + activity.getId()
                        + " does not belong to package " + line.packageId());
            }
            BigDecimal lineTotal = PlanPricer.lineTotal(activity.getPrice(), activity.getMinPrice(), travelers);
            totalsByPackage.merge(line.packageId(), lineTotal, BigDecimal::add);
        }
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<UUID, BigDecimal> group : totalsByPackage.entrySet()) {
            total = total.add(group.getKey() == null
                    ? group.getValue()
                    : MoneyMath.applyDiscountPct(group.getValue(), packagesById.get(group.getKey()).getDiscountPct()));
        }
        return new PriceQuoteResponse(FromPrice.of(total), FromPrice.perPerson(total, travelers));
    }

    private static Set<Line> linesOf(PriceQuoteRequest request) {
        Set<Line> lines = new LinkedHashSet<>();
        if (request.getItems() != null) {
            request.getItems().forEach(item -> lines.add(new Line(item.getActivityId(), item.getPackageId())));
        } else if (request.getActivityIds() != null) {
            request.getActivityIds().forEach(activityId -> lines.add(new Line(activityId, null)));
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
