package com.harvey.digitalgarden.controller;

import com.harvey.digitalgarden.common.Result;
import com.harvey.digitalgarden.dto.ledger.CreateLedgerUserResponse;
import com.harvey.digitalgarden.dto.ledger.LedgerUserAdminVO;
import com.harvey.digitalgarden.dto.ledger.LedgerUserStatusRequest;
import com.harvey.digitalgarden.service.ledger.LedgerUserAdminService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/ledger/users")
public class AdminLedgerUserController {

    private final LedgerUserAdminService ledgerUserAdminService;

    public AdminLedgerUserController(LedgerUserAdminService ledgerUserAdminService) {
        this.ledgerUserAdminService = ledgerUserAdminService;
    }

    @PostMapping
    public Result<CreateLedgerUserResponse> create() {
        return Result.success(ledgerUserAdminService.createUser());
    }

    @GetMapping
    public Result<List<LedgerUserAdminVO>> list() {
        return Result.success(ledgerUserAdminService.listUsers());
    }

    @PutMapping("/{id}/status")
    public Result<Void> setStatus(
            @PathVariable Long id, @Valid @RequestBody LedgerUserStatusRequest body) {
        ledgerUserAdminService.setStatus(id, body.getStatus());
        return Result.success();
    }
}
