package com.hairsalonproject2.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DesignerUpdateRequest {
    private Integer salonId;

    private String memberId;
    private String name;
    private String profileImage;
    private String introduction;

    private Integer careerYears = 0;
}
