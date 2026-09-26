package com.hairsalonproject2.salonservice.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ServiceCompareSearchRequest {
 private String serviceName;
 private String region;
 @NotNull(message = "페이지는 1 이상의 정수로 입력해 주세요.")
 @Min(value = 1, message = "페이지는 1 이상으로 입력해 주세요.")
 private Integer page = 1;
}
