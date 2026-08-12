package com.harvey.digitalgarden.controller;

import com.harvey.digitalgarden.common.Result;
import com.harvey.digitalgarden.dto.game.SemanticIdentityVO;
import com.harvey.digitalgarden.dto.game.SemanticStatusVO;
import com.harvey.digitalgarden.game.semantic.DeviceIdentityService;
import com.harvey.digitalgarden.game.semantic.SemanticRoundAdminService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/games/semantic")
public class GameSemanticController {

    private final SemanticRoundAdminService adminService;
    private final DeviceIdentityService identityService;

    public GameSemanticController(
            SemanticRoundAdminService adminService, DeviceIdentityService identityService) {
        this.adminService = adminService;
        this.identityService = identityService;
    }

    @GetMapping("/status")
    public Result<SemanticStatusVO> status() {
        return Result.success(adminService.getPublicStatus());
    }

    /** Lookup last nickname bound to this browser fingerprint (empty if unknown). */
    @GetMapping("/identity")
    public Result<SemanticIdentityVO> identity(@RequestParam("fp") String fp) {
        SemanticIdentityVO vo = new SemanticIdentityVO();
        vo.setUsername(identityService.findUsername(fp));
        return Result.success(vo);
    }
}
