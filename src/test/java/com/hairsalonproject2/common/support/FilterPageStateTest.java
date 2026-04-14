package com.hairsalonproject2.common.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FilterPageStateTest {

    @Test
    void noSubmittedSearchAndNoFilterUsesDefaultListing() {
        FilterPageState state = FilterPageState.of(false, false);

        assertThat(state.searched()).isTrue();
        assertThat(state.hasFilter()).isFalse();
        assertThat(state.filterRequired()).isFalse();
        assertThat(state.defaultListing()).isTrue();
    }

    @Test
    void submittedSearchWithoutFilterRequiresFilter() {
        FilterPageState state = FilterPageState.of(true, false);

        assertThat(state.searched()).isTrue();
        assertThat(state.hasFilter()).isFalse();
        assertThat(state.filterRequired()).isTrue();
        assertThat(state.defaultListing()).isFalse();
    }

    @Test
    void filterPresentSearchesResults() {
        FilterPageState state = FilterPageState.of(false, true);

        assertThat(state.searched()).isTrue();
        assertThat(state.hasFilter()).isTrue();
        assertThat(state.filterRequired()).isFalse();
        assertThat(state.defaultListing()).isFalse();
        assertThat(state.resultsAllowed()).isTrue();
    }

    @Test
    void requiredFilterPageDoesNotSearchInitialPage() {
        FilterPageState state = FilterPageState.requireFilter(false, false);

        assertThat(state.searched()).isFalse();
        assertThat(state.hasFilter()).isFalse();
        assertThat(state.filterRequired()).isFalse();
        assertThat(state.defaultListing()).isFalse();
        assertThat(state.resultsAllowed()).isFalse();
    }

    @Test
    void requiredFilterPageRequiresFilterAfterSubmit() {
        FilterPageState state = FilterPageState.requireFilter(true, false);

        assertThat(state.searched()).isTrue();
        assertThat(state.hasFilter()).isFalse();
        assertThat(state.filterRequired()).isTrue();
        assertThat(state.defaultListing()).isFalse();
        assertThat(state.resultsAllowed()).isFalse();
    }

    @Test
    void requiredFilterPageAllowsResultsWithFilter() {
        FilterPageState state = FilterPageState.requireFilter(true, true);

        assertThat(state.searched()).isTrue();
        assertThat(state.hasFilter()).isTrue();
        assertThat(state.filterRequired()).isFalse();
        assertThat(state.defaultListing()).isFalse();
        assertThat(state.resultsAllowed()).isTrue();
    }
}
