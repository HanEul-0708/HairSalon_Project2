package com.hairsalonproject2.common.constant;

import lombok.Getter;

/**
 * 회원 권한
 *
 * - USER: 일반 회원
 * - DESIGNER: 디자이너
 * - ADMIN: 관리자
 */
@Getter
public enum MemberRole {

    USER("일반회원"),
    DESIGNER("디자이너"),
    ADMIN("관리자");

    private final String description;

    MemberRole(String description) {
        this.description = description;
    }
}