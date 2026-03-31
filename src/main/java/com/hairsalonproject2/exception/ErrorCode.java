package com.hairsalonproject2.exception;

import lombok.Getter;

/**
 * 공통 에러 코드
 */
@Getter
public enum ErrorCode {

    MEMBER_NOT_FOUND("회원 정보를 찾을 수 없습니다."),
    DUPLICATE_MEMBER_ID("이미 사용 중인 아이디입니다."),
    DUPLICATE_MEMBER_EMAIL("이미 사용 중인 이메일입니다."),
    INVALID_PASSWORD("비밀번호가 일치하지 않습니다."),
    ACCESS_DENIED("접근 권한이 없습니다."),
    INTERNAL_SERVER_ERROR("서버 내부 오류가 발생했습니다.");

    private final String message;

    ErrorCode(String message) {
        this.message = message;
    }

}