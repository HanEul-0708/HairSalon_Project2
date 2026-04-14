package com.hairsalonproject2.common.constant;

import lombok.Getter;

/*
 * MemberRole
 *
 * member 테이블의 role 컬럼 ENUM과 매핑
 *
 * DB:
 * ENUM('USER', 'DESIGNER', 'ADMIN')
 */
@Getter
public enum MemberRole {

    USER("일반 회원"),
    DESIGNER("디자이너"),
    ADMIN("관리자");

    private final String description;

    MemberRole(String description) {
        this.description = description;
    }
}