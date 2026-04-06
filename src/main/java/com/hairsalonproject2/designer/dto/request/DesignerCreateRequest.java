package com.hairsalonproject2.designer.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DesignerCreateRequest {
    private Integer salonId;

    private String memberId;

    private String name;

    private String profileImage;
    private String introduction;

    private Integer careerYears = 0;
}