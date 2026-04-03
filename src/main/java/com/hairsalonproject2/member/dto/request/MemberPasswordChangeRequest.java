package com.hairsalonproject2.member.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 비밀번호 변경 요청 DTO
 */
@Getter
@Setter
public class MemberPasswordChangeRequest {

    @NotBlank(message = "현재 비밀번호를 입력해주세요.")
    private String currentPassword;

    @NotBlank(message = "새 비밀번호를 입력해주세요.")
    @Size(min = 4, max = 100, message = "새 비밀번호는 4자 이상이어야 합니다.")
    private String newPassword;
}