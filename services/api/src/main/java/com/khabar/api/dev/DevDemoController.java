package com.khabar.api.dev;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** `local` profile only: put the demo's call list back as seeded, so the next person runs the same story. */
@RestController
@RequestMapping("/dev/demo")
@Profile("local")
public class DevDemoController {

    private final DemoData demoData;

    public DevDemoController(DemoData demoData) {
        this.demoData = demoData;
    }

    @PostMapping("/reset")
    public Map<String, Integer> reset() {
        return Map.of("replies", demoData.resetFollowUp());
    }
}
