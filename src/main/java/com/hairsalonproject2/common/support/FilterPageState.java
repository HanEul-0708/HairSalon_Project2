package com.hairsalonproject2.common.support;

public record FilterPageState(
        boolean searched,
        boolean hasFilter,
        boolean filterRequired,
        boolean defaultListing
) {

    public static FilterPageState of(boolean requestSearched, boolean hasFilter) {
        boolean filterRequired = requestSearched && !hasFilter;
        boolean defaultListing = !requestSearched && !hasFilter;
        boolean searched = requestSearched || hasFilter || defaultListing;
        return new FilterPageState(searched, hasFilter, filterRequired, defaultListing);
    }

    public static FilterPageState requireFilter(boolean requestSearched, boolean hasFilter) {
        boolean filterRequired = requestSearched && !hasFilter;
        return new FilterPageState(requestSearched, hasFilter, filterRequired, false);
    }

    public static FilterPageState requireFilter(boolean hasFilter) {
        return requireFilter(true, hasFilter);
    }

    public boolean resultsAllowed() {
        return searched && hasFilter && !filterRequired;
    }
}
