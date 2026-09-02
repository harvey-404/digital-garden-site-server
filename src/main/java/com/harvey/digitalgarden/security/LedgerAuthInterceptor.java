package com.harvey.digitalgarden.security;

import com.harvey.digitalgarden.common.BusinessException;
import com.harvey.digitalgarden.entity.LedgerUser;
import com.harvey.digitalgarden.repository.LedgerUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class LedgerAuthInterceptor implements HandlerInterceptor {

    private final LedgerUserRepository ledgerUserRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = normalizePath(request);

        // permitAll invite — may reach interceptor without auth; skip all gates
        if ("/api/ledger/auth/invite".equals(path)) {
            return true;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LedgerPrincipal principal)) {
            return true;
        }

        LedgerUser user = ledgerUserRepository.findById(principal.uid())
                .orElseThrow(() -> BusinessException.forbidden("ACCOUNT_DISABLED"));

        if (!"active".equals(user.getStatus())) {
            throw BusinessException.forbidden("ACCOUNT_DISABLED");
        }

        if (!isOnboardingExempt(request, path) && !isOnboardingDone(user)) {
            throw BusinessException.forbidden("ONBOARDING_REQUIRED");
        }

        return true;
    }

    private static String normalizePath(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    private static boolean isOnboardingExempt(HttpServletRequest request, String path) {
        String method = request.getMethod();
        if ("GET".equalsIgnoreCase(method) && "/api/ledger/auth/me".equals(path)) {
            return true;
        }
        if ("POST".equalsIgnoreCase(method) && "/api/ledger/onboarding".equals(path)) {
            return true;
        }
        return false;
    }

    private static boolean isOnboardingDone(LedgerUser user) {
        String displayName = user.getDisplayName();
        BigDecimal defaultBudget = user.getDefaultBudgetAmount();
        return displayName != null
                && !displayName.isBlank()
                && defaultBudget != null
                && defaultBudget.compareTo(BigDecimal.ZERO) > 0;
    }
}
