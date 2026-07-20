package com.hairsalonproject2.member.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 회원정보 수정 요청 DTO
 */
@Getter
@Setter
public class MemberUpdateRequest {

 @NotBlank(message = "이름은 필수입니다.")
 @Size(max = 50, message = "이름은 50자 이하여야 합니다.")
 private String name;

 @NotBlank(message = "전화번호는 필수입니다.")
 @Pattern(regexp = "^01[0-9]-?\\d{3,4}-?\\d{4}$", message = "전화번호 형식이 올바르지 않습니다.")
 @Size(max = 20, message = "전화번호는 20자 이하여야 합니다.")
 private String phone;

 @NotBlank(message = "이메일은 필수입니다.")
 @Email(message = "이메일 형식이 올바르지 않습니다.")
 @Size(max = 100, message = "이메일은 100자 이하여야 합니다.")
 private String email;
}
