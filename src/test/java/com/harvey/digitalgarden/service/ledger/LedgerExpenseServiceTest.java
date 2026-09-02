package com.harvey.digitalgarden.service.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.harvey.digitalgarden.common.BusinessException;
import com.harvey.digitalgarden.common.ResultCode;
import com.harvey.digitalgarden.dto.ledger.LedgerExpenseRequest;
import com.harvey.digitalgarden.dto.ledger.LedgerExpenseVO;
import com.harvey.digitalgarden.entity.LedgerCategory;
import com.harvey.digitalgarden.entity.LedgerExpense;
import com.harvey.digitalgarden.ledger.LedgerTimeUtil;
import com.harvey.digitalgarden.repository.LedgerCategoryRepository;
import com.harvey.digitalgarden.repository.LedgerExpenseRepository;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LedgerExpenseServiceTest {

    @Mock LedgerExpenseRepository expenseRepository;
    @Mock LedgerCategoryRepository categoryRepository;
    @Mock LedgerBudgetService budgetService;

    private LedgerExpenseService service;

    @BeforeEach
    void setUp() {
        service = new LedgerExpenseService(expenseRepository, categoryRepository, budgetService);
    }

    @Test
    void createExpense_resolvesBudgetAndPersists() {
        long recordedAt = shanghai(2026, 9, 15, 12);
        when(categoryRepository.findById(5L)).thenReturn(Optional.of(category(5L, 1L)));
        when(budgetService.resolveBudget(1L, recordedAt)).thenReturn(20L);
        when(expenseRepository.save(any()))
                .thenAnswer(
                        inv -> {
                            LedgerExpense e = inv.getArgument(0);
                            e.setId(100L);
                            return e;
                        });

        LedgerExpenseVO vo = service.createExpense(1L, request(5L, "午餐", "88.50", recordedAt));

        assertEquals(100L, vo.getId());
        assertEquals(20L, vo.getBudgetId());
        assertEquals(5L, vo.getCategoryId());
        assertEquals("午餐", vo.getDescription());
        assertEquals(new BigDecimal("88.50"), vo.getAmount());
        assertEquals(recordedAt, vo.getRecordedAt());
        verify(budgetService).resolveBudget(1L, recordedAt);
    }

    @Test
    void createExpense_allowsFutureRecordedAt() {
        long future =
                ZonedDateTime.now(ZoneId.of("Asia/Shanghai")).plusMonths(2).toEpochSecond();
        when(categoryRepository.findById(5L)).thenReturn(Optional.of(category(5L, 1L)));
        when(budgetService.resolveBudget(1L, future)).thenReturn(30L);
        when(expenseRepository.save(any()))
                .thenAnswer(
                        inv -> {
                            LedgerExpense e = inv.getArgument(0);
                            e.setId(101L);
                            return e;
                        });

        LedgerExpenseVO vo = service.createExpense(1L, request(5L, "", "10", future));

        assertEquals(101L, vo.getId());
        assertEquals(future, vo.getRecordedAt());
        verify(budgetService).resolveBudget(1L, future);
    }

    @Test
    void createExpense_rejectsNonPositiveAmount() {
        BusinessException ex =
                assertThrows(
                        BusinessException.class,
                        () -> service.createExpense(1L, request(5L, "x", "0", shanghai(2026, 9, 1, 0))));
        assertEquals(ResultCode.BAD_REQUEST, ex.getCode());
        verify(budgetService, never()).resolveBudget(anyLong(), anyLong());
    }

    @Test
    void createExpense_rejectsForeignCategory() {
        when(categoryRepository.findById(5L)).thenReturn(Optional.of(category(5L, 99L)));

        BusinessException ex =
                assertThrows(
                        BusinessException.class,
                        () ->
                                service.createExpense(
                                        1L, request(5L, "x", "10", shanghai(2026, 9, 1, 0))));
        assertEquals(ResultCode.NOT_FOUND, ex.getCode());
    }

    @Test
    void updateExpense_sameMonth_keepsBudgetId() {
        long sept15 = shanghai(2026, 9, 15, 12);
        long sept20 = shanghai(2026, 9, 20, 9);
        LedgerExpense existing = expense(50L, 1L, 20L, 5L, "旧", new BigDecimal("50"), sept15);
        when(expenseRepository.findById(50L)).thenReturn(Optional.of(existing));
        when(categoryRepository.findById(5L)).thenReturn(Optional.of(category(5L, 1L)));
        when(expenseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LedgerExpenseVO vo = service.updateExpense(1L, 50L, request(5L, "新", "60", sept20));

        assertEquals(20L, vo.getBudgetId());
        assertEquals(sept20, vo.getRecordedAt());
        assertEquals(new BigDecimal("60"), vo.getAmount());
        verify(budgetService, never()).resolveBudget(anyLong(), anyLong());
    }

    @Test
    void updateExpense_crossMonth_shiftsSpentBetweenBudgets() {
        long septTs = shanghai(2026, 9, 10, 10);
        long octTs = shanghai(2026, 10, 5, 10);
        long septBudgetId = 201L;
        long octBudgetId = 202L;

        AtomicLong idSeq = new AtomicLong(1);
        List<LedgerExpense> store = new ArrayList<>();

        when(categoryRepository.findById(5L)).thenReturn(Optional.of(category(5L, 1L)));
        when(budgetService.resolveBudget(eq(1L), eq(septTs))).thenReturn(septBudgetId);
        when(budgetService.resolveBudget(eq(1L), eq(octTs))).thenReturn(octBudgetId);

        when(expenseRepository.save(any()))
                .thenAnswer(
                        inv -> {
                            LedgerExpense e = inv.getArgument(0);
                            if (e.getId() == null) {
                                e.setId(idSeq.getAndIncrement());
                            }
                            store.removeIf(x -> x.getId().equals(e.getId()));
                            store.add(copy(e));
                            return e;
                        });
        when(expenseRepository.findById(anyLong()))
                .thenAnswer(
                        inv -> {
                            Long id = inv.getArgument(0);
                            return store.stream()
                                    .filter(e -> e.getId().equals(id) && !e.isDeleted())
                                    .findFirst()
                                    .map(LedgerExpenseServiceTest::copy);
                        });
        when(expenseRepository.sumAmountByBudgetId(anyLong()))
                .thenAnswer(
                        inv -> {
                            Long bid = inv.getArgument(0);
                            return store.stream()
                                    .filter(e -> !e.isDeleted() && bid.equals(e.getBudgetId()))
                                    .map(LedgerExpense::getAmount)
                                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                        });

        LedgerExpenseVO created =
                service.createExpense(1L, request(5L, "九月消费", "100", septTs));
        assertEquals(septBudgetId, created.getBudgetId());
        assertEquals(new BigDecimal("100"), expenseRepository.sumAmountByBudgetId(septBudgetId));
        assertEquals(BigDecimal.ZERO, expenseRepository.sumAmountByBudgetId(octBudgetId));

        LedgerExpenseVO updated =
                service.updateExpense(1L, created.getId(), request(5L, "十月消费", "100", octTs));

        assertEquals(octBudgetId, updated.getBudgetId());
        assertEquals(octTs, updated.getRecordedAt());
        assertEquals(BigDecimal.ZERO, expenseRepository.sumAmountByBudgetId(septBudgetId));
        assertEquals(new BigDecimal("100"), expenseRepository.sumAmountByBudgetId(octBudgetId));
        verify(budgetService).resolveBudget(1L, octTs);
    }

    @Test
    void softDelete_marksDeleted() {
        LedgerExpense existing =
                expense(7L, 1L, 20L, 5L, "删我", new BigDecimal("12"), shanghai(2026, 9, 1, 0));
        when(expenseRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(expenseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.softDelete(1L, 7L);

        assertTrue(existing.isDeleted());
        verify(expenseRepository).save(existing);
    }

    @Test
    void listByMonth_usesRecordedAtRange() {
        long[] range = LedgerTimeUtil.monthRange("2026-09");
        LedgerExpense e1 =
                expense(1L, 1L, 20L, 5L, "a", new BigDecimal("10"), shanghai(2026, 9, 2, 0));
        when(expenseRepository.findByUserAndRecordedRange(1L, range[0], range[1]))
                .thenReturn(List.of(e1));

        List<LedgerExpenseVO> list = service.listByMonth(1L, "2026-09");

        assertEquals(1, list.size());
        assertEquals(1L, list.get(0).getId());
        verify(expenseRepository).findByUserAndRecordedRange(1L, range[0], range[1]);
    }

    @Test
    void listByMonth_rejectsBadMonth() {
        BusinessException ex =
                assertThrows(BusinessException.class, () -> service.listByMonth(1L, "2026/09"));
        assertEquals(ResultCode.BAD_REQUEST, ex.getCode());
    }

    private static LedgerExpenseRequest request(
            long categoryId, String description, String amount, long recordedAt) {
        LedgerExpenseRequest req = new LedgerExpenseRequest();
        req.setCategoryId(categoryId);
        req.setDescription(description);
        req.setAmount(new BigDecimal(amount));
        req.setRecordedAt(recordedAt);
        return req;
    }

    private static LedgerCategory category(long id, long userId) {
        LedgerCategory c = new LedgerCategory();
        c.setId(id);
        c.setUserId(userId);
        c.setName("餐饮美食");
        return c;
    }

    private static LedgerExpense expense(
            long id,
            long userId,
            long budgetId,
            long categoryId,
            String description,
            BigDecimal amount,
            long recordedAt) {
        LedgerExpense e = new LedgerExpense();
        e.setId(id);
        e.setUserId(userId);
        e.setBudgetId(budgetId);
        e.setCategoryId(categoryId);
        e.setDescription(description);
        e.setAmount(amount);
        e.setRecordedAt(recordedAt);
        return e;
    }

    private static LedgerExpense copy(LedgerExpense src) {
        LedgerExpense e = new LedgerExpense();
        e.setId(src.getId());
        e.setUserId(src.getUserId());
        e.setBudgetId(src.getBudgetId());
        e.setCategoryId(src.getCategoryId());
        e.setDescription(src.getDescription());
        e.setAmount(src.getAmount());
        e.setRecordedAt(src.getRecordedAt());
        e.setIsDeleted(src.isDeleted());
        return e;
    }

    private static long shanghai(int year, int month, int day, int hour) {
        return ZonedDateTime.of(year, month, day, hour, 0, 0, 0, ZoneId.of("Asia/Shanghai"))
                .toEpochSecond();
    }
}
