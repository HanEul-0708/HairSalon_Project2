package com.hairsalonproject2.common.exception;

import lombok.Getter;

/**
 * 공통 에러 코드
 */
@Getter
public enum ErrorCode {

    MEMBER_NOT_FOUND("회원 정보를 찾을 수 없습니다."),
    DUPLICATE_MEMBER_ID("이미 사용 중인 아이디입니다."),
    DUPLICATE_MEMBER_EMAIL("이미 사용 중인 이메일입니다."),
    DUPLICATE_MEMBER_PHONE("이미 사용 중인 전화번호입니다."),
    INVALID_SIGNUP_ROLE("회원가입 유형이 올바르지 않습니다."),
    INVALID_MEMBER_NAME("이름은 필수입니다."),
    DESIGNER_SIGNUP_SALON_REQUIRED("디자이너 회원가입은 미용실 선택이 필요합니다."),
    DESIGNER_SIGNUP_SALON_NOT_FOUND("선택한 미용실 정보를 찾을 수 없습니다."),
    DESIGNER_SIGNUP_ACCOUNT_ALREADY_EXISTS("해당 미용실은 이미 디자이너 계정이 연결되어 있습니다."),
    DESIGNER_SIGNUP_TARGET_DESIGNER_NOT_FOUND("선택한 미용실에 연결할 디자이너 정보를 찾을 수 없습니다."),
    INVALID_PASSWORD("비밀번호가 일치하지 않습니다."),
    PASSWORD_CONFIRM_NOT_MATCH("비밀번호와 비밀번호 확인이 일치하지 않습니다."),
    CANNOT_DELETE_MYSELF("자기 자신의 계정은 삭제할 수 없습니다."),
    CANNOT_CHANGE_MY_ROLE("자기 자신의 권한은 변경할 수 없습니다."),
    CANNOT_CHANGE_MY_STATUS("자기 자신의 상태는 변경할 수 없습니다."),
    LAST_ADMIN_CANNOT_BE_DELETED("마지막 관리자 계정은 삭제할 수 없습니다."),
    LAST_ADMIN_ROLE_CANNOT_BE_CHANGED("마지막 관리자 계정의 권한은 변경할 수 없습니다."),
    CONNECTED_DESIGNER_ACCOUNT_ROLE_CHANGE_NOT_ALLOWED("디자이너와 연결된 계정은 DESIGNER 권한만 사용할 수 있습니다."),
    DELETED_MEMBER_MODIFICATION_NOT_ALLOWED("삭제된 회원 정보는 수정하거나 상태를 변경할 수 없습니다."),
    ACCESS_DENIED("접근 권한이 없습니다."),
    INTERNAL_SERVER_ERROR("서버 내부 오류가 발생했습니다.");

    private final String message;

    ErrorCode(String message) {
        this.message = message;
    }
}
