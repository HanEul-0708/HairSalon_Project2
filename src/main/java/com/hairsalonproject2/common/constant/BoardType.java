package com.hairsalonproject2.common.constant;

/*
 * BoardType
 *
 * board 테이블 type 컬럼 ENUM과 매핑
 *
 * DB:
 * ENUM('NOTICE', 'QNA')
 */
public enum BoardType {

    /*
     * 공지사항
     */
    NOTICE,

    /*
     * 문의게시판
     */
    QNA
}