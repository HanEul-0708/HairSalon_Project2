package com.hairsalonproject2.member.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/*
    로그인 요청 DTO
    실제 로그인 인증은 Spring Security가 처리하고,
    이 DTO는 로그인 폼 바인딩용으로 사용한다.
*/
@Getter
@Setter
public class MemberLoginRequest {

    @NotBlank(message = "아이디를 입력해 주세요.")
    private String memberId;

    @NotBlank(message = "비밀번호를 입력해 주세요.")
    private String password;
}
