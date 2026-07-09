package com.buddywolfy.angrywolfy.web.view;

import com.buddywolfy.angrywolfy.service.DashboardService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardViewController {

    private final DashboardService dashboardService;

    public DashboardViewController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/overview")
    public String overview(Model model) {
        model.addAttribute("dashboard", dashboardService.getDashboard());
        return "overview";
    }
}
