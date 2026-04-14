package com.hairsalonproject2.designer.service;

import com.hairsalonproject2.designer.constant.DesignerSpecialty;
import com.hairsalonproject2.designer.dto.request.DesignerSearchRequest;
import com.hairsalonproject2.designer.entity.Designer;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DesignerSpecifications {
    private DesignerSpecifications() {
    }

    public static Specification<Designer> bySearch(DesignerSearchRequest request) {
        return bySearch(request, List.of());
    }

    public static Specification<Designer> bySearch(DesignerSearchRequest request, List<Integer> keywordMatchedSalonIds) {
        return Specification.<Designer>unrestricted()
                .and(keywordContains(request.getKeyword(), keywordMatchedSalonIds))
                .and(salonKeywordContains(request.getSalonKeyword()))
                .and(cityContains(request.getCity()))
                .and(districtContains(request.getDistrict()))
                .and(neighborhoodContains(request.getNeighborhood()))
                .and(careerYearsAtLeast(request.getMinCareerYears()));
    }

    private static Specification<Designer> keywordContains(String keyword, List<Integer> keywordMatchedSalonIds) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return (root, query, cb) -> {
            String pattern = containsPattern(keyword);
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.or(
                    likeIgnoreCase(cb, root.get("name"), pattern),
                    likeIgnoreCase(cb, root.get("introduction"), pattern),
                    likeIgnoreCase(cb, root.get("salon").get("name"), pattern)
            ));

            matchingSpecialties(keyword).forEach(specialty ->
                    predicates.add(cb.equal(root.get("specialty"), specialty)));

            if (keywordMatchedSalonIds != null && !keywordMatchedSalonIds.isEmpty()) {
                predicates.add(root.get("salon").get("salonId").in(keywordMatchedSalonIds));
            }

            return cb.or(predicates.toArray(Predicate[]::new));
        };
    }

    private static Specification<Designer> salonKeywordContains(String salonKeyword) {
        if (salonKeyword == null || salonKeyword.isBlank()) {
            return null;
        }
        String pattern = containsPattern(salonKeyword);
        return (root, query, cb) -> likeIgnoreCase(cb, root.get("salon").get("name"), pattern);
    }

    private static Specification<Designer> careerYearsAtLeast(Integer minCareerYears) {
        if (minCareerYears == null) {
            return null;
        }
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("careerYears"), minCareerYears);
    }

    private static Specification<Designer> cityContains(String city) {
        if (city == null || city.isBlank()) {
            return null;
        }
        String pattern = startsWithPattern(city);
        return (root, query, cb) -> salonAddressLike(root, cb, pattern);
    }

    private static Specification<Designer> districtContains(String district) {
        if (district == null || district.isBlank()) {
            return null;
        }
        String pattern = containsWordPattern(district);
        return (root, query, cb) -> salonAddressLike(root, cb, pattern);
    }

    private static Specification<Designer> neighborhoodContains(String neighborhood) {
        if (neighborhood == null || neighborhood.isBlank()) {
            return null;
        }
        String pattern = containsWordPattern(neighborhood);
        return (root, query, cb) -> salonAddressLike(root, cb, pattern);
    }

    private static Predicate salonAddressLike(Root<Designer> root, CriteriaBuilder cb, String pattern) {
        return cb.or(
                likeIgnoreCase(cb, root.get("salon").get("address"), pattern),
                likeIgnoreCase(cb, root.get("salon").get("roadAddress"), pattern)
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

    private static List<DesignerSpecialty> matchingSpecialties(String keyword) {
        String normalizedKeyword = normalize(keyword);
        List<DesignerSpecialty> specialties = new ArrayList<>();

        for (DesignerSpecialty specialty : DesignerSpecialty.values()) {
            if (normalize(specialty.name()).contains(normalizedKeyword)
                    || normalize(specialty.getLabel()).contains(normalizedKeyword)) {
                specialties.add(specialty);
            }
        }

        return specialties;
    }
}
