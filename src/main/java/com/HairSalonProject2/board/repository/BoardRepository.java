package com.HairSalonProject2.board.repository;

import com.HairSalonProject2.board.entity.Board;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * BoardRepository — 게시판 DB 접근 인터페이스
 * =====================================================
 * JpaRepository 를 상속하면 아래 기능이 공짜로 생김
 *
 * save(board)          → INSERT / UPDATE
 * findById(id)         → SELECT WHERE board_id = ?
 * findAll()            → SELECT * FROM board
 * delete(board)        → DELETE
 * count()              → SELECT COUNT(*)
 * existsById(id)       → 존재 여부 확인
 *
 * 추가로 필요한 것만 아래에 선언
 * 메서드 이름 규칙대로 쓰면 JPA 가 SQL 자동 생성해줌
 * 예) findByType → SELECT * FROM board WHERE type = ?
 */
public interface BoardRepository extends JpaRepository<Board, Long> {

    /**
     * 타입별 원글 목록 최신순 조회
     * 사용: 공지사항 목록, QnA 목록
     *
     * findBy       → WHERE 조건
     * Type         → type 컬럼
     * And          → AND 조건 추가
     * ParentIdIsNull → parent_id IS NULL (원글만)
     * OrderBy      → ORDER BY
     * BoardIdDesc  → board_id DESC (최신순)
     *
     * @param type 게시글 유형 ("NOTICE" 또는 "QNA")
     * @return 해당 타입의 원글 목록 최신순
     */
    List<Board> findByTypeAndParentIdIsNullOrderByBoardIdDesc(String type);

    /**
     * 특정 원글의 답글 목록 조회
     * 사용: 게시글 상세 페이지에서 답글 목록 표시
     *
     * @param parentId 원글의 boardId
     * @return 해당 원글에 달린 답글 목록
     */
    List<Board> findByParentIdOrderByBoardIdAsc(Long parentId);

    /**
     * 제목 + 내용 키워드 검색
     * 사용: 게시판 검색 기능
     *
     * @param type    게시글 유형
     * @param keyword 검색어
     * @return 검색 결과 목록
     */
    @Query("SELECT b FROM Board b " +
            "WHERE b.type = :type " +
            "AND b.parentId IS NULL " +
            "AND (b.title LIKE %:keyword% OR b.content LIKE %:keyword%) " +
            "ORDER BY b.boardId DESC")
    List<Board> searchByTypeAndKeyword(@Param("type") String type,
                                       @Param("keyword") String keyword);

    /**
     * 조회수 1 증가
     * 사용: 게시글 상세 페이지 열람 시
     *
     * @Modifying → UPDATE/DELETE 쿼리에 필수
     * @Query     → JPQL 직접 작성
     *
     * @param boardId 조회수 증가할 게시글 번호
     */
    @Modifying
    @Query("UPDATE Board b SET b.viewCount = b.viewCount + 1 " +
            "WHERE b.boardId = :boardId")
    void increaseViewCount(@Param("boardId") Long boardId);

    /**
     * 특정 회원이 작성한 게시글 목록
     * 사용: 마이페이지 내 게시글 목록
     *
     * @param memberId 회원 ID
     * @return 해당 회원의 게시글 목록 최신순
     */
    List<Board> findByMemberIdOrderByBoardIdDesc(String memberId);

    /**
     * 타입별 게시글 개수
     * 사용: 게시판 통계, 페이지네이션 계산
     *
     * @param type 게시글 유형
     * @return 해당 타입 게시글 수
     */
    long countByTypeAndParentIdIsNull(String type);

    /**
     * 특정 원글의 답글 개수 조회
     * 목록 화면에서 답글 개수 표시에 사용
     *
     * @param parentId 원글 boardId
     * @return 답글 개수
     */
    long countByParentId(Long parentId);
}