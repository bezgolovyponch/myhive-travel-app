package com.myhive.backend.service;

import com.myhive.backend.dto.PriceQuoteRequest;
import com.myhive.backend.dto.PriceQuoteResponse;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.exception.BadRequestException;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.util.FromPrice;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Prices a Trip Builder plan from the catalog, never from the browser: each line is price × travelers
 * with the activity's group minimum as the floor (as BookingService bills it), and the plan shows the
 * {@link FromPrice} of the sum, in total and per traveller.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PriceQuoteService {

    private final ActivityRepository activityRepository;

    public PriceQuoteResponse quote(PriceQuoteRequest request) {
        List<UUID> ids = List.copyOf(new LinkedHashSet<>(request.getActivityIds()));
        Map<UUID, Activity> byId = activityRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Activity::getId, Function.identity()));
        BigDecimal travelers = BigDecimal.valueOf(request.getTravelers());
        BigDecimal total = BigDecimal.ZERO;
        for (UUID id : ids) {
            Activity activity = byId.get(id);
            if (activity == null) {
                throw new BadRequestException("activityId " + id + " does not exist");
            }
            total = total.add(lineTotal(activity, travelers));
        }
        BigDecimal fromPrice = FromPrice.of(total);
        BigDecimal perPerson = fromPrice == null ? null : fromPrice.divide(travelers, 0, RoundingMode.CEILING);
        return new PriceQuoteResponse(fromPrice, perPerson);
    }

    private static BigDecimal lineTotal(Activity activity, BigDecimal travelers) {
        BigDecimal line = activity.getPrice().multiply(travelers);
        BigDecimal minPrice = activity.getMinPrice();
        if (minPrice != null && line.compareTo(minPrice) < 0) {
            return minPrice;
        }
        return line;
    }
}
