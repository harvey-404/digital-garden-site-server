package com.harvey.digitalgarden.controller;

import com.harvey.digitalgarden.common.Result;
import com.harvey.digitalgarden.dto.game.SemanticStatusVO;
import com.harvey.digitalgarden.game.semantic.SemanticRoundAdminService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/games/semantic")
public class GameSemanticController {

    private final SemanticRoundAdminService adminService;

    public GameSemanticController(SemanticRoundAdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/status")
    public Result<SemanticStatusVO> status() {
        return Result.success(adminService.getPublicStatus());
    }
}