package com.hairsalonproject2.board.repository;

import com.hairsalonproject2.board.entity.BoardReport;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * BoardReportRepository
 * 게시글 신고 이력 저장소
 */
public interface BoardReportRepository extends JpaRepository<BoardReport, Long> {

 /**
  * 같은 회원이 같은 게시글을 이미 신고했는지 확인
  */
 boolean existsByBoardBoardIdAndMemberMemberId(Integer boardId, String memberId);

 /**
  * 특정 게시글의 신고 이력을 모두 삭제
  */
 void deleteByBoardBoardId(Integer boardId);
}