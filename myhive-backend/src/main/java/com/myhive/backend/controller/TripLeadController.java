package com.myhive.backend.controller;

import com.myhive.backend.dto.TripLeadCreateRequest;
import com.myhive.backend.dto.TripLeadCreateResponse;
import com.myhive.backend.dto.TripLeadRestoreResponse;
import com.myhive.backend.dto.TripLeadSyncRequest;
import com.myhive.backend.dto.TripLeadUnsubscribeRequest;
import com.myhive.backend.service.TripLeadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/leads")
@RequiredArgsConstructor
public class TripLeadController {

    private final TripLeadService tripLeadService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TripLeadCreateResponse create(@Valid @RequestBody TripLeadCreateRequest request) {
        return tripLeadService.create(request);
    }

    @PatchMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sync(@PathVariable UUID id, @Valid @RequestBody TripLeadSyncRequest request) {
        tripLeadService.sync(id, request);
    }

    @GetMapping("/restore/{restoreToken}")
    public TripLeadRestoreResponse restore(@PathVariable UUID restoreToken) {
        return tripLeadService.restore(restoreToken);
    }

    @PostMapping("/unsubscribe")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(@Valid @RequestBody TripLeadUnsubscribeRequest request) {
        tripLeadService.unsubscribe(request.getToken());
    }

    /** RFC 8058 one-click target — mail providers POST here with no meaningful body. */
    @PostMapping("/unsubscribe/one-click")
    @ResponseStatus(HttpStatus.OK)
    public void unsubscribeOneClick(@RequestParam UUID token) {
        tripLeadService.unsubscribe(token);
    }
}
