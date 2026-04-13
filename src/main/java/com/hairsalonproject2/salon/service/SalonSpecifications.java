package com.hairsalonproject2.salon.service;

import com.hairsalonproject2.salon.dto.request.SalonSearchRequest;
import com.hairsalonproject2.salon.entity.Salon;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

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
                .and(cityContains(request.getCity()))
                .and(districtContains(request.getDistrict()))
                .and(neighborhoodContains(request.getNeighborhood()))
                .and(minRatingAtLeast(request.getMinRating()))
                .and(reservableEquals(request.getReservable()));
    }

    private static Specification<Salon> keywordContains(String keyword, List<Integer> keywordMatchedSalonIds) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        return (root, query, cb) -> {
            String pattern = containsPattern(keyword);
            Predicate keywordMatches = cb.or(
                    likeIgnoreCase(cb, root.get("name"), pattern),
                    likeIgnoreCase(cb, root.get("address"), pattern),
                    likeIgnoreCase(cb, root.get("roadAddress"), pattern),
                    likeIgnoreCase(cb, root.get("description"), pattern)
            );

            if (keywordMatchedSalonIds == null || keywordMatchedSalonIds.isEmpty()) {
                return keywordMatches;
            }

            return cb.or(keywordMatches, root.get("salonId").in(keywordMatchedSalonIds));
        };
    }

    private static Specification<Salon> regionContains(String region) {
        if (region == null || region.isBlank()) {
            return null;
        }
        String pattern = containsPattern(region);
        return (root, query, cb) -> addressLike(root, cb, pattern);
    }

    private static Specification<Salon> cityContains(String city) {
        if (city == null || city.isBlank()) {
            return null;
        }
        String pattern = startsWithPattern(city);
        return (root, query, cb) -> addressLike(root, cb, pattern);
    }

    private static Specification<Salon> districtContains(String district) {
        if (district == null || district.isBlank()) {
            return null;
        }
        String pattern = containsWordPattern(district);
        return (root, query, cb) -> addressLike(root, cb, pattern);
    }

    private static Specification<Salon> neighborhoodContains(String neighborhood) {
        if (neighborhood == null || neighborhood.isBlank()) {
            return null;
        }
        String pattern = containsWordPattern(neighborhood);
        return (root, query, cb) -> addressLike(root, cb, pattern);
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

    private static Predicate addressLike(Root<Salon> root, CriteriaBuilder cb, String pattern) {
        return cb.or(
                likeIgnoreCase(cb, root.get("address"), pattern),
                likeIgnoreCase(cb, root.get("roadAddress"), pattern)
        );
    }

    private static Predicate likeIgnoreCase(CriteriaBuilder cb, Path<String> path, String pattern) {
        return cb.like(lowerOrEmpty(cb, path), pattern);
    }

    private static Expression<String> lowerOrEmpty(CriteriaBuilder cb, Path<String> path) {
        return cb.lower(cb.coalesce(path, ""));
    }

    private static String containsPattern(String value) {
        return "%" + normalize(value) + "%";
    }

    private static String startsWithPattern(String value) {
        return normalize(value) + "%";
    }

    private static String containsWordPattern(String value) {
        return "% " + normalize(value) + "%";
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
