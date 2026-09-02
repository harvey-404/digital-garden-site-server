package com.harvey.digitalgarden.controller.ledger;

import com.harvey.digitalgarden.common.Result;
import com.harvey.digitalgarden.dto.ledger.LedgerExpenseRequest;
import com.harvey.digitalgarden.dto.ledger.LedgerExpenseVO;
import com.harvey.digitalgarden.security.LedgerPrincipal;
import com.harvey.digitalgarden.service.ledger.LedgerExpenseService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ledger/expenses")
public class LedgerExpenseController {

    private final LedgerExpenseService ledgerExpenseService;

    public LedgerExpenseController(LedgerExpenseService ledgerExpenseService) {
        this.ledgerExpenseService = ledgerExpenseService;
    }

    @GetMapping
    public Result<List<LedgerExpenseVO>> list(
            @AuthenticationPrincipal LedgerPrincipal principal,
            @RequestParam String month) {
        return Result.success(ledgerExpenseService.listByMonth(principal.uid(), month));
    }

    @PostMapping
    public Result<LedgerExpenseVO> create(
            @AuthenticationPrincipal LedgerPrincipal principal,
            @Valid @RequestBody LedgerExpenseRequest request) {
        return Result.success(ledgerExpenseService.createExpense(principal.uid(), request));
    }

    @PutMapping("/{id}")
    public Result<LedgerExpenseVO> update(
            @AuthenticationPrincipal LedgerPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody LedgerExpenseRequest request) {
        return Result.success(ledgerExpenseService.updateExpense(principal.uid(), id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @AuthenticationPrincipal LedgerPrincipal principal, @PathVariable Long id) {
        ledgerExpenseService.softDelete(principal.uid(), id);
        return Result.success();
    }
}
