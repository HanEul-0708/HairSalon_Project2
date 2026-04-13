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
}
