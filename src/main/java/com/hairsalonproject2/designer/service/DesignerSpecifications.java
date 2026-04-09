package com.hairsalonproject2.designer.service;

import com.hairsalonproject2.designer.dto.request.DesignerSearchRequest;
import com.hairsalonproject2.designer.entity.Designer;
import org.springframework.data.jpa.domain.Specification;

public final class DesignerSpecifications {
    private DesignerSpecifications() {
    }

    public static Specification<Designer> bySearch(DesignerSearchRequest request) {
        return Specification.<Designer>unrestricted()
                .and(keywordContains(request.getKeyword()))
                .and(salonNameContains(request.getSalonKeyword()))
                .and(minCareerYears(request.getMinCareerYears()));
    }

    private static Specification<Designer> keywordContains(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return (root, query, cb) -> {
            String pattern = "%" + keyword.toLowerCase() + "%";
            return cb.or(cb.like(cb.lower(root.get("name")), pattern), cb.like(cb.lower(root.get("introduction")), pattern), cb.like(cb.lower(root.get("salon").get("name")), pattern));
        };
    }

    private static Specification<Designer> salonNameContains(String salonKeyword) {
        if (salonKeyword == null || salonKeyword.isBlank()) {
            return null;
        }
        return (root, query, cb) -> cb.like(
                cb.lower(root.get("salon").get("name")),
                "%" + salonKeyword.toLowerCase() + "%"
        );
    }

    private static Specification<Designer> minCareerYears(Integer minCareerYears) {
        if (minCareerYears == null) {
            return null;
        }
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("careerYears"), minCareerYears);
    }
}
