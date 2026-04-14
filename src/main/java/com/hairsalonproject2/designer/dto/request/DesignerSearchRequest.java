package com.hairsalonproject2.designer.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class DesignerSearchRequest {
    private String keyword;
    private String salonKeyword;
    private String city;
    private String district;
    private String neighborhood;
    private Integer minCareerYears;
    private BigDecimal minRating;
    private String sortBy;
    private boolean searched;

    public boolean hasSearchRequest() {
        return searched
                || (keyword != null && !keyword.isBlank())
                || (salonKeyword != null && !salonKeyword.isBlank())
                || (city != null && !city.isBlank())
                || (district != null && !district.isBlank())
                || (neighborhood != null && !neighborhood.isBlank())
                || minCareerYears != null
                || minRating != null
                || (sortBy != null && !sortBy.isBlank());
    }
}
