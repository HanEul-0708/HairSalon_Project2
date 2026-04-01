package com.hairsalonproject2.service;

import com.hairsalonproject2.dto.request.DesignerSearchRequest;
import com.hairsalonproject2.entity.Designer;
import org.springframework.data.jpa.domain.Specification;

public final class DesignerSpecifications {
    private DesignerSpecifications() {}

    public static Specification<Designer> bySearch(DesignerSearchRequest request) {
        return Specification.where(keywordContains(request.getKeyword()))
                .and(salonEquals(request.getSalonId()))
                .and(minCareerYears(request.getMinCareerYears()));
    }

    private static Specification<Designer> keywordContains(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return (root, query, cb) -> {
            String pattern = "%" + keyword.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("introduction")), pattern),
                    cb.like(cb.lower(root.get("salon").get("name")), pattern)
            );
        };
    }

    private static Specification<Designer> salonEquals(Integer salonId) {
        if (salonId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("salon").get("salonId"), salonId);
    }

    private static Specification<Designer> minCareerYears(Integer minCareerYears) {
        if (minCareerYears == null) {
            return null;
        }
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("careerYears"), minCareerYears);
    }
}