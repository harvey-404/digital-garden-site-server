package com.harvey.digitalgarden.repository;

import com.harvey.digitalgarden.entity.LedgerBudget;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerBudgetRepository extends JpaRepository<LedgerBudget, Long> {

    Optional<LedgerBudget> findByUserIdAndBudgetDtmAndIsDeletedFalse(Long userId, Long budgetDtm);

    List<LedgerBudget> findByUserIdAndIsDeletedFalseOrderByBudgetDtmDesc(Long userId);
}
