package com.harvey.digitalgarden.controller;

import com.harvey.digitalgarden.common.BusinessException;
import com.harvey.digitalgarden.common.Result;
import com.harvey.digitalgarden.dto.game.SemanticRoundAdminVO;
import com.harvey.digitalgarden.dto.game.SemanticWordBatchRequest;
import com.harvey.digitalgarden.dto.game.SemanticWordRequest;
import com.harvey.digitalgarden.dto.game.SemanticWordVO;
import com.harvey.digitalgarden.game.semantic.SemanticRoundAdminService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/games/semantic")
public class AdminGameSemanticController {

    private final SemanticRoundAdminService adminService;

    public AdminGameSemanticController(SemanticRoundAdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/status")
    public Result<SemanticRoundAdminVO> status() {
        return Result.success(adminService.getAdminStatus());
    }

    @GetMapping("/words")
    public Result<List<SemanticWordVO>> listWords() {
        return Result.success(adminService.listWords());
    }

    @PostMapping("/words")
    public Result<List<SemanticWordVO>> addWords(@Valid @RequestBody SemanticWordBatchRequest req) {
        return Result.success(adminService.addWords(req.getWords()));
    }

    @PatchMapping("/words/{id}/skip")
    public Result<Void> skipWord(@PathVariable Long id) {
        adminService.skipWord(id);
        return Result.success();
    }

    @PatchMapping("/words/{id}/order")
    public Result<Void> reorder(@PathVariable Long id, @RequestBody SemanticWordRequest req) {
        if (req == null || req.getQueueOrder() == null) {
            throw BusinessException.badRequest("queueOrder 不能为空");
        }
        adminService.reorder(id, req.getQueueOrder());
        return Result.success();
    }

    @PatchMapping("/words/{id}/hints")
    public Result<SemanticWordVO> updateHints(
            @PathVariable Long id, @RequestBody SemanticWordRequest req) {
        if (req == null) {
            throw BusinessException.badRequest("请求体不能为空");
        }
        return Result.success(adminService.updateHints(id, req.getHint1(), req.getHint2(), req.getHint3()));
    }

    @PostMapping("/rounds/start")
    public Result<SemanticRoundAdminVO> startRound() {
        return Result.success(adminService.startRound());
    }

    @PostMapping("/rounds/stop")
    public Result<SemanticRoundAdminVO> stopRound() {
        return Result.success(adminService.stopRound());
    }
}
