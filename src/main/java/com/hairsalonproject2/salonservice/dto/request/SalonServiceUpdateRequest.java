package com.hairsalonproject2.salonservice.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SalonServiceUpdateRequest {
    private Integer salonId;

    private String name;

    private Integer price;

    private Integer duration;

    private String description;
}