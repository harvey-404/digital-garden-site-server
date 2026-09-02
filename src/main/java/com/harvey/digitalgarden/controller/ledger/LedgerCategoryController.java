package com.harvey.digitalgarden.controller.ledger;

import com.harvey.digitalgarden.common.Result;
import com.harvey.digitalgarden.dto.ledger.LedgerCategoryRequest;
import com.harvey.digitalgarden.dto.ledger.LedgerCategoryVO;
import com.harvey.digitalgarden.security.LedgerPrincipal;
import com.harvey.digitalgarden.service.ledger.LedgerCategoryService;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ledger/categories")
public class LedgerCategoryController {

    private final LedgerCategoryService ledgerCategoryService;

    public LedgerCategoryController(LedgerCategoryService ledgerCategoryService) {
        this.ledgerCategoryService = ledgerCategoryService;
    }

    @GetMapping
    public Result<List<LedgerCategoryVO>> list(@AuthenticationPrincipal LedgerPrincipal principal) {
        return Result.success(ledgerCategoryService.listCategories(principal.uid()));
    }

    @PostMapping
    public Result<LedgerCategoryVO> create(
            @AuthenticationPrincipal LedgerPrincipal principal,
            @Valid @RequestBody LedgerCategoryRequest request) {
        return Result.success(ledgerCategoryService.createCategory(principal.uid(), request));
    }

    @PutMapping("/{id}")
    public Result<LedgerCategoryVO> update(
            @AuthenticationPrincipal LedgerPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody LedgerCategoryRequest request) {
        return Result.success(ledgerCategoryService.updateCategory(principal.uid(), id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @AuthenticationPrincipal LedgerPrincipal principal, @PathVariable Long id) {
        ledgerCategoryService.softDelete(principal.uid(), id);
        return Result.success();
    }
}
