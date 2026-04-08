package com.hairsalonproject2.member.dto.response;

public record AvailabilityResponse(boolean available) {

    public static AvailabilityResponse from(boolean available) {
        return new AvailabilityResponse(available);
    }
}
