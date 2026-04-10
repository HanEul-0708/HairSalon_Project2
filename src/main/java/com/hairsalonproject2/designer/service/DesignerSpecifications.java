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
                .and(salonKeywordContains(request.getSalonKeyword()))
                .and(cityContains(request.getCity()))
                .and(districtContains(request.getDistrict()))
                .and(neighborhoodContains(request.getNeighborhood()))
                .and(careerYearsAtLeast(request.getMinCareerYears()));
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

    private static Specification<Designer> salonKeywordContains(String salonKeyword) {
        if (salonKeyword == null || salonKeyword.isBlank()) {
            return null;
        }
        return (root, query, cb) ->
                cb.like(cb.lower(root.get("salon").get("name")), "%" + salonKeyword.toLowerCase() + "%");
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
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("salon").get("address")), city.toLowerCase() + "%"),
                cb.like(cb.lower(root.get("salon").get("roadAddress")), city.toLowerCase() + "%")
        );
    }

    private static Specification<Designer> districtContains(String district) {
        if (district == null || district.isBlank()) {
            return null;
        }
        String pattern = "% " + district.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("salon").get("address")), pattern),
                cb.like(cb.lower(root.get("salon").get("roadAddress")), pattern)
        );
    }

    private static Specification<Designer> neighborhoodContains(String neighborhood) {
        if (neighborhood == null || neighborhood.isBlank()) {
            return null;
        }
        String pattern = "% " + neighborhood.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("salon").get("address")), pattern),
                cb.like(cb.lower(root.get("salon").get("roadAddress")), pattern)
        );
    }
}
