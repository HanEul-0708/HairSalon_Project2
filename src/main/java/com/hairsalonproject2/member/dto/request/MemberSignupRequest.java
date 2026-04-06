package com.hairsalonproject2.member.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 회원가입 요청 DTO
 *
 * 역할
 * - 회원가입 화면에서 입력받은 값을 전달
 * - Bean Validation 으로 1차 검증 수행
 */
@Getter
@Setter
@NoArgsConstructor
public class MemberSignupRequest {

    /**
     * 회원 아이디
     *
     * 조건
     * - 필수
     * - 4자 이상 30자 이하
     * - 영문 소문자, 숫자만 허용
     */
    @NotBlank(message = "아이디는 필수입니다.")
    @Size(min = 4, max = 30, message = "아이디는 4자 이상 30자 이하로 입력해주세요.")
    @Pattern(regexp = "^[a-z0-9]+$", message = "아이디는 영문 소문자와 숫자만 사용할 수 있습니다.")
    private String memberId;

    /**
     * 비밀번호
     *
     * 조건
     * - 필수
     * - 8자 이상 100자 이하
     */
    @NotBlank(message = "비밀번호는 필수입니다.")
    @Size(min = 8, max = 100, message = "비밀번호는 8자 이상 입력해주세요.")
    private String password;

    /**
     * 비밀번호 확인
     *
     * 조건
     * - 필수
     * - 실제 일치 여부는 Service 에서 추가 검증
     */
    @NotBlank(message = "비밀번호 확인은 필수입니다.")
    private String passwordConfirm;

    /**
     * 이름
     */
    @NotBlank(message = "이름은 필수입니다.")
    @Size(max = 50, message = "이름은 50자 이하여야 합니다.")
    private String name;

    /**
     * 전화번호
     *
     * 예시
     * - 01012341234
     * - 010-1234-1234
     */
    @NotBlank(message = "전화번호는 필수입니다.")
    @Pattern(
            regexp = "^01[0-9]-?\\d{3,4}-?\\d{4}$",
            message = "전화번호 형식이 올바르지 않습니다."
    )
    @Size(max = 20, message = "전화번호는 20자 이하여야 합니다.")
    private String phone;

    /**
     * 이메일
     */
    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "이메일 형식이 아닙니다.")
    @Size(max = 100, message = "이메일은 100자 이하여야 합니다.")
    private String email;
}