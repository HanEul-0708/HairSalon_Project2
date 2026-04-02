package com.hairsalonproject2.board.repository;

import com.hairsalonproject2.board.dto.response.BoardResponse;
import com.hairsalonproject2.board.entity.Board;
import com.hairsalonproject2.common.constant.BoardType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * BoardRepository
 * 게시글 조회 전용 저장소
 */
public interface BoardRepository extends JpaRepository<Board, Integer> {

    List<Board> findByTypeAndParentIsNullOrderByBoardIdDesc(BoardType type);

    List<Board> findByParentIsNullOrderByBoardIdDesc();

    long countByParentIsNullAndCreatedAtGreaterThanEqual(LocalDateTime createdAt);

    List<Board> findByParentBoardIdOrderByBoardIdAsc(Integer parentBoardId);

    List<Board> findByMemberMemberIdOrderByBoardIdDesc(String memberId);

    long countByTypeAndParentIsNull(BoardType type);

    long countByTypeAndParentIsNullAndHiddenFalse(BoardType type);

    long countByParentIsNullAndHiddenTrue();

    long countByParentIsNullAndReportCountGreaterThan(Integer reportCount);

    long countByParentBoardId(Integer parentBoardId);

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

    @Modifying
    @Query("""
            UPDATE Board b
            SET b.viewCount = b.viewCount + 1
            WHERE b.boardId = :boardId
            """)
    void increaseViewCount(@Param("boardId") Integer boardId);
}
