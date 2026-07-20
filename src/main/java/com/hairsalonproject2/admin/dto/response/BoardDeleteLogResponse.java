package com.hairsalonproject2.admin.dto.response;

import com.hairsalonproject2.admin.entity.BoardDeleteLog;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * BoardDeleteLogResponse
 * 관리자 삭제 기록 화면용 DTO
 */
@Getter
public class BoardDeleteLogResponse {
 private Long deleteLogId;
 private Integer boardId;
 private String boardType;
 private String boardTitle;
 private String writerId;
 private String deletedBy;
 private String deleteReason;
 private LocalDateTime createdAt;

 public static BoardDeleteLogResponse from(BoardDeleteLog log) {
  BoardDeleteLogResponse response = new BoardDeleteLogResponse();
  response.deleteLogId = log.getDeleteLogId();
  response.boardId = log.getBoardId();
  response.boardType = log.getBoardType().name();
  response.boardTitle = log.getBoardTitle();
  response.writerId = log.getWriterId();
  response.deletedBy = log.getDeletedBy();
  response.deleteReason = log.getDeleteReason();
  response.createdAt = log.getCreatedAt();
  return response;
 }
}
