package com.harvey.digitalgarden.service.ledger;

import com.harvey.digitalgarden.common.BusinessException;
import com.harvey.digitalgarden.dto.ledger.LedgerCategoryRequest;
import com.harvey.digitalgarden.dto.ledger.LedgerCategoryVO;
import com.harvey.digitalgarden.entity.LedgerCategory;
import com.harvey.digitalgarden.repository.LedgerCategoryRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerCategoryService {

    private final LedgerCategoryRepository categoryRepository;

    public LedgerCategoryService(LedgerCategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public List<LedgerCategoryVO> listCategories(long userId) {
        List<LedgerCategory> categories =
                categoryRepository.findByUserIdAndIsDeletedFalseOrderBySortOrderAsc(userId);
        List<LedgerCategoryVO> result = new ArrayList<>(categories.size());
        for (LedgerCategory category : categories) {
            result.add(toVO(category));
        }
        return result;
    }

    @Transactional
    public LedgerCategoryVO createCategory(long userId, LedgerCategoryRequest request) {
        ValidatedFields fields = validateRequest(request);
        assertNameUnique(userId, fields.name(), null);

        int sortOrder = resolveSortOrder(userId, fields.sortOrder());
        LedgerCategory category = new LedgerCategory();
        category.setUserId(userId);
        category.setName(fields.name());
        category.setIcon(fields.icon());
        category.setSortOrder(sortOrder);
        categoryRepository.save(category);
        return toVO(category);
    }

    @Transactional
    public LedgerCategoryVO updateCategory(long userId, long categoryId, LedgerCategoryRequest request) {
        LedgerCategory category = findOwnedCategory(userId, categoryId);
        ValidatedFields fields = validateRequest(request);
        assertNameUnique(userId, fields.name(), categoryId);

        category.setName(fields.name());
        category.setIcon(fields.icon());
        if (fields.sortOrder() != null) {
            category.setSortOrder(fields.sortOrder());
        }
        categoryRepository.save(category);
        return toVO(category);
    }

    @Transactional
    public void softDelete(long userId, long categoryId) {
        LedgerCategory category = findOwnedCategory(userId, categoryId);
        category.setIsDeleted(true);
        categoryRepository.save(category);
    }

    private LedgerCategory findOwnedCategory(long userId, long categoryId) {
        LedgerCategory category =
                categoryRepository
                        .findById(categoryId)
                        .orElseThrow(() -> BusinessException.notFound("分类不存在"));
        if (!category.getUserId().equals(userId)) {
            throw BusinessException.notFound("分类不存在");
        }
        return category;
    }

    private void assertNameUnique(long userId, String name, Long excludeId) {
        boolean duplicate =
                excludeId == null
                        ? categoryRepository.existsByUserIdAndNameAndIsDeletedFalse(userId, name)
                        : categoryRepository.existsByUserIdAndNameAndIsDeletedFalseAndIdNot(
                                userId, name, excludeId);
        if (duplicate) {
            throw BusinessException.conflict("CATEGORY_NAME_DUPLICATE");
        }
    }

    private int resolveSortOrder(long userId, Integer requested) {
        if (requested != null) {
            return requested;
        }
        return categoryRepository.findByUserIdAndIsDeletedFalseOrderBySortOrderAsc(userId).stream()
                .map(LedgerCategory::getSortOrder)
                .max(Comparator.naturalOrder())
                .map(max -> max + 1)
                .orElse(0);
    }

    private static ValidatedFields validateRequest(LedgerCategoryRequest request) {
        if (request == null) {
            throw BusinessException.badRequest("请求体不能为空");
        }
        String name = request.getName();
        if (name == null || name.isBlank()) {
            throw BusinessException.badRequest("category.name 不能为空");
        }
        name = name.trim();
        if (name.length() > 64) {
            throw BusinessException.badRequest("category.name 长度不能超过 64");
        }

        String icon = request.getIcon();
        if (icon == null) {
            icon = "";
        }
        if (icon.length() > 32) {
            throw BusinessException.badRequest("category.icon 长度不能超过 32");
        }

        return new ValidatedFields(name, icon, request.getSortOrder());
    }

    private static LedgerCategoryVO toVO(LedgerCategory category) {
        LedgerCategoryVO vo = new LedgerCategoryVO();
        vo.setId(category.getId());
        vo.setName(category.getName());
        vo.setIcon(category.getIcon());
        vo.setSortOrder(category.getSortOrder());
        return vo;
    }

    private record ValidatedFields(String name, String icon, Integer sortOrder) {}
}
