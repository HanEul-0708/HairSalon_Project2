package com.hairsalonproject2.member.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DesignerSignupSalonOptionResponse {
    private Integer salonId;
    private String salonName;
    private String address;
}
