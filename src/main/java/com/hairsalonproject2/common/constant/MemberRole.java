package com.hairsalonproject2.common.constant;

/*
 * MemberRole
 *
 * member 테이블의 role 컬럼 ENUM과 매핑
 *
 * DB:
 * ENUM('USER', 'DESIGNER', 'ADMIN')
 */
public enum MemberRole {

    /*
     * 일반 사용자
     */
    USER,

    /*
     * 디자이너 계정
     */
    DESIGNER,

    /*
     * 관리자
     */
    ADMIN
}