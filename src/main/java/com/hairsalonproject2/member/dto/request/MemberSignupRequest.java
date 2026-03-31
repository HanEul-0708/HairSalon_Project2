package com.hairsalonproject2.member.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 회원가입 요청 DTO
 *
 * 팀 DTO 규칙:
 * - 회원가입 입력값 → MemberSignupRequest
 */
@Getter
@Setter
public class MemberSignupRequest {

    @NotBlank(message = "아이디는 필수입니다.")
    @Size(max = 30, message = "아이디는 30자 이하여야 합니다.")
    private String memberId;

    @NotBlank(message = "비밀번호는 필수입니다.")
    @Size(min = 4, max = 100, message = "비밀번호는 4자 이상이어야 합니다.")
    private String password;

    @NotBlank(message = "이름은 필수입니다.")
    @Size(max = 50, message = "이름은 50자 이하여야 합니다.")
    private String name;

    @Size(max = 20, message = "전화번호는 20자 이하여야 합니다.")
    private String phone;

    @Email(message = "이메일 형식이 아닙니다.")
    @Size(max = 100, message = "이메일은 100자 이하여야 합니다.")
    private String email;
}