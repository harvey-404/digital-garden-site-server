package com.harvey.digitalgarden.service.ledger;

import com.harvey.digitalgarden.common.BusinessException;
import com.harvey.digitalgarden.dto.ledger.LedgerExpenseRequest;
import com.harvey.digitalgarden.dto.ledger.LedgerExpenseVO;
import com.harvey.digitalgarden.entity.LedgerCategory;
import com.harvey.digitalgarden.entity.LedgerExpense;
import com.harvey.digitalgarden.ledger.LedgerTimeUtil;
import com.harvey.digitalgarden.repository.LedgerCategoryRepository;
import com.harvey.digitalgarden.repository.LedgerExpenseRepository;
import java.math.BigDecimal;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerExpenseService {

    private final LedgerExpenseRepository expenseRepository;
    private final LedgerCategoryRepository categoryRepository;
    private final LedgerBudgetService budgetService;

    public LedgerExpenseService(
            LedgerExpenseRepository expenseRepository,
            LedgerCategoryRepository categoryRepository,
            LedgerBudgetService budgetService) {
        this.expenseRepository = expenseRepository;
        this.categoryRepository = categoryRepository;
        this.budgetService = budgetService;
    }

    @Transactional
    public LedgerExpenseVO createExpense(long userId, LedgerExpenseRequest request) {
        ValidatedFields fields = validateRequest(userId, request);
        long budgetId = budgetService.resolveBudget(userId, fields.recordedAt());

        LedgerExpense expense = new LedgerExpense();
        expense.setUserId(userId);
        expense.setBudgetId(budgetId);
        expense.setCategoryId(fields.categoryId());
        expense.setDescription(fields.description());
        expense.setAmount(fields.amount());
        expense.setRecordedAt(fields.recordedAt());
        expenseRepository.save(expense);
        return toVO(expense);
    }

    @Transactional
    public LedgerExpenseVO updateExpense(long userId, long expenseId, LedgerExpenseRequest request) {
        LedgerExpense expense = findOwnedExpense(userId, expenseId);
        ValidatedFields fields = validateRequest(userId, request);

        long oldMonth = LedgerTimeUtil.monthStartShanghai(expense.getRecordedAt());
        long newMonth = LedgerTimeUtil.monthStartShanghai(fields.recordedAt());
        if (oldMonth != newMonth) {
            long budgetId = budgetService.resolveBudget(userId, fields.recordedAt());
            expense.setBudgetId(budgetId);
        }

        expense.setCategoryId(fields.categoryId());
        expense.setDescription(fields.description());
        expense.setAmount(fields.amount());
        expense.setRecordedAt(fields.recordedAt());
        expenseRepository.save(expense);
        return toVO(expense);
    }

    @Transactional
    public void softDelete(long userId, long expenseId) {
        LedgerExpense expense = findOwnedExpense(userId, expenseId);
        expense.setIsDeleted(true);
        expenseRepository.save(expense);
    }

    public List<LedgerExpenseVO> listByMonth(long userId, String month) {
        long[] range = parseMonthRangeOrBadRequest(month);
        List<LedgerExpense> expenses =
                expenseRepository.findByUserAndRecordedRange(userId, range[0], range[1]);
        List<LedgerExpenseVO> result = new ArrayList<>(expenses.size());
        for (LedgerExpense expense : expenses) {
            result.add(toVO(expense));
        }
        return result;
    }

    private LedgerExpense findOwnedExpense(long userId, long expenseId) {
        LedgerExpense expense =
                expenseRepository
                        .findById(expenseId)
                        .orElseThrow(() -> BusinessException.notFound("账单不存在"));
        if (!expense.getUserId().equals(userId)) {
            throw BusinessException.notFound("账单不存在");
        }
        return expense;
    }

    private ValidatedFields validateRequest(long userId, LedgerExpenseRequest request) {
        if (request == null) {
            throw BusinessException.badRequest("请求体不能为空");
        }

        BigDecimal amount = request.getAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw BusinessException.badRequest("expense.amount 必须大于 0");
        }

        Long recordedAt = request.getRecordedAt();
        if (recordedAt == null || recordedAt <= 0L) {
            throw BusinessException.badRequest("expense.recorded_at 必须大于 0");
        }

        Long categoryId = request.getCategoryId();
        if (categoryId == null || categoryId <= 0L) {
            throw BusinessException.badRequest("expense.category_id 不能为空");
        }
        LedgerCategory category =
                categoryRepository
                        .findById(categoryId)
                        .orElseThrow(() -> BusinessException.notFound("分类不存在"));
        if (!category.getUserId().equals(userId)) {
            throw BusinessException.notFound("分类不存在");
        }

        String description = request.getDescription();
        if (description == null) {
            description = "";
        }
        if (description.length() > 500) {
            throw BusinessException.badRequest("expense.description 长度不能超过 500");
        }

        return new ValidatedFields(categoryId, description, amount, recordedAt);
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

    private static LedgerExpenseVO toVO(LedgerExpense expense) {
        LedgerExpenseVO vo = new LedgerExpenseVO();
        vo.setId(expense.getId());
        vo.setBudgetId(expense.getBudgetId());
        vo.setCategoryId(expense.getCategoryId());
        vo.setDescription(expense.getDescription());
        vo.setAmount(expense.getAmount());
        vo.setRecordedAt(expense.getRecordedAt());
        return vo;
    }

    private record ValidatedFields(
            Long categoryId, String description, BigDecimal amount, Long recordedAt) {}
}
