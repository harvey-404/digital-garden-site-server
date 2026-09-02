package com.harvey.digitalgarden.service.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.harvey.digitalgarden.common.BusinessException;
import com.harvey.digitalgarden.common.ResultCode;
import com.harvey.digitalgarden.dto.ledger.LedgerAuthResponse;
import com.harvey.digitalgarden.dto.ledger.OnboardingRequest;
import com.harvey.digitalgarden.entity.LedgerUser;
import com.harvey.digitalgarden.repository.LedgerUserRepository;
import com.harvey.digitalgarden.security.JwtUtil;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LedgerAuthServiceTest {

    @Mock LedgerUserRepository userRepository;
    @Mock JwtUtil jwtUtil;

    private LedgerAuthService service;

    @BeforeEach
    void setUp() {
        service = new LedgerAuthService(userRepository, jwtUtil);
    }

    @Test
    void invite_normalizesCodeAndReturnsToken() {
        LedgerUser user = activeUser(1L, "L20260902-0001-ABCD1234", "ABCD-EFGH-JKLM");
        when(userRepository.findByInviteCode("ABCD-EFGH-JKLM")).thenReturn(Optional.of(user));
        when(jwtUtil.generateLedgerToken(1L, "L20260902-0001-ABCD1234")).thenReturn("ledger-jwt");

        LedgerAuthResponse resp = service.invite("abcd-efgh-jklm");

        assertEquals("ledger-jwt", resp.getToken());
        assertEquals("L20260902-0001-ABCD1234", resp.getUserSn());
        assertFalse(resp.isOnboardingDone());
    }

    @Test
    void invite_disabled_throws403() {
        LedgerUser user = activeUser(2L, "L20260902-0002-ABCD1234", "WXYZ-2345-6789");
        user.setStatus("disabled");
        when(userRepository.findByInviteCode("WXYZ-2345-6789")).thenReturn(Optional.of(user));

        BusinessException ex =
                assertThrows(BusinessException.class, () -> service.invite("wxyz-2345-6789"));

        assertEquals(ResultCode.FORBIDDEN, ex.getCode());
        assertEquals("ACCOUNT_DISABLED", ex.getMessage());
        verify(jwtUtil, never()).generateLedgerToken(anyLong(), anyString());
    }

    @Test
    void onboarding_duplicate_throws409() {
        LedgerUser user = activeUser(3L, "L20260902-0003-ABCD1234", "AAAA-BBBB-CCCC");
        user.setDisplayName("已完成");
        user.setDefaultBudgetAmount(new BigDecimal("3000"));
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));

        OnboardingRequest req = new OnboardingRequest();
        req.setDisplayName("再试一次");
        req.setDefaultBudgetAmount(new BigDecimal("1000"));

        BusinessException ex =
                assertThrows(BusinessException.class, () -> service.completeOnboarding(3L, req));

        assertEquals(ResultCode.CONFLICT, ex.getCode());
        assertEquals("ALREADY_ONBOARDED", ex.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void onboarding_setsNameAndBudget() {
        LedgerUser user = activeUser(4L, "L20260902-0004-ABCD1234", "DDDD-EEEE-FFFF");
        when(userRepository.findById(4L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OnboardingRequest req = new OnboardingRequest();
        req.setDisplayName("  小明  ");
        req.setDefaultBudgetAmount(new BigDecimal("2500.50"));

        var vo = service.completeOnboarding(4L, req);

        assertEquals("小明", vo.getDisplayName());
        assertEquals(new BigDecimal("2500.50"), vo.getDefaultBudgetAmount());
        assertTrue(vo.isOnboardingDone());
        verify(userRepository).save(user);
    }

    @Test
    void me_disabled_throws403() {
        LedgerUser user = activeUser(5L, "L20260902-0005-ABCD1234", "GGGG-HHHH-JJJJ");
        user.setStatus("disabled");
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.me(5L));
        assertEquals(ResultCode.FORBIDDEN, ex.getCode());
        assertEquals("ACCOUNT_DISABLED", ex.getMessage());
    }

    private static LedgerUser activeUser(long id, String userSn, String inviteCode) {
        LedgerUser user = new LedgerUser();
        user.setId(id);
        user.setUserSn(userSn);
        user.setInviteCode(inviteCode);
        user.setStatus("active");
        user.setDisplayName("");
        user.setDefaultBudgetAmount(BigDecimal.ZERO);
        return user;
    }
}
