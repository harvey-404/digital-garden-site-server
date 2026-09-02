package com.harvey.digitalgarden.service.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.harvey.digitalgarden.dto.ledger.CategoryBreakdownItem;
import com.harvey.digitalgarden.dto.ledger.LedgerDashboardVO;
import com.harvey.digitalgarden.entity.LedgerBudget;
import com.harvey.digitalgarden.entity.LedgerCategory;
import com.harvey.digitalgarden.entity.LedgerExpense;
import com.harvey.digitalgarden.entity.LedgerUser;
import com.harvey.digitalgarden.ledger.LedgerTimeUtil;
import com.harvey.digitalgarden.repository.LedgerBudgetRepository;
import com.harvey.digitalgarden.repository.LedgerCategoryRepository;
import com.harvey.digitalgarden.repository.LedgerExpenseRepository;
import com.harvey.digitalgarden.repository.LedgerUserRepository;
import com.harvey.digitalgarden.repository.projection.CategoryAmountSum;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LedgerDashboardServiceTest {

    @Mock LedgerUserRepository userRepository;
    @Mock LedgerBudgetRepository budgetRepository;
    @Mock LedgerExpenseRepository expenseRepository;
    @Mock LedgerCategoryRepository categoryRepository;

    private LedgerDashboardService service;

    @BeforeEach
    void setUp() {
        service =
                new LedgerDashboardService(
                        userRepository, budgetRepository, expenseRepository, categoryRepository);
    }

    @Test
    void getDashboard_spentEqualsSumOfExpensesInMonth() {
        long userId = 1L;
        String month = "2026-09";
        long[] range = LedgerTimeUtil.monthRange(month);
        long period = range[0];

        List<LedgerExpense> expenses =
                List.of(
                        expense(userId, 10L, 5L, "100", shanghai(2026, 9, 5, 10)),
                        expense(userId, 10L, 6L, "45.50", shanghai(2026, 9, 20, 18)),
                        expense(userId, 10L, 5L, "12", shanghai(2026, 9, 28, 8)));

        BigDecimal expectedSpent =
                expenses.stream().map(LedgerExpense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId, new BigDecimal("3000"))));
        when(budgetRepository.findByUserIdAndBudgetDtmAndIsDeletedFalse(userId, period))
                .thenReturn(Optional.of(budget(10L, userId, period, new BigDecimal("3000"))));
        when(expenseRepository.sumByUserAndRecordedRange(userId, range[0], range[1]))
                .thenReturn(expectedSpent);
        when(expenseRepository.sumAmountByBudgetId(10L)).thenReturn(expectedSpent);
        when(expenseRepository.findByUserAndRecordedRange(userId, range[0], range[1]))
                .thenReturn(expenses);
        when(expenseRepository.sumByCategoryAndRecordedRange(userId, range[0], range[1]))
                .thenReturn(
                        List.of(
                                categorySum(5L, new BigDecimal("112")),
                                categorySum(6L, new BigDecimal("45.50"))));
        when(categoryRepository.findByUserIdAndIsDeletedFalseOrderBySortOrderAsc(userId))
                .thenReturn(
                        List.of(category(5L, userId, "餐饮美食"), category(6L, userId, "交通出行")));

        LedgerDashboardVO dashboard = service.getDashboard(userId, month);

        assertEquals(new BigDecimal("157.50"), dashboard.getSpent());
        assertEquals(expectedSpent, dashboard.getSpent());

        BigDecimal listSum =
                expenseRepository.findByUserAndRecordedRange(userId, range[0], range[1]).stream()
                        .map(LedgerExpense::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(dashboard.getSpent(), listSum);
        assertEquals(expectedSpent, expenseRepository.sumAmountByBudgetId(10L));

        assertTrue(dashboard.isBudgetActivated());
        assertEquals(10L, dashboard.getBudgetId());
        assertEquals(new BigDecimal("3000"), dashboard.getBudgetAmount());
        assertEquals(new BigDecimal("2842.50"), dashboard.getRemaining());

        assertEquals(2, dashboard.getCategoryBreakdown().size());
        CategoryBreakdownItem top = dashboard.getCategoryBreakdown().get(0);
        assertEquals(5L, top.getCategoryId());
        assertEquals("餐饮美食", top.getCategoryName());
        assertEquals(new BigDecimal("112"), top.getAmount());
    }

    @Test
    void getDashboard_withoutBudget_usesDefaultBudgetReference() {
        long userId = 2L;
        String month = "2026-10";
        long[] range = LedgerTimeUtil.monthRange(month);
        long period = range[0];
        BigDecimal spent = new BigDecimal("80");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId, new BigDecimal("2500"))));
        when(budgetRepository.findByUserIdAndBudgetDtmAndIsDeletedFalse(userId, period))
                .thenReturn(Optional.empty());
        when(expenseRepository.sumByUserAndRecordedRange(userId, range[0], range[1])).thenReturn(spent);
        when(expenseRepository.sumByCategoryAndRecordedRange(userId, range[0], range[1]))
                .thenReturn(List.of());

        LedgerDashboardVO dashboard = service.getDashboard(userId, month);

        assertFalse(dashboard.isBudgetActivated());
        assertNull(dashboard.getBudgetId());
        assertNull(dashboard.getBudgetAmount());
        assertEquals(new BigDecimal("2500"), dashboard.getDefaultBudgetAmount());
        assertEquals(spent, dashboard.getSpent());
        assertEquals(new BigDecimal("2420"), dashboard.getRemaining());
    }

    private static LedgerUser user(long id, BigDecimal defaultBudget) {
        LedgerUser user = new LedgerUser();
        user.setId(id);
        user.setDefaultBudgetAmount(defaultBudget);
        return user;
    }

    private static LedgerBudget budget(long id, long userId, long budgetDtm, BigDecimal amount) {
        LedgerBudget budget = new LedgerBudget();
        budget.setId(id);
        budget.setUserId(userId);
        budget.setBudgetDtm(budgetDtm);
        budget.setAmount(amount);
        return budget;
    }

    private static LedgerCategory category(long id, long userId, String name) {
        LedgerCategory category = new LedgerCategory();
        category.setId(id);
        category.setUserId(userId);
        category.setName(name);
        return category;
    }

    private static LedgerExpense expense(
            long userId, long budgetId, long categoryId, String amount, long recordedAt) {
        LedgerExpense expense = new LedgerExpense();
        expense.setUserId(userId);
        expense.setBudgetId(budgetId);
        expense.setCategoryId(categoryId);
        expense.setAmount(new BigDecimal(amount));
        expense.setRecordedAt(recordedAt);
        return expense;
    }

    private static CategoryAmountSum categorySum(long categoryId, BigDecimal amount) {
        return new CategoryAmountSum() {
            @Override
            public Long getCategoryId() {
                return categoryId;
            }

            @Override
            public BigDecimal getAmount() {
                return amount;
            }
        };
    }

    private static long shanghai(int year, int month, int day, int hour) {
        return ZonedDateTime.of(year, month, day, hour, 0, 0, 0, ZoneId.of("Asia/Shanghai"))
                .toEpochSecond();
    }
}
