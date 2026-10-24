package com.akanksha.url_shortener.controller;

import com.akanksha.url_shortener.dto.ShortenRequest;
import com.akanksha.url_shortener.dto.ShortenResponse;
import com.akanksha.url_shortener.dto.StatsResponse;
import com.akanksha.url_shortener.service.UrlService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/urls")
public class UrlController {

    private final UrlService service;

    public UrlController(UrlService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ShortenResponse> shorten(@Valid @RequestBody ShortenRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.shorten(request));
    }

    @GetMapping("/{shortCode}/stats")
    public StatsResponse stats(@PathVariable String shortCode) {
        return service.stats(shortCode);
    }
}
