package com.hairsalonproject2.board.dto.request;

import com.hairsalonproject2.common.constant.BoardType;
import lombok.Getter;
import lombok.Setter;

/**
 * BoardSearchRequest
 * 게시글 검색 요청 데이터
 */
@Getter
@Setter
public class BoardSearchRequest {

    /**
     * 게시판 종류
     * 예: NOTICE, QNA
     */
    private BoardType type;

    /**
     * 검색어
     */
    private String keyword;
}