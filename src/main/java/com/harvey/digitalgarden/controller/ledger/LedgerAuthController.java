package com.harvey.digitalgarden.controller.ledger;

import com.harvey.digitalgarden.common.Result;
import com.harvey.digitalgarden.dto.ledger.DefaultBudgetRequest;
import com.harvey.digitalgarden.dto.ledger.LedgerAuthResponse;
import com.harvey.digitalgarden.dto.ledger.LedgerInviteRequest;
import com.harvey.digitalgarden.dto.ledger.LedgerMeVO;
import com.harvey.digitalgarden.dto.ledger.OnboardingRequest;
import com.harvey.digitalgarden.dto.ledger.ProfileSettingsRequest;
import com.harvey.digitalgarden.security.LedgerPrincipal;
import com.harvey.digitalgarden.service.ledger.LedgerAuthService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ledger")
public class LedgerAuthController {

    private final LedgerAuthService ledgerAuthService;

    public LedgerAuthController(LedgerAuthService ledgerAuthService) {
        this.ledgerAuthService = ledgerAuthService;
    }

    @PostMapping("/auth/invite")
    public Result<LedgerAuthResponse> invite(@Valid @RequestBody LedgerInviteRequest request) {
        return Result.success(ledgerAuthService.invite(request.getInviteCode()));
    }

    @GetMapping("/auth/me")
    public Result<LedgerMeVO> me(@AuthenticationPrincipal LedgerPrincipal principal) {
        return Result.success(ledgerAuthService.me(principal.uid()));
    }

    @PostMapping("/onboarding")
    public Result<LedgerMeVO> onboarding(
            @AuthenticationPrincipal LedgerPrincipal principal,
            @Valid @RequestBody OnboardingRequest request) {
        return Result.success(ledgerAuthService.completeOnboarding(principal.uid(), request));
    }

    @PutMapping("/settings/profile")
    public Result<LedgerMeVO> updateProfile(
            @AuthenticationPrincipal LedgerPrincipal principal,
            @Valid @RequestBody ProfileSettingsRequest request) {
        return Result.success(ledgerAuthService.updateProfile(principal.uid(), request));
    }

    @PutMapping("/settings/default-budget")
    public Result<LedgerMeVO> updateDefaultBudget(
            @AuthenticationPrincipal LedgerPrincipal principal,
            @Valid @RequestBody DefaultBudgetRequest request) {
        return Result.success(ledgerAuthService.updateDefaultBudget(principal.uid(), request));
    }
}
