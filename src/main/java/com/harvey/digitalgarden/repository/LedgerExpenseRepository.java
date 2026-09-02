package com.harvey.digitalgarden.repository;

import com.harvey.digitalgarden.entity.LedgerExpense;
import com.harvey.digitalgarden.repository.projection.CategoryAmountSum;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerExpenseRepository extends JpaRepository<LedgerExpense, Long> {

    @Query("SELECT e FROM LedgerExpense e WHERE e.userId = :uid AND e.isDeleted = false "
            + "AND e.recordedAt >= :start AND e.recordedAt < :end ORDER BY e.recordedAt DESC")
    List<LedgerExpense> findByUserAndRecordedRange(
            @Param("uid") Long uid, @Param("start") Long start, @Param("end") Long end);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM LedgerExpense e WHERE e.budgetId = :bid AND e.isDeleted = false")
    BigDecimal sumAmountByBudgetId(@Param("bid") Long budgetId);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM LedgerExpense e WHERE e.userId = :uid AND e.isDeleted = false "
            + "AND e.recordedAt >= :start AND e.recordedAt < :end")
    BigDecimal sumByUserAndRecordedRange(
            @Param("uid") Long uid, @Param("start") Long start, @Param("end") Long end);

    @Query(
            "SELECT e.categoryId AS categoryId, SUM(e.amount) AS amount FROM LedgerExpense e "
                    + "WHERE e.userId = :uid AND e.isDeleted = false "
                    + "AND e.recordedAt >= :start AND e.recordedAt < :end "
                    + "GROUP BY e.categoryId")
    List<CategoryAmountSum> sumByCategoryAndRecordedRange(
            @Param("uid") Long uid, @Param("start") Long start, @Param("end") Long end);
}
