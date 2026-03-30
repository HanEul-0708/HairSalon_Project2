package com.HairSalonProject2.board.service;

import com.HairSalonProject2.board.dto.request.BoardCreateRequest;
import com.HairSalonProject2.board.dto.request.BoardSearchRequest;
import com.HairSalonProject2.board.dto.request.BoardUpdateRequest;
import com.HairSalonProject2.board.dto.response.BoardDetailResponse;
import com.HairSalonProject2.board.dto.response.BoardResponse;
import com.HairSalonProject2.board.dto.response.BoardSummaryResponse;
import com.HairSalonProject2.board.entity.Board;
import com.HairSalonProject2.board.exception.BoardException;
import com.HairSalonProject2.board.repository.BoardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * BoardService — 게시판 비즈니스 로직 담당
 * =====================================================
 * Controller 는 요청만 받아서 Service 에 전달
 * 실제 기능 처리는 모두 여기서 담당
 *
 * @RequiredArgsConstructor
 * → final 필드 생성자 자동 생성 (의존성 주입)
 *
 * @Transactional
 * → DB 작업 중 오류 나면 자동으로 롤백(되돌리기)
 * → 읽기 전용 메서드는 @Transactional(readOnly = true) 사용
 *   (성능 최적화 — 변경 감지 비활성화)
 */
@Service
@RequiredArgsConstructor
@Transactional
public class BoardService {

    /**
     * BoardRepository 주입
     * DB 조회/저장 전담
     */
    private final BoardRepository boardRepository;


    /* =============================================
       목록 조회
       ============================================= */

    /**
     * 공지사항 목록 조회
     *
     * @return 공지사항 목록 (최신순)
     */
    @Transactional(readOnly = true)
    public List<BoardResponse> getNoticeList() {
        return boardRepository
                .findByTypeAndParentIdIsNullOrderByBoardIdDesc("NOTICE")
                .stream()
                .map(BoardResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * QnA 목록 조회
     *
     * @return QnA 목록 (최신순)
     */
    @Transactional(readOnly = true)
    public List<BoardResponse> getQnaList() {
        List<BoardResponse> list = boardRepository
                .findByTypeAndParentIdIsNullOrderByBoardIdDesc("QNA")
                .stream()
                .map(BoardResponse::from)
                .collect(Collectors.toList());

        // 각 게시글의 답글 개수 세팅
        list.forEach(board ->
                board.setReplyCount(
                        (int) boardRepository.countByParentId(board.getBoardId())
                )
        );

        return list;
    }

    /**
     * 타입 + 키워드 검색
     *
     * @param request 검색 조건 (type, keyword)
     * @return 검색 결과 목록
     */
    @Transactional(readOnly = true)
    public List<BoardResponse> search(BoardSearchRequest request) {
        List<Board> boards;

        if (request.hasKeyword()) {
            /* 키워드 있으면 검색 */
            boards = boardRepository.searchByTypeAndKeyword(
                    request.getType(), request.getKeyword());
        } else {
            /* 키워드 없으면 전체 목록 */
            boards = boardRepository
                    .findByTypeAndParentIdIsNullOrderByBoardIdDesc(
                            request.getType());
        }

        return boards.stream()
                .map(BoardResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 메인 홈 최근 공지사항 (최대 5개)
     *
     * @return 최근 공지사항 요약 목록
     */
    @Transactional(readOnly = true)
    public List<BoardSummaryResponse> getRecentNotices() {
        return boardRepository
                .findByTypeAndParentIdIsNullOrderByBoardIdDesc("NOTICE")
                .stream()
                .limit(5)   /* 최대 5개만 */
                .map(BoardSummaryResponse::from)
                .collect(Collectors.toList());
    }


    /* =============================================
       상세 조회
       ============================================= */

    /**
     * 게시글 상세 조회
     * 조회수 1 증가 포함
     *
     * @param boardId 게시글 번호
     * @return 게시글 상세 정보
     * @throws BoardException 게시글이 없을 때
     */
    public BoardDetailResponse getDetail(Long boardId) {
        /* 게시글 조회 — 없으면 예외 */
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() ->
                        new BoardException("존재하지 않는 게시글입니다."));

        /* 조회수 1 증가 */
        boardRepository.increaseViewCount(boardId);

        /* 상세 응답 생성 */
        BoardDetailResponse response = BoardDetailResponse.from(board);

        /* 답글 목록 조회 후 세팅 */
        List<BoardResponse> replies = boardRepository
                .findByParentIdOrderByBoardIdAsc(boardId)
                .stream()
                .map(BoardResponse::from)
                .collect(Collectors.toList());
        response.setReplies(replies);

        return response;
    }


    /* =============================================
       작성 / 수정 / 삭제
       ============================================= */

    /**
     * 게시글 작성
     *
     * @param request  작성 요청 데이터
     * @param memberId 작성자 회원 ID (로그인 사용자)
     * @return 생성된 게시글 번호
     */
    public Long create(BoardCreateRequest request, String memberId) {
        Board board;

        if (request.getParentId() != null) {
            /* 답글 작성 — 원글 존재 여부 먼저 확인 */
            boardRepository.findById(request.getParentId())
                    .orElseThrow(() ->
                            new BoardException("원글을 찾을 수 없습니다."));

            board = Board.createReply(
                    memberId,
                    request.getType(),
                    request.getTitle(),
                    request.getContent(),
                    request.getParentId()
            );
        } else {
            /* 원글 작성 */
            board = Board.create(
                    memberId,
                    request.getType(),
                    request.getTitle(),
                    request.getContent()
            );
        }

        return boardRepository.save(board).getBoardId();
    }

    /**
     * 게시글 수정
     * 작성자 본인만 수정 가능
     *
     * @param boardId  수정할 게시글 번호
     * @param request  수정 요청 데이터
     * @param memberId 요청자 회원 ID
     * @throws BoardException 게시글 없음 / 권한 없음
     */
    public void update(Long boardId, BoardUpdateRequest request,
                       String memberId) {
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() ->
                        new BoardException("존재하지 않는 게시글입니다."));

        /* 작성자 본인 여부 확인 */
        if (!board.isWriter(memberId)) {
            throw new BoardException("수정 권한이 없습니다.");
        }

        /* 수정 처리 — @Transactional 덕분에 자동 저장 */
        board.update(request.getTitle(), request.getContent());
    }

    /**
     * 게시글 삭제
     * 작성자 본인 또는 관리자만 삭제 가능
     *
     * @param boardId  삭제할 게시글 번호
     * @param memberId 요청자 회원 ID
     * @param isAdmin  관리자 여부
     * @throws BoardException 게시글 없음 / 권한 없음
     */
    public void delete(Long boardId, String memberId, boolean isAdmin) {
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() ->
                        new BoardException("존재하지 않는 게시글입니다."));

        /* 관리자이거나 본인이면 삭제 가능 */
        if (!isAdmin && !board.isWriter(memberId)) {
            throw new BoardException("삭제 권한이 없습니다.");
        }

        /* cascade = ALL 설정으로 이미지/파일도 함께 삭제됨 */
        boardRepository.delete(board);
    }

    /**
     * 관리자 전용 삭제
     * Security 에서 ADMIN 권한 확인 후 호출
     *
     * @param boardId 삭제할 게시글 번호
     */
    public void adminDelete(Long boardId) {
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() ->
                        new BoardException("존재하지 않는 게시글입니다."));
        boardRepository.delete(board);
    }

    /**
     * 내가 작성한 게시글 목록
     * 마이페이지에서 사용
     *
     * @param memberId 회원 ID
     * @return 내 게시글 목록
     */
    @Transactional(readOnly = true)
    public List<BoardResponse> getMyBoards(String memberId) {
        return boardRepository
                .findByMemberIdOrderByBoardIdDesc(memberId)
                .stream()
                .map(BoardResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 게시글 존재 + 본인 여부 확인
     * 수정/삭제 버튼 표시 여부 결정에 사용
     *
     * @param boardId  게시글 번호
     * @param memberId 확인할 회원 ID
     * @return true: 본인 게시글 / false: 타인 게시글
     */
    @Transactional(readOnly = true)
    public boolean isMyBoard(Long boardId, String memberId) {
        return boardRepository.findById(boardId)
                .map(board -> board.isWriter(memberId))
                .orElse(false);
    }
}