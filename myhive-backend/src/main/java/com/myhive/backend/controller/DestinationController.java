package com.myhive.backend.controller;

import com.myhive.backend.dto.DestinationDTO;
import com.myhive.backend.service.DestinationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/destinations")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DestinationController {

    private final DestinationService destinationService;

    @GetMapping
    public ResponseEntity<List<DestinationDTO>> getAllDestinations() {
        return ResponseEntity.ok(destinationService.getAllDestinations());
    }

    @GetMapping("/{id}")
    public ResponseEntity<DestinationDTO> getDestinationById(@PathVariable UUID id) {
        return ResponseEntity.ok(destinationService.getDestinationById(id));
    }
}
