package com.hairsalonproject2.designer.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class DesignerSearchRequest {
    private String keyword;
    private Integer salonId;
    private BigDecimal minRating;
    private Integer minCareerYears;
}