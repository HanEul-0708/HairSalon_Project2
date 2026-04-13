package com.hairsalonproject2.admin.support;

import com.hairsalonproject2.common.support.PageUtils;
import org.springframework.data.domain.Page;

import java.util.ArrayList;
import java.util.List;

public final class AdminPaginationUtils {

    private AdminPaginationUtils() {
    }

    public static int normalizePageNumber(int requestedPage) {
        return Math.max(requestedPage, 1);
    }

    public static <T> Page<T> slice(List<T> items, int requestedPage, int pageSize) {
        return PageUtils.sliceOneBased(items, requestedPage, pageSize);
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
