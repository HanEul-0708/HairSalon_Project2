package com.hairsalonproject2.board.repository;

import com.hairsalonproject2.board.dto.response.BoardReplyResponse;
import com.hairsalonproject2.board.dto.response.BoardResponse;
import com.hairsalonproject2.board.entity.Board;
import com.hairsalonproject2.board.entity.BoardFile;
import com.hairsalonproject2.board.entity.BoardImage;
import com.hairsalonproject2.common.constant.BoardType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * BoardRepository
 * 게시글 조회 전용 저장소
 */
public interface BoardRepository extends JpaRepository<Board, Integer> {

    /**
     * 게시판 종류별 원글 목록 조회
     */
    List<Board> findByTypeAndParentIsNullOrderByBoardIdDesc(BoardType type);

    /**
     * 전체 원글 목록 조회
     */
    List<Board> findByParentIsNullOrderByBoardIdDesc();

    /**
     * 특정 시각 이후 작성된 원글 수 조회
     */
    long countByParentIsNullAndCreatedAtGreaterThanEqual(LocalDateTime createdAt);

    /**
     * 특정 부모 글의 답글 목록 조회
     *
     * 중요:
     * Board 엔티티에는 parentBoardId 필드가 없고
     * parent(Board) 연관관계만 있으므로
     * parent.boardId 기준으로 파생 메서드를 작성해야 한다.
     */
    List<Board> findByParent_BoardIdOrderByBoardIdAsc(Integer parentBoardId);

    @Query("""
            SELECT b
            FROM Board b
            JOIN FETCH b.member m
            LEFT JOIN FETCH b.parent p
            WHERE b.boardId = :boardId
            """)
    Optional<Board> findDetailByBoardId(@Param("boardId") Integer boardId);

    @Query("""
            SELECT bi
            FROM BoardImage bi
            WHERE bi.board.boardId = :boardId
            ORDER BY bi.imageId ASC
            """)
    List<BoardImage> findImagesByBoardId(@Param("boardId") Integer boardId);

    @Query("""
            SELECT bf
            FROM BoardFile bf
            WHERE bf.board.boardId = :boardId
            ORDER BY bf.fileId ASC
            """)
    List<BoardFile> findFilesByBoardId(@Param("boardId") Integer boardId);

    /**
     * 특정 회원이 작성한 게시글 목록 조회
     */
    List<Board> findByMemberMemberIdOrderByBoardIdDesc(String memberId);

    /**
     * 특정 게시판 종류의 원글 수 조회
     */
    long countByTypeAndParentIsNull(BoardType type);

    /**
     * 특정 게시판 종류의 노출 중인 원글 수 조회
     */
    long countByTypeAndParentIsNullAndHiddenFalse(BoardType type);

    /**
     * 숨김 처리된 원글 수 조회
     */
    long countByParentIsNullAndHiddenTrue();

    /**
     * 신고 수가 특정 값보다 큰 원글 수 조회
     */
    long countByParentIsNullAndReportCountGreaterThan(Integer reportCount);

    /**
     * 특정 부모 글의 답글 수 조회
     */
    long countByParent_BoardId(Integer parentBoardId);

    @Query(value = """
            SELECT new com.hairsalonproject2.board.dto.response.BoardResponse(
                b.boardId,
                b.type,
                b.title,
                m.memberId,
                b.viewCount,
                b.createdAt,
                false,
                COUNT(c),
                b.hidden,
                b.reportCount,
                CASE WHEN COUNT(c) > 0 THEN true ELSE false END
            )
            FROM Board b
            JOIN b.member m
            LEFT JOIN b.children c
            WHERE b.parent IS NULL
              AND b.hidden = false
              AND b.type = :type
              AND (
                    :keyword IS NULL
                    OR :keyword = ''
                    OR b.title LIKE %:keyword%
                    OR b.content LIKE %:keyword%
                  )
            GROUP BY b.boardId, b.type, b.title, m.memberId, b.viewCount, b.createdAt, b.hidden, b.reportCount
            ORDER BY b.boardId DESC
            """,
            countQuery = """
            SELECT COUNT(b)
            FROM Board b
            WHERE b.parent IS NULL
              AND b.hidden = false
              AND b.type = :type
              AND (
                    :keyword IS NULL
                    OR :keyword = ''
                    OR b.title LIKE %:keyword%
                    OR b.content LIKE %:keyword%
                  )
            """)
    Page<BoardResponse> searchPageByTypeAndKeyword(@Param("type") BoardType type,
                                                   @Param("keyword") String keyword,
                                                   Pageable pageable);

    @Query(value = """
            SELECT new com.hairsalonproject2.board.dto.response.BoardResponse(
                b.boardId,
                b.type,
                b.title,
                m.memberId,
                b.viewCount,
                b.createdAt,
                false,
                COUNT(c),
                b.hidden,
                b.reportCount,
                CASE WHEN COUNT(c) > 0 THEN true ELSE false END
            )
            FROM Board b
            JOIN b.member m
            LEFT JOIN b.children c
            WHERE b.parent IS NULL
              AND b.hidden = false
              AND b.type = :type
              AND (:canViewAll = true OR m.memberId = :viewerMemberId)
              AND (
                    :keyword IS NULL
                    OR :keyword = ''
                    OR b.title LIKE %:keyword%
                    OR b.content LIKE %:keyword%
                  )
            GROUP BY b.boardId, b.type, b.title, m.memberId, b.viewCount, b.createdAt, b.hidden, b.reportCount
            ORDER BY b.boardId DESC
            """,
            countQuery = """
            SELECT COUNT(b)
            FROM Board b
            JOIN b.member m
            WHERE b.parent IS NULL
              AND b.hidden = false
              AND b.type = :type
              AND (:canViewAll = true OR m.memberId = :viewerMemberId)
              AND (
                    :keyword IS NULL
                    OR :keyword = ''
                    OR b.title LIKE %:keyword%
                    OR b.content LIKE %:keyword%
                  )
            """)
    Page<BoardResponse> searchAccessiblePageByTypeAndKeyword(@Param("type") BoardType type,
                                                             @Param("keyword") String keyword,
                                                             @Param("viewerMemberId") String viewerMemberId,
                                                             @Param("canViewAll") boolean canViewAll,
                                                             Pageable pageable);

    @Query(value = """
            SELECT new com.hairsalonproject2.board.dto.response.BoardResponse(
                b.boardId,
                b.type,
                b.title,
                m.memberId,
                b.viewCount,
                b.createdAt,
                false,
                COUNT(c),
                b.hidden,
                b.reportCount,
                CASE WHEN COUNT(c) > 0 THEN true ELSE false END
            )
            FROM Board b
            JOIN b.member m
            LEFT JOIN b.children c
            WHERE b.parent IS NULL
              AND (
                    :type IS NULL
                    OR b.type = :type
                  )
              AND (
                    :keyword IS NULL
                    OR :keyword = ''
                    OR b.title LIKE %:keyword%
                    OR b.content LIKE %:keyword%
                    OR m.memberId LIKE %:keyword%
                  )
            GROUP BY b.boardId, b.type, b.title, m.memberId, b.viewCount, b.createdAt, b.hidden, b.reportCount
            ORDER BY b.boardId DESC
            """,
            countQuery = """
            SELECT COUNT(b)
            FROM Board b
            JOIN b.member m
            WHERE b.parent IS NULL
              AND (
                    :type IS NULL
                    OR b.type = :type
                  )
              AND (
                    :keyword IS NULL
                    OR :keyword = ''
                    OR b.title LIKE %:keyword%
                    OR b.content LIKE %:keyword%
                    OR m.memberId LIKE %:keyword%
                  )
            """)
    Page<BoardResponse> searchAdminPage(@Param("type") BoardType type,
                                        @Param("keyword") String keyword,
                                        Pageable pageable);

    @Query("""
            SELECT new com.hairsalonproject2.board.dto.response.BoardReplyResponse(
                b.boardId,
                p.boardId,
                b.type,
                b.title,
                b.content,
                m.memberId,
                b.createdAt,
                b.updatedAt
            )
            FROM Board b
            JOIN b.member m
            LEFT JOIN b.parent p
            WHERE p.boardId = :parentBoardId
              AND b.hidden = false
            ORDER BY b.boardId ASC
            """)
    List<BoardReplyResponse> findReplyResponsesByParentBoardId(@Param("parentBoardId") Integer parentBoardId);

}
