package com.hairsalonproject2.common.support;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageUtilsTest {

    @Test
    void sliceZeroBasedReturnsRequestedPage() {
        Page<Integer> page = PageUtils.sliceZeroBased(List.of(1, 2, 3, 4, 5), 1, 2);

        assertThat(page.getContent()).containsExactly(3, 4);
        assertThat(page.getNumber()).isEqualTo(1);
        assertThat(page.getSize()).isEqualTo(2);
        assertThat(page.getTotalElements()).isEqualTo(5);
    }

    @Test
    void sliceZeroBasedClampsOutOfRangePageToLastPage() {
        Page<Integer> page = PageUtils.sliceZeroBased(List.of(1, 2, 3, 4, 5), 99, 2);

        assertThat(page.getContent()).containsExactly(5);
        assertThat(page.getNumber()).isEqualTo(2);
    }

    @Test
    void sliceZeroBasedHandlesEmptyItemsAndInvalidSize() {
        Page<Integer> page = PageUtils.sliceZeroBased(null, -1, 0);

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getNumber()).isZero();
        assertThat(page.getSize()).isEqualTo(1);
        assertThat(page.getTotalElements()).isZero();
    }

    @Test
    void sliceOneBasedConvertsRequestedPageToZeroBasedPage() {
        Page<Integer> page = PageUtils.sliceOneBased(List.of(1, 2, 3, 4, 5), 2, 2);

        assertThat(page.getContent()).containsExactly(3, 4);
        assertThat(PageUtils.currentPage(page)).isEqualTo(2);
    }

    @Test
    void pageNumbersReturnsOneBasedNumbers() {
        Page<Integer> page = PageUtils.sliceZeroBased(List.of(1, 2, 3, 4, 5), 0, 2);

        assertThat(PageUtils.pageNumbers(page)).containsExactly(1, 2, 3);
        assertThat(PageUtils.pageNumbers(PageUtils.empty(5))).isEmpty();
    }

    @Test
    void zeroBasedPageWindowReturnsPagesAroundCurrentPage() {
        Page<Integer> page = PageUtils.sliceZeroBased(List.of(1, 2, 3, 4, 5, 6, 7), 2, 1);

        assertThat(PageUtils.zeroBasedPageWindow(page, 2)).containsExactly(0, 1, 2, 3, 4);
    }

    @Test
    void zeroBasedPageWindowClampsToPageRange() {
        Page<Integer> page = PageUtils.sliceZeroBased(List.of(1, 2, 3), 0, 1);

        assertThat(PageUtils.zeroBasedPageWindow(page, 2)).containsExactly(0, 1, 2);
        assertThat(PageUtils.zeroBasedPageWindow(PageUtils.empty(5), 2)).isEmpty();
    }
}
