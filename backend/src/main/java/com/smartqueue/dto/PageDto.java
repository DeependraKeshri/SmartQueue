package com.smartqueue.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

public record PageDto<T>(List<T> items, int page, int size, long totalElements, int totalPages) {
    public static <E, T> PageDto<T> of(Page<E> p, Function<E, T> mapper) {
        return new PageDto<>(p.getContent().stream().map(mapper).toList(), p.getNumber(), p.getSize(),
                p.getTotalElements(), p.getTotalPages());
    }
}
