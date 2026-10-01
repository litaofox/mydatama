package com.mydatama.common.api;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.function.Function;

/**
 * 分页响应 {total, list}。
 */
@Data
@AllArgsConstructor
public class PageData<T> {

    private long total;
    private List<T> list;

    public static <E, T> PageData<T> of(IPage<E> page, Function<E, T> mapper) {
        List<T> list = page.getRecords().stream().map(mapper).toList();
        return new PageData<>(page.getTotal(), list);
    }
}
