package com.hairsalonproject2.salonservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SalonServiceUpdateRequest {
 @NotNull(message = "살롱 ID를 입력해주세요.")
 @Positive(message = "살롱 ID는 1 이상이어야 합니다.")
 private Integer salonId;

 @NotBlank(message = "시술명을 입력해주세요.")
 @Size(max = 100, message = "시술명은 100자 이내로 입력해주세요.")
 private String name;

 @NotNull(message = "가격을 입력해주세요.")
 @PositiveOrZero(message = "가격은 0원 이상이어야 합니다.")
 private Integer price;

 @NotNull(message = "소요 시간을 입력해주세요.")
 @Positive(message = "소요 시간은 1분 이상이어야 합니다.")
 private Integer duration;

 private String description;
}
