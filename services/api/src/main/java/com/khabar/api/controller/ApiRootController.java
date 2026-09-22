package com.khabar.api.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * The API's own address, opened in a browser: send people to the app instead of showing a bare 401.
 * Set khabar.web.app-url (WEB_APP_URL) to the site; without it, say what this is.
 */
@RestController
public class ApiRootController {

    private final String appUrl;

    public ApiRootController(@Value("${khabar.web.app-url:}") String appUrl) {
        this.appUrl = appUrl.trim();
    }

    @GetMapping("/")
    public ResponseEntity<Map<String, String>> root() {
        if (!appUrl.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, appUrl).build();
        }
        return ResponseEntity.ok(Map.of(
                "service", "Khabar clinical API",
                "health", "/api/health",
                "note", "This is the API behind the Khabar app. Open the app's own address to use it."));
    }
}
