package com.harvey.digitalgarden.service.ledger;

import com.harvey.digitalgarden.common.BusinessException;
import com.harvey.digitalgarden.dto.ledger.CategoryBreakdownItem;
import com.harvey.digitalgarden.dto.ledger.LedgerDashboardVO;
import com.harvey.digitalgarden.entity.LedgerBudget;
import com.harvey.digitalgarden.entity.LedgerCategory;
import com.harvey.digitalgarden.entity.LedgerUser;
import com.harvey.digitalgarden.ledger.LedgerTimeUtil;
import com.harvey.digitalgarden.repository.LedgerBudgetRepository;
import com.harvey.digitalgarden.repository.LedgerCategoryRepository;
import com.harvey.digitalgarden.repository.LedgerExpenseRepository;
import com.harvey.digitalgarden.repository.LedgerUserRepository;
import com.harvey.digitalgarden.repository.projection.CategoryAmountSum;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class LedgerDashboardService {

    private final LedgerUserRepository userRepository;
    private final LedgerBudgetRepository budgetRepository;
    private final LedgerExpenseRepository expenseRepository;
    private final LedgerCategoryRepository categoryRepository;

    public LedgerDashboardService(
            LedgerUserRepository userRepository,
            LedgerBudgetRepository budgetRepository,
            LedgerExpenseRepository expenseRepository,
            LedgerCategoryRepository categoryRepository) {
        this.userRepository = userRepository;
        this.budgetRepository = budgetRepository;
        this.expenseRepository = expenseRepository;
        this.categoryRepository = categoryRepository;
    }

    public LedgerDashboardVO getDashboard(long userId, String month) {
        long[] range = parseMonthRangeOrBadRequest(month);
        long period = range[0];

        LedgerUser user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> BusinessException.notFound("账本用户不存在"));

        Optional<LedgerBudget> budgetOpt =
                budgetRepository.findByUserIdAndBudgetDtmAndIsDeletedFalse(userId, period);

        BigDecimal spentByRange =
                nullToZero(expenseRepository.sumByUserAndRecordedRange(userId, range[0], range[1]));

        BigDecimal referenceAmount =
                budgetOpt.map(LedgerBudget::getAmount).orElse(user.getDefaultBudgetAmount());

        LedgerDashboardVO vo = new LedgerDashboardVO();
        vo.setMonth(month.trim());
        vo.setDefaultBudgetAmount(user.getDefaultBudgetAmount());
        vo.setSpent(spentByRange);
        vo.setRate(computeRate(spentByRange, referenceAmount));
        vo.setRemaining(referenceAmount.subtract(spentByRange));
        vo.setCategoryBreakdown(buildCategoryBreakdown(userId, range[0], range[1], spentByRange));

        if (budgetOpt.isPresent()) {
            LedgerBudget budget = budgetOpt.get();
            vo.setBudgetActivated(true);
            vo.setBudgetId(budget.getId());
            vo.setBudgetDtm(budget.getBudgetDtm());
            vo.setBudgetAmount(budget.getAmount());
        } else {
            vo.setBudgetActivated(false);
            vo.setBudgetId(null);
            vo.setBudgetDtm(null);
            vo.setBudgetAmount(null);
        }

        return vo;
    }

    private List<CategoryBreakdownItem> buildCategoryBreakdown(
            long userId, long start, long end, BigDecimal totalSpent) {
        List<CategoryAmountSum> sums =
                expenseRepository.sumByCategoryAndRecordedRange(userId, start, end);
        if (sums.isEmpty()) {
            return List.of();
        }

        Map<Long, String> categoryNames = loadCategoryNames(userId);
        List<CategoryBreakdownItem> items = new ArrayList<>(sums.size());
        for (CategoryAmountSum sum : sums) {
            BigDecimal amount = nullToZero(sum.getAmount());
            CategoryBreakdownItem item = new CategoryBreakdownItem();
            item.setCategoryId(sum.getCategoryId());
            item.setCategoryName(categoryNames.getOrDefault(sum.getCategoryId(), ""));
            item.setAmount(amount);
            item.setRate(computeRate(amount, totalSpent));
            items.add(item);
        }
        items.sort(
                Comparator.comparing(CategoryBreakdownItem::getAmount, Comparator.reverseOrder())
                        .thenComparing(CategoryBreakdownItem::getCategoryId));
        return items;
    }

    private Map<Long, String> loadCategoryNames(long userId) {
        List<LedgerCategory> categories =
                categoryRepository.findByUserIdAndIsDeletedFalseOrderBySortOrderAsc(userId);
        Map<Long, String> names = new HashMap<>(categories.size());
        for (LedgerCategory category : categories) {
            names.put(category.getId(), category.getName());
        }
        return names;
    }

    private static BigDecimal computeRate(BigDecimal spent, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return spent.divide(amount, 4, RoundingMode.HALF_UP);
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static long[] parseMonthRangeOrBadRequest(String month) {
        if (month == null || month.isBlank()) {
            throw BusinessException.badRequest("month 格式须为 YYYY-MM");
        }
        try {
            return LedgerTimeUtil.monthRange(month.trim());
        } catch (DateTimeParseException ex) {
            throw BusinessException.badRequest("month 格式须为 YYYY-MM");
        }
    }
}
