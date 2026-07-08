package com.harvey.digitalgarden.common;

import lombok.Data;
import org.springframework.data.domain.Page;
import java.util.List;

@Data
public class PageResult<T> {
    private List<T> items;
    private long total;
    private int page;
    private int size;

    public static <E, T> PageResult<T> of(Page<E> page, List<T> items) {
        PageResult<T> pr = new PageResult<>();
        pr.items = items;
        pr.total = page.getTotalElements();
        pr.page = page.getNumber();
        pr.size = page.getSize();
        return pr;
    }
}
