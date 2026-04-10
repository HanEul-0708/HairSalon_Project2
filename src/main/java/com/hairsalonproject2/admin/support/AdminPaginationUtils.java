package com.hairsalonproject2.admin.support;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.List;

public final class AdminPaginationUtils {

    private AdminPaginationUtils() {
    }

    public static int normalizePageNumber(int requestedPage) {
        return Math.max(requestedPage, 1);
    }

    public static <T> Page<T> slice(List<T> items, int requestedPage, int pageSize) {
        int safePage = normalizePageNumber(requestedPage);
        int safeSize = Math.max(pageSize, 1);
        int total = items == null ? 0 : items.size();

        if (total == 0) {
            return new PageImpl<>(List.of(), PageRequest.of(0, safeSize), 0);
        }

        int zeroBasedPage = safePage - 1;
        int fromIndex = Math.min(zeroBasedPage * safeSize, total);
        int toIndex = Math.min(fromIndex + safeSize, total);

        if (fromIndex >= total) {
            int lastPage = Math.max((total - 1) / safeSize, 0);
            fromIndex = lastPage * safeSize;
            toIndex = Math.min(fromIndex + safeSize, total);
            zeroBasedPage = lastPage;
        }

        return new PageImpl<>(
                items.subList(fromIndex, toIndex),
                PageRequest.of(zeroBasedPage, safeSize),
                total
        );
    }

    public static List<Integer> getPageNumbers(Page<?> page) {
        if (page == null || page.getTotalPages() <= 0) {
            return List.of();
        }

        int start = getPageGroupStart(page);
        int end = getPageGroupEnd(page);

        List<Integer> numbers = new ArrayList<>();
        for (int i = start; i <= end; i++) {
            numbers.add(i);
        }
        return numbers;
    }

    public static Integer getPreviousGroupPage(Page<?> page) {
        if (page == null || page.getTotalPages() <= 0) {
            return null;
        }

        int start = getPageGroupStart(page);
        return start > 1 ? start - 1 : null;
    }

    public static Integer getNextGroupPage(Page<?> page) {
        if (page == null || page.getTotalPages() <= 0) {
            return null;
        }

        int end = getPageGroupEnd(page);
        return end < page.getTotalPages() ? end + 1 : null;
    }

    private static int getPageGroupStart(Page<?> page) {
        int current = page.getNumber() + 1;
        return ((current - 1) / 5) * 5 + 1;
    }

    private static int getPageGroupEnd(Page<?> page) {
        int start = getPageGroupStart(page);
        return Math.min(page.getTotalPages(), start + 4);
    }
}
