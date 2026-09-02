package com.harvey.digitalgarden.controller.ledger;

import com.harvey.digitalgarden.common.Result;
import com.harvey.digitalgarden.dto.ledger.LedgerDashboardVO;
import com.harvey.digitalgarden.security.LedgerPrincipal;
import com.harvey.digitalgarden.service.ledger.LedgerDashboardService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ledger")
public class LedgerDashboardController {

    private final LedgerDashboardService ledgerDashboardService;

    public LedgerDashboardController(LedgerDashboardService ledgerDashboardService) {
        this.ledgerDashboardService = ledgerDashboardService;
    }

    @GetMapping("/dashboard")
    public Result<LedgerDashboardVO> dashboard(
            @AuthenticationPrincipal LedgerPrincipal principal, @RequestParam String month) {
        return Result.success(ledgerDashboardService.getDashboard(principal.uid(), month));
    }
}
