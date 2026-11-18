package com.instakill.analytics.api;

import com.instakill.analytics.application.AnalyticsService;
import com.instakill.infrastructure.security.JwtUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AnalyticsWebController {

    private final AnalyticsService analyticsService;

    public AnalyticsWebController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/analytics")
    public String analytics(@AuthenticationPrincipal JwtUserPrincipal principal, Model model) {
        model.addAttribute("stats", analyticsService.forUser(principal.id()));
        return "analytics";
    }
}
