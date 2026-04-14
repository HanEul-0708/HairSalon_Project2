package com.hairsalonproject2.common.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class DeploymentInfoController {

    private final Environment environment;
    private final String assetVersion;
    private final Instant startedAt = Instant.now();

    public DeploymentInfoController(Environment environment,
                                    @Value("${app.asset-version:${APP_ASSET_VERSION:local}}") String assetVersion) {
        this.environment = environment;
        this.assetVersion = assetVersion;
    }

    @GetMapping("/system/version")
    public Map<String, String> version() {
        Map<String, String> response = new LinkedHashMap<>();
        response.put("assetVersion", assetVersion);
        response.put("activeProfiles", String.join(",", environment.getActiveProfiles()));
        response.put("process", ManagementFactory.getRuntimeMXBean().getName());
        response.put("startedAt", startedAt.toString());
        return response;
    }
}
