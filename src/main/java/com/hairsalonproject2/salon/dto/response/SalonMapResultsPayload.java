package com.hairsalonproject2.salon.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class SalonMapResultsPayload {
    private List<SalonBranchMarkerResponse> results;
    private String debug;
    private String error;
}
