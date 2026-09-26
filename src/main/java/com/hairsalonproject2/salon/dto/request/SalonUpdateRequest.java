package com.hairsalonproject2.salon.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SalonUpdateRequest {
 @NotBlank(message = "살롱명을 입력해 주세요.")
 @Size(max = 100, message = "살롱명은 100자 이하로 입력해 주세요.")
 private String name;
 @NotBlank(message = "주소를 입력해 주세요.")
 @Size(max = 255, message = "주소는 255자 이하로 입력해 주세요.")
 private String address;
 @Size(max = 255, message = "도로명 주소는 255자 이하로 입력해 주세요.")
 private String roadAddress;
 @Size(max = 20, message = "전화번호는 20자 이하로 입력해 주세요.")
 private String phone;
 private String description;
 @Size(max = 255, message = "이미지 URL은 255자 이하로 입력해 주세요.")
 private String imageUrl;
 @Size(max = 255, message = "외부 상세 URL은 255자 이하로 입력해 주세요.")
 private String placeUrl;
 @NotNull(message = "예약 가능 여부를 선택해 주세요.")
 private Boolean reservable = true;
}
