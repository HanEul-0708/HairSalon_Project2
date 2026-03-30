package com.HairSalonProject2.board.controller;

import com.HairSalonProject2.board.dto.request.BoardCreateRequest;
import com.HairSalonProject2.board.dto.request.BoardReplyRequest;
import com.HairSalonProject2.board.dto.request.BoardSearchRequest;
import com.HairSalonProject2.board.dto.request.BoardUpdateRequest;
import com.HairSalonProject2.board.dto.response.BoardDetailResponse;
import com.HairSalonProject2.board.dto.response.BoardResponse;
import com.HairSalonProject2.board.service.BoardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * BoardController — 게시판 URL 교통 정리
 * =====================================================
 * 브라우저 요청을 받아서 Service 에 전달하고
 * 결과를 HTML 에 넘겨주는 중간 다리 역할
 *
 * URL 규칙 (팀 문서 기준)
 * GET  /boards/notices          → 공지사항 목록
 * GET  /boards/notices/{id}     → 공지사항 상세
 * GET  /boards/qna              → QnA 목록
 * GET  /boards/qna/{id}         → QnA 상세
 * GET  /boards/qna/new          → QnA 작성 페이지
 * POST /boards/qna              → QnA 저장
 * GET  /boards/{id}/edit        → 수정 페이지
 * POST /boards/{id}/edit        → 수정 처리
 * POST /boards/{id}/delete      → 삭제 처리
 *
 * @RequiredArgsConstructor → final 필드 생성자 자동 생성
 */
@Controller
@RequestMapping("/boards")
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;


    /* =============================================
       공지사항
       ============================================= */

    /**
     * 공지사항 목록
     * GET /boards/notices
     * GET /boards/notices?keyword=검색어
     *
     * @param request 검색 조건 (keyword)
     * @param model   Thymeleaf 에 데이터 전달
     */
    @GetMapping("/notices")
    public String noticeList(BoardSearchRequest request, Model model) {
        // type 을 명시하여 서비스에 전달 (공지사항)
        String keyword = request.getKeyword();
        List<BoardResponse> boardList = boardService.searchBoards("NOTICE", keyword);
        model.addAttribute("boardList", boardList);
        model.addAttribute("keyword", keyword);
        model.addAttribute("boardType", "NOTICE");
        return "board/notice-list";
    }

    /**
     * 공지사항 상세
     * GET /boards/notices/{boardId}
     *
     * @param boardId 게시글 번호 (URL 경로 변수)
     * @param model   Thymeleaf 에 데이터 전달
     */
    @GetMapping("/notices/{boardId}")
    public String noticeDetail(@PathVariable Long boardId, Model model) {
        BoardDetailResponse board = boardService.getDetail(boardId);
        model.addAttribute("board", board);
        return "board/notice-detail";
    }


    /* =============================================
       QnA 게시판
       ============================================= */

    /**
     * QnA 목록
     * GET /boards/qna
     * GET /boards/qna?keyword=검색어
     */
    @GetMapping("/qna")
    public String qnaList(BoardSearchRequest request, Model model) {
        // QnA 목록 검색 처리
        String keyword = request.getKeyword();
        List<BoardResponse> boardList = boardService.searchBoards("QNA", keyword);
        model.addAttribute("boardList", boardList);
        model.addAttribute("keyword", keyword);
        model.addAttribute("boardType", "QNA");
        return "board/qna-list";
    }

    /**
     * QnA 상세
     * GET /boards/qna/{boardId}
     */
    @GetMapping("/qna/{boardId}")
    public String qnaDetail(@PathVariable Long boardId, Model model) {
        BoardDetailResponse board = boardService.getDetail(boardId);
        model.addAttribute("board", board);
        return "board/qna-detail";
    }

    /**
     * QnA 작성 페이지
     * GET /boards/qna/new
     *
     * @PreAuthorize → 로그인한 사람만 접근 가능
     * (비로그인 시 Security 가 자동으로 로그인 페이지로 이동)
     */
    @GetMapping("/qna/new")
    @PreAuthorize("isAuthenticated()")
    public String qnaWriteForm(Model model) {
        model.addAttribute("boardType", "QNA");
        return "board/write";
    }

    /**
     * QnA 저장 처리
     * POST /boards/qna
     *
     * @Valid          → BoardCreateRequest 유효성 검사 실행
     * BindingResult  → 유효성 검사 결과 담는 객체
     * @AuthenticationPrincipal → 현재 로그인한 사용자 정보
     */
    @PostMapping("/qna")
    @PreAuthorize("isAuthenticated()")
    public String qnaCreate(@Valid BoardCreateRequest request,
                            BindingResult bindingResult,
                            @AuthenticationPrincipal UserDetails userDetails,
                            Model model) {

        /* 유효성 검사 실패 시 작성 페이지로 돌아감 */
        if (bindingResult.hasErrors()) {
            model.addAttribute("boardType", "QNA");
            return "board/write";
        }

        /* 현재 로그인한 사용자 ID 가져오기 */
        String memberId = userDetails.getUsername();

        /* 게시글 저장 후 상세 페이지로 이동 */
        Long boardId = boardService.create(request, memberId);
        return "redirect:/boards/qna/" + boardId;
    }

    /**
     * QnA 답글 저장 처리
     * POST /boards/qna/{boardId}/reply
     *
     * 관리자가 QnA 게시글에 답글을 달 때 사용한다. 원글의 boardId는 URL 경로로
     * 전달되며, BoardReplyRequest의 parentId에도 동일한 값이 설정되어야 한다.
     * 제목이 비어 있으면 Service에서 자동으로 "Re: 원글 제목" 형태로 생성한다.
     *
     * @param boardId 원글 게시글 번호
     * @param request 답글 작성 요청 DTO
     * @param bindingResult 유효성 검사 결과
     * @param userDetails 로그인 사용자 정보
     * @param model Thymeleaf 모델
     * @return 작성 후 원글 상세 페이지로 리다이렉트
     */
    @PostMapping("/qna/{boardId}/reply")
    @PreAuthorize("hasRole('ADMIN')")
    public String replyQna(@PathVariable Long boardId,
                           @Valid BoardReplyRequest request,
                           BindingResult bindingResult,
                           @AuthenticationPrincipal UserDetails userDetails,
                           Model model) {

        // parentId가 URL과 일치하는지 확인 (안 맞으면 강제로 맞춤)
        if (!boardId.equals(request.getParentId())) {
            // BindingResult에 에러 추가 가능하지만 간단히 상위 페이지로 이동
            return "redirect:/boards/qna/" + boardId;
        }

        // 유효성 검사 실패 시 원글 상세 페이지로 이동 (에러 처리 단순화)
        if (bindingResult.hasErrors()) {
            return "redirect:/boards/qna/" + boardId + "?error=validation";
        }

        // 로그인 사용자 ID (관리자)
        String memberId = userDetails.getUsername();

        // 답글 저장
        boardService.createReply(request, memberId);

        // 저장 후 원글 상세 페이지로 이동
        return "redirect:/boards/qna/" + boardId;
    }

    /**
     * 공지사항 작성 페이지
     * GET /boards/notices/new
     *
     * @PreAuthorize("hasRole('ADMIN')") → ADMIN 만 접근
     */
    @GetMapping("/notices/new")
    @PreAuthorize("hasRole('ADMIN')")
    public String noticeWriteForm(Model model) {
        model.addAttribute("boardType", "NOTICE");
        return "board/write";
    }

    /**
     * 공지사항 저장 처리
     * POST /boards/notices
     */
    @PostMapping("/notices")
    @PreAuthorize("hasRole('ADMIN')")
    public String noticeCreate(@Valid BoardCreateRequest request,
                               BindingResult bindingResult,
                               @AuthenticationPrincipal UserDetails userDetails,
                               Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("boardType", "NOTICE");
            return "board/write";
        }

        String memberId = userDetails.getUsername();
        Long boardId = boardService.create(request, memberId);
        return "redirect:/boards/notices/" + boardId;
    }


    /* =============================================
       수정 / 삭제
       ============================================= */

    /**
     * 게시글 수정 페이지
     * GET /boards/{boardId}/edit
     */
    @GetMapping("/{boardId}/edit")
    @PreAuthorize("isAuthenticated()")
    public String editForm(@PathVariable Long boardId,
                           @AuthenticationPrincipal UserDetails userDetails,
                           Model model) {
        BoardDetailResponse board = boardService.getDetail(boardId);

        /* 본인 게시글인지 확인 */
        if (!boardService.isMyBoard(boardId, userDetails.getUsername())) {
            return "redirect:/boards/qna/" + boardId + "?error=forbidden";
        }

        model.addAttribute("board", board);
        return "board/edit";
    }

    /**
     * 게시글 수정 처리
     * POST /boards/{boardId}/edit
     */
    @PostMapping("/{boardId}/edit")
    @PreAuthorize("isAuthenticated()")
    public String edit(@PathVariable Long boardId,
                       @Valid BoardUpdateRequest request,
                       BindingResult bindingResult,
                       @AuthenticationPrincipal UserDetails userDetails,
                       Model model) {

        if (bindingResult.hasErrors()) {
            /* 수정 실패 시 수정 페이지로 돌아감 */
            BoardDetailResponse board = boardService.getDetail(boardId);
            model.addAttribute("board", board);
            return "board/edit";
        }

        String memberId = userDetails.getUsername();
        boolean isAdmin = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        boardService.update(boardId, request, memberId);

        /* 수정 완료 후 상세 페이지로 이동 */
        String type = boardService.getDetail(boardId).getType();
        if ("NOTICE".equals(type)) {
            return "redirect:/boards/notices/" + boardId;
        }
        return "redirect:/boards/qna/" + boardId;
    }

    /**
     * 게시글 삭제 처리
     * POST /boards/{boardId}/delete
     *
     * DELETE 메서드는 HTML form 에서 지원 안 해서
     * POST 방식으로 처리
     */
    @PostMapping("/{boardId}/delete")
    @PreAuthorize("isAuthenticated()")
    public String delete(@PathVariable Long boardId,
                         @AuthenticationPrincipal UserDetails userDetails) {

        String memberId = userDetails.getUsername();
        boolean isAdmin = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        /* 삭제 전 타입 기억 */
        String type = boardService.getDetail(boardId).getType();

        boardService.delete(boardId, memberId, isAdmin);

        /* 삭제 후 목록으로 이동 */
        if ("NOTICE".equals(type)) {
            return "redirect:/boards/notices";
        }
        return "redirect:/boards/qna";
    }
}