package com.hairsalonproject2.common.support;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.stream.IntStream;

public final class PageUtils {

    private PageUtils() {
    }

    public static <T> Page<T> sliceZeroBased(List<T> items, int requestedPage, int pageSize) {
        List<T> safeItems = items == null ? List.of() : items;
        int safeSize = Math.max(pageSize, 1);
        int total = safeItems.size();
        int maxPage = total == 0 ? 0 : (total - 1) / safeSize;
        int safePage = Math.min(Math.max(requestedPage, 0), maxPage);
        int fromIndex = Math.min(safePage * safeSize, total);
        int toIndex = Math.min(fromIndex + safeSize, total);

        return new PageImpl<>(
                safeItems.subList(fromIndex, toIndex),
                PageRequest.of(safePage, safeSize),
                total
        );
    }

    public static <T> Page<T> sliceOneBased(List<T> items, int requestedPage, int pageSize) {
        return sliceZeroBased(items, requestedPage - 1, pageSize);
    }

    public static <T> Page<T> empty(int pageSize) {
        return sliceZeroBased(List.of(), 0, pageSize);
    }

    public static int currentPage(Page<?> page) {
        return page == null ? 1 : page.getNumber() + 1;
    }

    public static List<Integer> pageNumbers(Page<?> page) {
        if (page == null || page.getTotalPages() <= 0) {
            return List.of();
        }
        return pageNumbers(page.getTotalPages());
    }

    public static List<Integer> pageNumbers(int totalPages) {
        if (totalPages <= 0) {
            return List.of();
        }
        return IntStream.rangeClosed(1, totalPages).boxed().toList();
    }

    public static List<Integer> zeroBasedPageWindow(Page<?> page, int radius) {
        if (page == null || page.getTotalPages() <= 0) {
            return List.of();
        }

        int safeRadius = Math.max(radius, 0);
        int current = page.getNumber();
        int start = Math.max(0, current - safeRadius);
        int end = Math.min(page.getTotalPages() - 1, current + safeRadius);
        return IntStream.rangeClosed(start, end).boxed().toList();
    }
}
