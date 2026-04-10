package com.hairsalonproject2.salon.service;

import com.hairsalonproject2.salon.dto.request.SalonSearchRequest;
import com.hairsalonproject2.salon.entity.Salon;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;

public final class SalonSpecifications {
    private SalonSpecifications() {
    }

    public static Specification<Salon> bySearch(SalonSearchRequest request) {
        return Specification.<Salon>unrestricted()
                .and(keywordContains(request.getKeyword()))
                .and(regionContains(request.getRegion()))
                .and(cityContains(request.getCity()))
                .and(districtContains(request.getDistrict()))
                .and(neighborhoodContains(request.getNeighborhood()))
                .and(minRatingAtLeast(request.getMinRating()))
                .and(reservableEquals(request.getReservable()));
    }

    private static Specification<Salon> keywordContains(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return (root, query, cb) -> {
            String pattern = "%" + keyword.toLowerCase() + "%";
            return cb.or(cb.like(cb.lower(root.get("name")), pattern), cb.like(cb.lower(root.get("address")), pattern), cb.like(cb.lower(root.get("roadAddress")), pattern), cb.like(cb.lower(root.get("description")), pattern));
        };
    }

    private static Specification<Salon> regionContains(String region) {
        if (region == null || region.isBlank()) {
            return null;
        }
        return (root, query, cb) -> cb.or(cb.like(cb.lower(root.get("address")), "%" + region.toLowerCase() + "%"), cb.like(cb.lower(root.get("roadAddress")), "%" + region.toLowerCase() + "%"));
    }

    private static Specification<Salon> cityContains(String city) {
        if (city == null || city.isBlank()) {
            return null;
        }
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("address")), city.toLowerCase() + "%"),
                cb.like(cb.lower(root.get("roadAddress")), city.toLowerCase() + "%")
        );
    }

    private static Specification<Salon> districtContains(String district) {
        if (district == null || district.isBlank()) {
            return null;
        }
        String pattern = "% " + district.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("address")), pattern),
                cb.like(cb.lower(root.get("roadAddress")), pattern)
        );
    }

    private static Specification<Salon> neighborhoodContains(String neighborhood) {
        if (neighborhood == null || neighborhood.isBlank()) {
            return null;
        }
        String pattern = "% " + neighborhood.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("address")), pattern),
                cb.like(cb.lower(root.get("roadAddress")), pattern)
        );
    }

    private static Specification<Salon> minRatingAtLeast(BigDecimal minRating) {
        if (minRating == null) {
            return null;
        }
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("averageRating"), minRating);
    }

    private static Specification<Salon> reservableEquals(Boolean reservable) {
        if (reservable == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("reservable"), reservable);
    }
}
