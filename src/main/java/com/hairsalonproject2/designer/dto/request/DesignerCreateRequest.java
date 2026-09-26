package com.hairsalonproject2.designer.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DesignerCreateRequest {
 @NotNull(message = "소속 미용실 ID를 입력해 주세요.")
 @Positive(message = "미용실 ID는 1 이상이어야 합니다.")
 private Integer salonId;

 @Size(max = 30, message = "회원 ID는 30자 이하로 입력해 주세요.")
 private String memberId;

 @NotBlank(message = "디자이너 이름을 입력해 주세요.")
 @Size(max = 50, message = "이름은 50자 이하로 입력해 주세요.")
 private String name;

 @Size(max = 255, message = "프로필 이미지 URL은 255자 이하로 입력해 주세요.")
 private String profileImage;
 private String introduction;

 @NotNull(message = "경력 연차를 입력해 주세요.")
 @Min(value = 0, message = "경력 연차는 0 이상이어야 합니다.")
 private Integer careerYears;
}
