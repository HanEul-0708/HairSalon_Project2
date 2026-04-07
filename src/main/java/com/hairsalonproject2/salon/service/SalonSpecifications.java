package com.hairsalonproject2.salon.service;

import com.hairsalonproject2.salon.dto.request.SalonSearchRequest;
import com.hairsalonproject2.salon.entity.Salon;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;

public final class SalonSpecifications {
    private SalonSpecifications() {
    }

    public static Specification<Salon> bySearch(SalonSearchRequest request) {
        return bySearch(request, List.of());
    }

    public static Specification<Salon> bySearch(SalonSearchRequest request, List<Integer> keywordMatchedSalonIds) {
        return Specification.<Salon>unrestricted()
                .and(keywordContains(request.getKeyword(), keywordMatchedSalonIds))
                .and(regionContains(request.getRegion()))
                .and(minRatingAtLeast(request.getMinRating()))
                .and(reservableEquals(request.getReservable()));
    }

    private static Specification<Salon> keywordContains(String keyword, List<Integer> keywordMatchedSalonIds) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return (root, query, cb) -> {
            String pattern = "%" + keyword.toLowerCase() + "%";
            if (keywordMatchedSalonIds == null || keywordMatchedSalonIds.isEmpty()) {
                return cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("address")), pattern),
                        cb.like(cb.lower(root.get("roadAddress")), pattern),
                        cb.like(cb.lower(root.get("description")), pattern)
                );
            }
            return cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("address")), pattern),
                    cb.like(cb.lower(root.get("roadAddress")), pattern),
                    cb.like(cb.lower(root.get("description")), pattern),
                    root.get("salonId").in(keywordMatchedSalonIds)
            );
        };
    }

    private static Specification<Salon> regionContains(String region) {
        if (region == null || region.isBlank()) {
            return null;
        }
        return (root, query, cb) -> cb.or(cb.like(cb.lower(root.get("address")), "%" + region.toLowerCase() + "%"), cb.like(cb.lower(root.get("roadAddress")), "%" + region.toLowerCase() + "%"));
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
