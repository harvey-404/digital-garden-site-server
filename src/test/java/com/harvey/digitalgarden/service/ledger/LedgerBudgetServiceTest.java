package com.harvey.digitalgarden.service.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.harvey.digitalgarden.common.BusinessException;
import com.harvey.digitalgarden.common.ResultCode;
import com.harvey.digitalgarden.dto.ledger.LedgerBudgetVO;
import com.harvey.digitalgarden.entity.LedgerBudget;
import com.harvey.digitalgarden.entity.LedgerUser;
import com.harvey.digitalgarden.ledger.LedgerTimeUtil;
import com.harvey.digitalgarden.repository.LedgerBudgetRepository;
import com.harvey.digitalgarden.repository.LedgerExpenseRepository;
import com.harvey.digitalgarden.repository.LedgerUserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class LedgerBudgetServiceTest {

    @Mock LedgerUserRepository userRepository;
    @Mock LedgerBudgetRepository budgetRepository;
    @Mock LedgerExpenseRepository expenseRepository;

    private LedgerBudgetService service;

    @BeforeEach
    void setUp() {
        service = new LedgerBudgetService(userRepository, budgetRepository, expenseRepository);
    }

    @Test
    void resolveBudget_returnsExistingId() {
        long recordedAt =
                ZonedDateTime.of(2026, 9, 15, 12, 0, 0, 0, ZoneId.of("Asia/Shanghai"))
                        .toEpochSecond();
        long period = LedgerTimeUtil.monthStartShanghai(recordedAt);
        LedgerBudget existing = budget(10L, 1L, period, new BigDecimal("3000"));
        when(budgetRepository.findByUserIdAndBudgetDtmAndIsDeletedFalse(1L, period))
                .thenReturn(Optional.of(existing));

        long id = service.resolveBudget(1L, recordedAt);

        assertEquals(10L, id);
        verify(budgetRepository, never()).saveAndFlush(any());
    }

    @Test
    void resolveBudget_createsWithDefaultAmount() {
        long recordedAt =
                ZonedDateTime.of(2026, 9, 15, 12, 0, 0, 0, ZoneId.of("Asia/Shanghai"))
                        .toEpochSecond();
        long period = LedgerTimeUtil.monthStartShanghai(recordedAt);
        when(budgetRepository.findByUserIdAndBudgetDtmAndIsDeletedFalse(1L, period))
                .thenReturn(Optional.empty());

        LedgerUser user = user(1L, new BigDecimal("2500.00"));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(budgetRepository.saveAndFlush(any()))
                .thenAnswer(
                        inv -> {
                            LedgerBudget b = inv.getArgument(0);
                            b.setId(42L);
                            return b;
                        });

        long id = service.resolveBudget(1L, recordedAt);

        assertEquals(42L, id);
        ArgumentCaptor<LedgerBudget> captor = ArgumentCaptor.forClass(LedgerBudget.class);
        verify(budgetRepository).saveAndFlush(captor.capture());
        assertEquals(1L, captor.getValue().getUserId());
        assertEquals(period, captor.getValue().getBudgetDtm());
        assertEquals(new BigDecimal("2500.00"), captor.getValue().getAmount());
    }

    @Test
    void resolveBudget_duplicateKeyRetriesSelect() {
        long recordedAt =
                ZonedDateTime.of(2026, 10, 1, 8, 0, 0, 0, ZoneId.of("Asia/Shanghai"))
                        .toEpochSecond();
        long period = LedgerTimeUtil.monthStartShanghai(recordedAt);
        when(budgetRepository.findByUserIdAndBudgetDtmAndIsDeletedFalse(2L, period))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(budget(99L, 2L, period, new BigDecimal("1000"))));

        LedgerUser user = user(2L, new BigDecimal("1000"));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(budgetRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        long id = service.resolveBudget(2L, recordedAt);

        assertEquals(99L, id);
    }

    @Test
    void activateMonth_returnsExisting() {
        long period = LedgerTimeUtil.parseMonth("2026-09");
        LedgerBudget existing = budget(5L, 1L, period, new BigDecimal("3000"));
        when(budgetRepository.findByUserIdAndBudgetDtmAndIsDeletedFalse(1L, period))
                .thenReturn(Optional.of(existing));
        when(expenseRepository.sumAmountByBudgetId(5L)).thenReturn(new BigDecimal("120.50"));

        LedgerBudgetVO vo = service.activateMonth(1L, "2026-09");

        assertEquals(5L, vo.getId());
        assertEquals(period, vo.getBudgetDtm());
        assertEquals(new BigDecimal("120.50"), vo.getSpent());
        assertNull(vo.getSpentLabel());
        verify(budgetRepository, never()).saveAndFlush(any());
    }

    @Test
    void activateMonth_createsNew() {
        long period = LedgerTimeUtil.parseMonth("2026-09");
        when(budgetRepository.findByUserIdAndBudgetDtmAndIsDeletedFalse(1L, period))
                .thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, new BigDecimal("4000"))));
        when(budgetRepository.saveAndFlush(any()))
                .thenAnswer(
                        inv -> {
                            LedgerBudget b = inv.getArgument(0);
                            b.setId(7L);
                            return b;
                        });
        when(expenseRepository.sumAmountByBudgetId(7L)).thenReturn(BigDecimal.ZERO);

        LedgerBudgetVO vo = service.activateMonth(1L, "2026-09");

        assertEquals(7L, vo.getId());
        assertEquals(new BigDecimal("4000"), vo.getAmount());
        assertEquals(BigDecimal.ZERO, vo.getSpent());
        assertEquals("暂无消费", vo.getSpentLabel());
    }

    @Test
    void listBudgets_ordersDescAndLabelsZeroSpent() {
        long sept = LedgerTimeUtil.parseMonth("2026-09");
        long aug = LedgerTimeUtil.parseMonth("2026-08");
        LedgerBudget b1 = budget(1L, 1L, sept, new BigDecimal("3000"));
        LedgerBudget b2 = budget(2L, 1L, aug, new BigDecimal("2000"));
        when(budgetRepository.findByUserIdAndIsDeletedFalseOrderByBudgetDtmDesc(1L))
                .thenReturn(List.of(b1, b2));
        when(expenseRepository.sumAmountByBudgetId(1L)).thenReturn(BigDecimal.ZERO);
        when(expenseRepository.sumAmountByBudgetId(2L)).thenReturn(new BigDecimal("500"));

        List<LedgerBudgetVO> list = service.listBudgets(1L);

        assertEquals(2, list.size());
        assertEquals(sept, list.get(0).getBudgetDtm());
        assertEquals("暂无消费", list.get(0).getSpentLabel());
        assertEquals(BigDecimal.ZERO.setScale(4), list.get(0).getRate().setScale(4));
        assertEquals(aug, list.get(1).getBudgetDtm());
        assertNull(list.get(1).getSpentLabel());
        assertEquals(
                new BigDecimal("500").divide(new BigDecimal("2000"), 4, RoundingMode.HALF_UP),
                list.get(1).getRate().setScale(4, RoundingMode.HALF_UP));
    }

    @Test
    void updateAmount_checksOwnershipAndValidates() {
        LedgerBudget budget = budget(3L, 9L, LedgerTimeUtil.parseMonth("2026-09"), new BigDecimal("1000"));
        when(budgetRepository.findById(3L)).thenReturn(Optional.of(budget));

        BusinessException ex =
                assertThrows(
                        BusinessException.class,
                        () -> service.updateAmount(1L, 3L, new BigDecimal("2000")));
        assertEquals(ResultCode.NOT_FOUND, ex.getCode());
        verify(budgetRepository, never()).save(any());
    }

    @Test
    void updateAmount_updatesOwnedBudget() {
        LedgerBudget budget = budget(3L, 1L, LedgerTimeUtil.parseMonth("2026-09"), new BigDecimal("1000"));
        when(budgetRepository.findById(3L)).thenReturn(Optional.of(budget));
        when(budgetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(expenseRepository.sumAmountByBudgetId(3L)).thenReturn(new BigDecimal("100"));

        LedgerBudgetVO vo = service.updateAmount(1L, 3L, new BigDecimal("2500.50"));

        assertEquals(new BigDecimal("2500.50"), vo.getAmount());
        assertEquals(new BigDecimal("100"), vo.getSpent());
        verify(budgetRepository).save(budget);
    }

    @Test
    void updateAmount_rejectsInvalidAmount() {
        BusinessException ex =
                assertThrows(
                        BusinessException.class,
                        () -> service.updateAmount(1L, 3L, BigDecimal.ZERO));
        assertEquals(ResultCode.BAD_REQUEST, ex.getCode());
    }

    private static LedgerUser user(long id, BigDecimal defaultBudget) {
        LedgerUser user = new LedgerUser();
        user.setId(id);
        user.setDefaultBudgetAmount(defaultBudget);
        user.setStatus("active");
        return user;
    }

    private static LedgerBudget budget(long id, long userId, long budgetDtm, BigDecimal amount) {
        LedgerBudget b = new LedgerBudget();
        b.setId(id);
        b.setUserId(userId);
        b.setBudgetDtm(budgetDtm);
        b.setAmount(amount);
        return b;
    }
}
