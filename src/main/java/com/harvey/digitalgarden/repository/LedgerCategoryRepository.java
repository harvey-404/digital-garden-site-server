package com.harvey.digitalgarden.repository;

import com.harvey.digitalgarden.entity.LedgerCategory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerCategoryRepository extends JpaRepository<LedgerCategory, Long> {

    List<LedgerCategory> findByUserIdAndIsDeletedFalseOrderBySortOrderAsc(Long userId);

    boolean existsByUserIdAndNameAndIsDeletedFalse(Long userId, String name);

    boolean existsByUserIdAndNameAndIsDeletedFalseAndIdNot(Long userId, String name, Long id);
}
