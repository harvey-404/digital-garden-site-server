package com.harvey.digitalgarden.service.ledger;

import com.harvey.digitalgarden.common.BusinessException;
import com.harvey.digitalgarden.dto.ledger.LedgerBudgetVO;
import com.harvey.digitalgarden.entity.LedgerBudget;
import com.harvey.digitalgarden.entity.LedgerUser;
import com.harvey.digitalgarden.ledger.LedgerTimeUtil;
import com.harvey.digitalgarden.repository.LedgerBudgetRepository;
import com.harvey.digitalgarden.repository.LedgerExpenseRepository;
import com.harvey.digitalgarden.repository.LedgerUserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerBudgetService {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("999999.99");
    private static final String ZERO_SPENT_LABEL = "暂无消费";

    private final LedgerUserRepository userRepository;
    private final LedgerBudgetRepository budgetRepository;
    private final LedgerExpenseRepository expenseRepository;

    public LedgerBudgetService(
            LedgerUserRepository userRepository,
            LedgerBudgetRepository budgetRepository,
            LedgerExpenseRepository expenseRepository) {
        this.userRepository = userRepository;
        this.budgetRepository = budgetRepository;
        this.expenseRepository = expenseRepository;
    }

    @Transactional
    public long resolveBudget(long userId, long recordedAt) {
        long period = LedgerTimeUtil.monthStartShanghai(recordedAt);
        Optional<LedgerBudget> existing =
                budgetRepository.findByUserIdAndBudgetDtmAndIsDeletedFalse(userId, period);
        if (existing.isPresent()) {
            return existing.get().getId();
        }

        LedgerUser user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> BusinessException.notFound("账本用户不存在"));

        LedgerBudget budget = new LedgerBudget();
        budget.setUserId(userId);
        budget.setBudgetDtm(period);
        budget.setAmount(user.getDefaultBudgetAmount());

        try {
            budgetRepository.saveAndFlush(budget);
            return budget.getId();
        } catch (DataIntegrityViolationException ex) {
            return budgetRepository
                    .findByUserIdAndBudgetDtmAndIsDeletedFalse(userId, period)
                    .orElseThrow(() -> ex)
                    .getId();
        }
    }

    @Transactional
    public LedgerBudgetVO activateMonth(long userId, String month) {
        long period = parseMonthOrBadRequest(month);
        Optional<LedgerBudget> existing =
                budgetRepository.findByUserIdAndBudgetDtmAndIsDeletedFalse(userId, period);
        if (existing.isPresent()) {
            return toVO(existing.get());
        }

        LedgerUser user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> BusinessException.notFound("账本用户不存在"));

        LedgerBudget budget = new LedgerBudget();
        budget.setUserId(userId);
        budget.setBudgetDtm(period);
        budget.setAmount(user.getDefaultBudgetAmount());

        try {
            budgetRepository.saveAndFlush(budget);
        } catch (DataIntegrityViolationException ex) {
            budget =
                    budgetRepository
                            .findByUserIdAndBudgetDtmAndIsDeletedFalse(userId, period)
                            .orElseThrow(() -> ex);
        }

        return toVO(budget);
    }

    public List<LedgerBudgetVO> listBudgets(long userId) {
        List<LedgerBudget> budgets =
                budgetRepository.findByUserIdAndIsDeletedFalseOrderByBudgetDtmDesc(userId);
        List<LedgerBudgetVO> result = new ArrayList<>(budgets.size());
        for (LedgerBudget budget : budgets) {
            result.add(toVO(budget));
        }
        return result;
    }

    @Transactional
    public LedgerBudgetVO updateAmount(long userId, long budgetId, BigDecimal amount) {
        BigDecimal validated = validateAmount(amount);
        LedgerBudget budget =
                budgetRepository
                        .findById(budgetId)
                        .orElseThrow(() -> BusinessException.notFound("预算不存在"));
        if (!budget.getUserId().equals(userId)) {
            throw BusinessException.notFound("预算不存在");
        }
        budget.setAmount(validated);
        budgetRepository.save(budget);
        return toVO(budget);
    }

    private LedgerBudgetVO toVO(LedgerBudget budget) {
        BigDecimal spent = expenseRepository.sumAmountByBudgetId(budget.getId());
        if (spent == null) {
            spent = BigDecimal.ZERO;
        }

        LedgerBudgetVO vo = new LedgerBudgetVO();
        vo.setId(budget.getId());
        vo.setBudgetDtm(budget.getBudgetDtm());
        vo.setAmount(budget.getAmount());
        vo.setSpent(spent);
        vo.setRate(computeRate(spent, budget.getAmount()));
        if (spent.compareTo(BigDecimal.ZERO) > 0) {
            vo.setSpentLabel(null);
        } else {
            vo.setSpentLabel(ZERO_SPENT_LABEL);
        }
        return vo;
    }

    private static BigDecimal computeRate(BigDecimal spent, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return spent.divide(amount, 4, RoundingMode.HALF_UP);
    }

    private static BigDecimal validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw BusinessException.badRequest("budget.amount 必须大于 0");
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw BusinessException.badRequest("budget.amount 不能超过 999999.99");
        }
        return amount;
    }

    private static long parseMonthOrBadRequest(String month) {
        if (month == null || month.isBlank()) {
            throw BusinessException.badRequest("month 格式须为 YYYY-MM");
        }
        try {
            return LedgerTimeUtil.parseMonth(month.trim());
        } catch (DateTimeParseException ex) {
            throw BusinessException.badRequest("month 格式须为 YYYY-MM");
        }
    }
}
