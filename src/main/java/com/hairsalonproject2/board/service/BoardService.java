package com.hairsalonproject2.board.service;

import com.hairsalonproject2.admin.dto.response.BoardDeleteLogResponse;
import com.hairsalonproject2.board.dto.request.BoardCreateRequest;
import com.hairsalonproject2.board.dto.request.BoardReplyRequest;
import com.hairsalonproject2.board.dto.request.BoardSearchRequest;
import com.hairsalonproject2.board.dto.request.BoardUpdateRequest;
import com.hairsalonproject2.board.dto.response.BoardDetailResponse;
import com.hairsalonproject2.board.dto.response.BoardResponse;
import com.hairsalonproject2.board.dto.response.BoardSummaryResponse;
import com.hairsalonproject2.common.constant.BoardType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * BoardService
 * <p>
 * 컨트롤러 호환용 파사드 서비스
 * <p>
 * 설명
 * - 기존 컨트롤러가 주입받는 BoardService 이름은 유지
 * - 실제 구현은 세부 서비스로 위임
 */
@Service
@RequiredArgsConstructor
@Transactional
public class BoardService {

 private final BoardQueryService boardQueryService;
 private final BoardCommandService boardCommandService;
 private final BoardAdminService boardAdminService;

 @Transactional(readOnly = true)
 public Page<BoardResponse> getNoticePage(int page, int size, String keyword) {
  return boardQueryService.getNoticePage(page, size, keyword);
 }

 @Transactional(readOnly = true)
 public Page<BoardResponse> getQnaPage(int page, int size, String keyword) {
  return boardQueryService.getQnaPage(page, size, keyword);
 }

 @Transactional(readOnly = true)
 public Page<BoardResponse> getBoardPage(BoardType type, String keyword, int page, int size) {
  return boardQueryService.getBoardPage(type, keyword, page, size);
 }

 @Transactional(readOnly = true)
 public Page<BoardResponse> search(BoardSearchRequest request, int page, int size) {
  return boardQueryService.search(request, page, size);
 }

 @Transactional(readOnly = true)
 public List<BoardSummaryResponse> getRecentNotices() {
  return boardQueryService.getRecentNotices();
 }

 @Transactional(readOnly = true)
 public Page<BoardResponse> getAdminBoardPage(BoardType type, String keyword, int page, int size) {
  return boardQueryService.getAdminBoardPage(type, keyword, page, size);
 }

 @Transactional(readOnly = true)
 public long countOriginalBoards(BoardType type) {
  return boardQueryService.countOriginalBoards(type);
 }

 @Transactional(readOnly = true)
 public long countTodayBoards() {
  return boardQueryService.countTodayBoards();
 }

 @Transactional(readOnly = true)
 public long countHiddenBoards() {
  return boardQueryService.countHiddenBoards();
 }

 @Transactional(readOnly = true)
 public long countReportedBoards() {
  return boardQueryService.countReportedBoards();
 }

 @Transactional(readOnly = true)
 public List<BoardDeleteLogResponse> getRecentDeleteLogs() {
  return boardQueryService.getRecentDeleteLogs();
 }

 @Transactional(readOnly = true)
 public BoardDetailResponse getDetailOnly(Integer boardId) {
  return boardQueryService.getDetailOnly(boardId);
 }

 @Transactional(readOnly = true)
 public BoardDetailResponse getPublicDetail(Integer boardId, BoardType type) {
  return boardQueryService.getPublicDetail(boardId, type);
 }

 public BoardDetailResponse getDetailAndIncreaseView(Integer boardId) {
  return boardQueryService.getDetailAndIncreaseView(boardId);
 }

 public BoardDetailResponse getPublicDetailAndIncreaseView(Integer boardId, BoardType type) {
  return boardQueryService.getPublicDetailAndIncreaseView(boardId, type);
 }

 public void increaseViewCount(Integer boardId) {
  boardQueryService.increaseViewCount(boardId);
 }

 @Transactional(readOnly = true)
 public BoardType getBoardType(Integer boardId) {
  return boardQueryService.getBoardType(boardId);
 }

 public Integer create(BoardCreateRequest request, String memberId, List<MultipartFile> files, BoardType boardType) {
  return boardCommandService.create(request, memberId, files, boardType);
 }

 public Integer createReply(BoardReplyRequest request, String memberId) {
  return boardCommandService.createReply(request, memberId);
 }

 public void update(Integer boardId, BoardUpdateRequest request, String memberId, List<MultipartFile> files, boolean isAdmin) {
  boardCommandService.update(boardId, request, memberId, files, isAdmin);
 }

 public boolean reportBoard(Integer boardId, String reporterId) {
  return boardAdminService.reportBoard(boardId, reporterId);
 }

 public void hideBoard(Integer boardId, String reason) {
  boardAdminService.hideBoard(boardId, reason);
 }

 public void unhideBoard(Integer boardId) {
  boardAdminService.unhideBoard(boardId);
 }

 public void resetReportCount(Integer boardId) {
  boardAdminService.resetReportCount(boardId);
 }

 public void delete(Integer boardId, String memberId, boolean isAdmin) {
  boardCommandService.delete(boardId, memberId, isAdmin);
 }

 public void adminDelete(Integer boardId, String adminId, String reason) {
  boardAdminService.adminDelete(boardId, adminId, reason);
 }

 @Transactional(readOnly = true)
 public List<BoardResponse> getMyBoards(String memberId) {
  return boardQueryService.getMyBoards(memberId);
 }

 @Transactional(readOnly = true)
 public boolean isMyBoard(Integer boardId, String memberId) {
  return boardQueryService.isMyBoard(boardId, memberId);
 }
}
