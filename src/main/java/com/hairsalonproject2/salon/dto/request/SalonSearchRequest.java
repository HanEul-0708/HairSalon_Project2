package com.hairsalonproject2.salon.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class SalonSearchRequest {
    private String keyword;
    private String region;
    private BigDecimal minRating;
    private Boolean reservable;
    private String sort = "recommended";
    private boolean serviceKeywordSearchEnabled;
    private boolean searched;

    public boolean hasSearchRequest() {
        return searched;
    }
}
