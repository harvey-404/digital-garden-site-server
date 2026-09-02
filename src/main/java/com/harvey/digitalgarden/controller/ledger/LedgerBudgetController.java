package com.harvey.digitalgarden.controller.ledger;

import com.harvey.digitalgarden.common.Result;
import com.harvey.digitalgarden.dto.ledger.ActivateBudgetRequest;
import com.harvey.digitalgarden.dto.ledger.LedgerBudgetVO;
import com.harvey.digitalgarden.dto.ledger.UpdateBudgetAmountRequest;
import com.harvey.digitalgarden.security.LedgerPrincipal;
import com.harvey.digitalgarden.service.ledger.LedgerBudgetService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ledger/budgets")
public class LedgerBudgetController {

    private final LedgerBudgetService ledgerBudgetService;

    public LedgerBudgetController(LedgerBudgetService ledgerBudgetService) {
        this.ledgerBudgetService = ledgerBudgetService;
    }

    @GetMapping
    public Result<List<LedgerBudgetVO>> list(@AuthenticationPrincipal LedgerPrincipal principal) {
        return Result.success(ledgerBudgetService.listBudgets(principal.uid()));
    }

    @PostMapping
    public Result<LedgerBudgetVO> activate(
            @AuthenticationPrincipal LedgerPrincipal principal,
            @Valid @RequestBody ActivateBudgetRequest request) {
        return Result.success(ledgerBudgetService.activateMonth(principal.uid(), request.getMonth()));
    }

    @PutMapping("/{id}")
    public Result<LedgerBudgetVO> updateAmount(
            @AuthenticationPrincipal LedgerPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody UpdateBudgetAmountRequest request) {
        return Result.success(
                ledgerBudgetService.updateAmount(principal.uid(), id, request.getAmount()));
    }
}
