package com.instakill.analytics.api;

import com.instakill.analytics.application.AnalyticsService;
import com.instakill.analytics.application.UserAnalytics;
import com.instakill.infrastructure.security.JwtUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsRestController {

    private final AnalyticsService analyticsService;

    public AnalyticsRestController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /** Statisticile utilizatorului logat. */
    @GetMapping("/me")
    public UserAnalytics myAnalytics(@AuthenticationPrincipal JwtUserPrincipal principal) {
        return analyticsService.forUser(principal.id());
    }
}
