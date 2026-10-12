package com.harvey.digitalgarden.ledger.chat;

import java.math.BigDecimal;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import com.harvey.digitalgarden.ledger.chat.ChatDtos.*;

public final class ChatDraftValidator {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private ChatDraftValidator() {}
    public static Issue amountIssue(String raw) {
        if (raw == null || raw.isBlank()) return new Issue("AMOUNT_REQUIRED", "请补充金额");
        try {
            if (!raw.matches("[0-9]+(?:\\.[0-9]{1,2})?")) throw new NumberFormatException();
            BigDecimal n = new BigDecimal(raw);
            if (n.signum() <= 0 || n.compareTo(new BigDecimal("999999.99")) > 0) throw new NumberFormatException();
            return null;
        } catch (NumberFormatException ex) { return new Issue("AMOUNT_INVALID", "金额须大于0、不超过999999.99，最多两位小数"); }
    }
    public static List<Issue> validate(Item item, List<Long> categoryIds) {
        List<Issue> issues = new ArrayList<>();
        Issue amount = amountIssue(item.getAmount());
        if (amount != null) issues.add(amount);
        if (item.getCategoryId() == null || !categoryIds.contains(item.getCategoryId()))
            issues.add(new Issue("CATEGORY_REQUIRED", "请选择已有分类"));
        if (item.getRecordedAt() == null || item.getRecordedAt() <= 0)
            issues.add(new Issue("DATE_CONFLICT", "请确认消费日期"));
        if (item.getDescription() == null || item.getDescription().length() > 500)
            issues.add(new Issue("DESCRIPTION_INVALID", "说明不能超过500字"));
        if (!item.getDuplicateExpenseIds().isEmpty() && !item.isDuplicateAcknowledged())
            issues.add(new Issue("DUPLICATE_REVIEW", "可能重复，请核对已有记录或选择仍然记一笔"));
        item.setFuture(item.getRecordedAt() != null && item.getRecordedAt() > Instant.now().getEpochSecond());
        item.setIssues(issues);
        return issues;
    }
    public static long dateAtNoon(LocalDate date) { return date.atTime(12, 0).atZone(ZONE).toEpochSecond(); }
}
