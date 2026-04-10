package com.hairsalonproject2.salonservice.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SalonServiceSearchRequest {
    private String keyword;
    private String salonKeyword;
    private String region;
    private String city;
    private String district;
    private String neighborhood;
    private Integer maxPrice;
    private Integer maxDuration;
    private String sortBy;
    private boolean searched;

    public boolean hasSearchRequest() {
        return searched;
    }
}
