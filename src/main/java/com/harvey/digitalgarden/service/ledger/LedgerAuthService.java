package com.harvey.digitalgarden.service.ledger;

import com.harvey.digitalgarden.common.BusinessException;
import com.harvey.digitalgarden.dto.ledger.DefaultBudgetRequest;
import com.harvey.digitalgarden.dto.ledger.LedgerAuthResponse;
import com.harvey.digitalgarden.dto.ledger.LedgerMeVO;
import com.harvey.digitalgarden.dto.ledger.OnboardingRequest;
import com.harvey.digitalgarden.dto.ledger.ProfileSettingsRequest;
import com.harvey.digitalgarden.entity.LedgerUser;
import com.harvey.digitalgarden.ledger.LedgerInviteUtil;
import com.harvey.digitalgarden.repository.LedgerUserRepository;
import com.harvey.digitalgarden.security.JwtUtil;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerAuthService {

    private static final BigDecimal MAX_BUDGET = new BigDecimal("999999.99");

    private final LedgerUserRepository userRepository;
    private final JwtUtil jwtUtil;

    public LedgerAuthService(LedgerUserRepository userRepository, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
    }

    public LedgerAuthResponse invite(String rawCode) {
        String code;
        try {
            code = LedgerInviteUtil.normalizeInviteCode(rawCode == null ? "" : rawCode);
        } catch (IllegalArgumentException ex) {
            throw BusinessException.badRequest("邀请码格式无效");
        }

        LedgerUser user =
                userRepository
                        .findByInviteCode(code)
                        .orElseThrow(() -> BusinessException.notFound("邀请码无效"));

        if (!"active".equals(user.getStatus())) {
            throw BusinessException.forbidden("ACCOUNT_DISABLED");
        }

        String token = jwtUtil.generateLedgerToken(user.getId(), user.getUserSn());
        return new LedgerAuthResponse(token, user.getUserSn(), isOnboardingDone(user));
    }

    public LedgerMeVO me(long uid) {
        LedgerUser user = requireUser(uid);
        if (!"active".equals(user.getStatus())) {
            throw BusinessException.forbidden("ACCOUNT_DISABLED");
        }
        return toMeVO(user);
    }

    @Transactional
    public LedgerMeVO completeOnboarding(long uid, OnboardingRequest request) {
        LedgerUser user = requireActiveUser(uid);
        if (isOnboardingDone(user)) {
            throw BusinessException.conflict("ALREADY_ONBOARDED");
        }

        String displayName = validateDisplayName(request.getDisplayName());
        BigDecimal budget = validateDefaultBudget(request.getDefaultBudgetAmount());

        user.setDisplayName(displayName);
        user.setDefaultBudgetAmount(budget);
        userRepository.save(user);
        return toMeVO(user);
    }

    @Transactional
    public LedgerMeVO updateProfile(long uid, ProfileSettingsRequest request) {
        LedgerUser user = requireActiveUser(uid);
        user.setDisplayName(validateDisplayName(request.getDisplayName()));
        userRepository.save(user);
        return toMeVO(user);
    }

    @Transactional
    public LedgerMeVO updateDefaultBudget(long uid, DefaultBudgetRequest request) {
        LedgerUser user = requireActiveUser(uid);
        user.setDefaultBudgetAmount(validateDefaultBudget(request.getDefaultBudgetAmount()));
        userRepository.save(user);
        return toMeVO(user);
    }

    private LedgerUser requireUser(long uid) {
        return userRepository
                .findById(uid)
                .orElseThrow(() -> BusinessException.notFound("账本用户不存在"));
    }

    private LedgerUser requireActiveUser(long uid) {
        LedgerUser user = requireUser(uid);
        if (!"active".equals(user.getStatus())) {
            throw BusinessException.forbidden("ACCOUNT_DISABLED");
        }
        return user;
    }

    static boolean isOnboardingDone(LedgerUser user) {
        String displayName = user.getDisplayName();
        BigDecimal defaultBudget = user.getDefaultBudgetAmount();
        return displayName != null
                && !displayName.isBlank()
                && defaultBudget != null
                && defaultBudget.compareTo(BigDecimal.ZERO) > 0;
    }

    private static String validateDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            throw BusinessException.badRequest("display_name 不能为空");
        }
        String trimmed = displayName.trim();
        if (trimmed.isEmpty()) {
            throw BusinessException.badRequest("display_name 不能为空");
        }
        if (trimmed.length() > 64) {
            throw BusinessException.badRequest("display_name 长度不能超过 64");
        }
        return trimmed;
    }

    private static BigDecimal validateDefaultBudget(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw BusinessException.badRequest("default_budget_amount 必须大于 0");
        }
        if (amount.compareTo(MAX_BUDGET) > 0) {
            throw BusinessException.badRequest("default_budget_amount 不能超过 999999.99");
        }
        return amount;
    }

    private static LedgerMeVO toMeVO(LedgerUser user) {
        LedgerMeVO vo = new LedgerMeVO();
        vo.setUserSn(user.getUserSn());
        vo.setDisplayName(user.getDisplayName());
        vo.setDefaultBudgetAmount(user.getDefaultBudgetAmount());
        vo.setStatus(user.getStatus());
        vo.setOnboardingDone(isOnboardingDone(user));
        return vo;
    }
}
