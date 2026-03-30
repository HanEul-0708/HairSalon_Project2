package com.HairSalonProject2.board.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * BoardSearchRequest — 게시글 검색 요청 DTO
 * =====================================================
 * 게시판 목록 페이지에서 검색 폼 입력값을 담는 그릇
 * GET 방식으로 URL 파라미터로 전달됨
 * 예) /boards/qna?keyword=예약&type=QNA
 *
 * 사용 위치
 * GET /boards/notices?keyword=...
 * GET /boards/qna?keyword=...
 */
@Getter
@NoArgsConstructor
public class BoardSearchRequest {

    /**
     * 게시글 유형
     * "NOTICE" 또는 "QNA"
     * URL 파라미터로 자동 바인딩됨
     */
    private String type;

    /**
     * 검색 키워드
     * 제목 + 내용에서 검색
     * 비어있으면 전체 목록 조회
     */
    private String keyword;

    /**
     * 검색어가 있는지 확인
     * Service 에서 전체 조회 vs 검색 분기에 사용
     *
     * @return true: 검색어 있음 / false: 없음
     */
    public boolean hasKeyword() {
        return keyword != null && !keyword.trim().isEmpty();
    }
}