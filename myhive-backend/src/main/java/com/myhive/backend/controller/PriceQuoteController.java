package com.myhive.backend.controller;

import com.myhive.backend.dto.PriceQuoteRequest;
import com.myhive.backend.dto.PriceQuoteResponse;
import com.myhive.backend.service.PriceQuoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/pricing")
@RequiredArgsConstructor
public class PriceQuoteController {

    private final PriceQuoteService priceQuoteService;

    @PostMapping("/quote")
    public PriceQuoteResponse quote(@Valid @RequestBody PriceQuoteRequest request) {
        return priceQuoteService.quote(request);
    }
}
