package com.hairsalonproject2.board.controller;

import com.hairsalonproject2.board.dto.request.BoardCreateRequest;
import com.hairsalonproject2.board.dto.request.BoardReplyRequest;
import com.hairsalonproject2.board.dto.request.BoardSearchRequest;
import com.hairsalonproject2.board.dto.request.BoardUpdateRequest;
import com.hairsalonproject2.board.dto.response.BoardDetailResponse;
import com.hairsalonproject2.board.dto.response.BoardResponse;
import com.hairsalonproject2.board.service.BoardService;
import com.hairsalonproject2.board.service.BoardViewGuard;
import com.hairsalonproject2.board.support.BoardContentPolicy;
import com.hairsalonproject2.common.constant.BoardType;
import com.hairsalonproject2.common.support.PageUtils;
import com.hairsalonproject2.member.service.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * BoardController
 * 게시판 화면 요청 처리
 */
@Controller
@RequestMapping("/boards")
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;
    private final BoardViewGuard boardViewGuard;

    @GetMapping("/notices")
    public String noticeList(BoardSearchRequest request,
                             @RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "10") int size,
                             Model model) {
        Page<BoardResponse> boardPage = boardService.getNoticePage(page, size, request.getKeyword());
        applyBoardPageModel(model, boardPage, request.getKeyword(), BoardType.NOTICE, size);
        return "board/notice-list";
    }

    @GetMapping("/notices/{boardId}")
    public String noticeDetail(@PathVariable Integer boardId,
                               @RequestParam(required = false) String error,
                               HttpServletRequest request,
                               HttpServletResponse response,
                               Model model) {
        BoardDetailResponse board = getBoardDetailWithViewGuard(boardId, BoardType.NOTICE, request, response);
        model.addAttribute("board", board);
        applyDetailMessage(model, error, false, false);
        return "board/notice-detail";
    }

    @GetMapping("/qna")
    public String qnaList(BoardSearchRequest request,
                          @RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = "10") int size,
                          @AuthenticationPrincipal CustomUserDetails userDetails,
                          Model model) {
        Page<BoardResponse> boardPage = boardService.getQnaPageForViewer(
                page,
                size,
                request.getKeyword(),
                memberIdOrNull(userDetails),
                canViewAllQna(userDetails)
        );
        applyBoardPageModel(model, boardPage, request.getKeyword(), BoardType.QNA, size);
        return "board/qna-list";
    }

    @GetMapping("/qna/{boardId}")
    public String qnaDetail(@PathVariable Integer boardId,
                            @RequestParam(required = false) String error,
                            @RequestParam(defaultValue = "false") boolean reported,
                            @RequestParam(defaultValue = "false") boolean alreadyReported,
                            HttpServletRequest request,
                            HttpServletResponse response,
                            @AuthenticationPrincipal CustomUserDetails userDetails,
                            Model model) {
        if (userDetails == null) {
            return "redirect:/members/login";
        }

        BoardDetailResponse board = getQnaDetailWithViewGuard(boardId, request, response, userDetails);
        model.addAttribute("board", board);
        model.addAttribute("boardReplyRequest", new BoardReplyRequest());
        applyDetailMessage(model, error, reported, alreadyReported);
        return "board/qna-detail";
    }

    @PostMapping("/qna/{boardId}/report")
    @PreAuthorize("isAuthenticated()")
    public String reportQna(@PathVariable Integer boardId,
                            @AuthenticationPrincipal CustomUserDetails userDetails) {
        boolean reported = boardService.reportAccessibleQna(
                boardId,
                userDetails.getUsername(),
                canViewAllQna(userDetails)
        );
        if (reported) {
            return "redirect:/boards/qna/" + boardId + "?reported=true";
        }
        return "redirect:/boards/qna/" + boardId + "?alreadyReported=true";
    }

    @GetMapping("/qna/new")
    @PreAuthorize("isAuthenticated()")
    public String qnaWriteForm(Model model) {
        model.addAttribute("boardType", BoardType.QNA.name());
        model.addAttribute("boardCreateRequest", new BoardCreateRequest());
        return "board/write";
    }

    @PostMapping("/qna")
    @PreAuthorize("isAuthenticated()")
    public String qnaCreate(@ModelAttribute("boardCreateRequest") @Valid BoardCreateRequest request,
                            BindingResult bindingResult,
                            @RequestParam(value = "files", required = false) List<MultipartFile> files,
                            @AuthenticationPrincipal CustomUserDetails userDetails,
                            Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("boardType", BoardType.QNA.name());
            return "board/write";
        }
        rejectTextLimitIfNeeded(bindingResult, "content", request.getContent(), "내용");
        if (bindingResult.hasErrors()) {
            model.addAttribute("boardType", BoardType.QNA.name());
            return "board/write";
        }
        Integer boardId = boardService.create(request, userDetails.getUsername(), files, BoardType.QNA);
        return "redirect:/boards/qna/" + boardId;
    }

    @PostMapping("/qna/{boardId}/reply")
    @PreAuthorize("hasRole('ADMIN')")
    public String replyQna(@PathVariable Integer boardId,
                           @ModelAttribute("boardReplyRequest") @Valid BoardReplyRequest request,
                           BindingResult bindingResult,
                           @AuthenticationPrincipal CustomUserDetails userDetails,
                           Model model) {
        if (request.getParentId() == null || boardId.intValue() != request.getParentId().intValue()) {
            return "redirect:/boards/qna/" + boardId;
        }
        rejectTextLimitIfNeeded(bindingResult, "content", request.getContent(), "답변 내용");
        if (bindingResult.hasErrors()) {
            BoardDetailResponse board = boardService.getAccessibleQnaDetail(
                    boardId,
                    userDetails.getUsername(),
                    canViewAllQna(userDetails)
            );
            model.addAttribute("board", board);
            return "board/qna-detail";
        }
        boardService.createReply(request, userDetails.getUsername());
        return "redirect:/boards/qna/" + boardId;
    }

    @GetMapping("/notices/new")
    @PreAuthorize("hasRole('ADMIN')")
    public String noticeWriteForm(Model model) {
        model.addAttribute("boardType", BoardType.NOTICE.name());
        model.addAttribute("boardCreateRequest", new BoardCreateRequest());
        return "board/write";
    }

    @PostMapping("/notices")
    @PreAuthorize("hasRole('ADMIN')")
    public String noticeCreate(@ModelAttribute("boardCreateRequest") @Valid BoardCreateRequest request,
                               BindingResult bindingResult,
                               @RequestParam(value = "files", required = false) List<MultipartFile> files,
                               @AuthenticationPrincipal CustomUserDetails userDetails,
                               Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("boardType", BoardType.NOTICE.name());
            return "board/write";
        }
        rejectTextLimitIfNeeded(bindingResult, "content", request.getContent(), "내용");
        if (bindingResult.hasErrors()) {
            model.addAttribute("boardType", BoardType.NOTICE.name());
            return "board/write";
        }
        Integer boardId = boardService.create(request, userDetails.getUsername(), files, BoardType.NOTICE);
        return "redirect:/boards/notices/" + boardId;
    }

    @GetMapping("/{boardId}/edit")
    @PreAuthorize("isAuthenticated()")
    public String editForm(@PathVariable Integer boardId,
                           @AuthenticationPrincipal CustomUserDetails userDetails,
                           Model model) {
        BoardType boardType = boardService.getBoardType(boardId);
        if (!boardService.canEditBoard(boardId, userDetails.getUsername(), isAdmin(userDetails))) {
            return "redirect:" + buildDetailPath(boardId, boardType) + "?error=forbidden";
        }
        BoardDetailResponse board = boardService.getDetailOnly(boardId);
        BoardUpdateRequest request = new BoardUpdateRequest();
        request.setTitle(board.getTitle());
        request.setContent(board.getContent());
        model.addAttribute("board", board);
        model.addAttribute("boardUpdateRequest", request);
        return "board/edit";
    }

    @PostMapping("/{boardId}/edit")
    @PreAuthorize("isAuthenticated()")
    public String edit(@PathVariable Integer boardId,
                       @ModelAttribute("boardUpdateRequest") @Valid BoardUpdateRequest request,
                       BindingResult bindingResult,
                       @RequestParam(value = "files", required = false) List<MultipartFile> files,
                       @AuthenticationPrincipal CustomUserDetails userDetails,
                       Model model) {
        BoardType boardType = boardService.getBoardType(boardId);
        if (bindingResult.hasErrors()) {
            BoardDetailResponse board = boardService.getDetailOnly(boardId);
            model.addAttribute("board", board);
            model.addAttribute("boardUpdateRequest", request);
            return "board/edit";
        }
        rejectTextLimitIfNeeded(bindingResult, "content", request.getContent(), "내용");
        if (bindingResult.hasErrors()) {
            BoardDetailResponse board = boardService.getDetailOnly(boardId);
            model.addAttribute("board", board);
            model.addAttribute("boardUpdateRequest", request);
            return "board/edit";
        }
        boardService.update(boardId, request, userDetails.getUsername(), files, isAdmin(userDetails));
        return "redirect:" + buildDetailPath(boardId, boardType);
    }

    @DeleteMapping("/{boardId}")
    @PreAuthorize("isAuthenticated()")
    public String delete(@PathVariable Integer boardId,
                         @AuthenticationPrincipal CustomUserDetails userDetails) {
        boolean isAdmin = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        BoardType type = boardService.getBoardType(boardId);
        boardService.delete(boardId, userDetails.getUsername(), isAdmin);
        return type == BoardType.NOTICE ? "redirect:/boards/notices" : "redirect:/boards/qna";
    }

    private void applyBoardPageModel(Model model,
                                     Page<BoardResponse> boardPage,
                                     String keyword,
                                     BoardType boardType,
                                     int size) {
        model.addAttribute("boardPage", boardPage);
        model.addAttribute("boardList", boardPage.getContent());
        model.addAttribute("keyword", keyword);
        model.addAttribute("boardType", boardType.name());
        model.addAttribute("size", size);
        model.addAttribute("pageNumbers", PageUtils.zeroBasedPageWindow(boardPage, 2));
    }

    private BoardDetailResponse getBoardDetailWithViewGuard(Integer boardId,
                                                            BoardType boardType,
                                                            HttpServletRequest request,
                                                            HttpServletResponse response) {
        if (!boardViewGuard.shouldIncreaseView(boardId, boardType, request, response)) {
            return boardService.getPublicDetail(boardId, boardType);
        }
        return boardService.getPublicDetailAndIncreaseView(boardId, boardType);
    }

    private BoardDetailResponse getQnaDetailWithViewGuard(Integer boardId,
                                                          HttpServletRequest request,
                                                          HttpServletResponse response,
                                                          CustomUserDetails userDetails) {
        BoardDetailResponse board = boardService.getAccessibleQnaDetail(
                boardId,
                userDetails.getUsername(),
                canViewAllQna(userDetails)
        );
        if (!boardViewGuard.shouldIncreaseView(boardId, BoardType.QNA, request, response)) {
            return board;
        }
        return boardService.getAccessibleQnaDetailAndIncreaseView(
                boardId,
                userDetails.getUsername(),
                canViewAllQna(userDetails)
        );
    }

    private String buildDetailPath(Integer boardId, BoardType boardType) {
        return boardType == BoardType.NOTICE
                ? "/boards/notices/" + boardId
                : "/boards/qna/" + boardId;
    }

    private boolean isAdmin(CustomUserDetails userDetails) {
        return userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    private void applyDetailMessage(Model model, String error, boolean reported, boolean alreadyReported) {
        if ("forbidden".equals(error)) {
            model.addAttribute("boardMessageType", "error");
            model.addAttribute("boardMessage", "수정 권한이 없습니다.");
            return;
        }
        if (reported) {
            model.addAttribute("boardMessageType", "success");
            model.addAttribute("boardMessage", "신고가 접수되었습니다.");
            return;
        }
        if (alreadyReported) {
            model.addAttribute("boardMessageType", "info");
            model.addAttribute("boardMessage", "이미 신고한 게시글입니다.");
        }
    }

    private boolean canViewAllQna(CustomUserDetails userDetails) {
        return userDetails != null && userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                        || a.getAuthority().equals("ROLE_DESIGNER"));
    }

    private void rejectTextLimitIfNeeded(BindingResult bindingResult, String field, String content, String label) {
        if (!BoardContentPolicy.exceedsTextLimit(content)) {
            return;
        }
        bindingResult.rejectValue(
                field,
                "Size",
                label + "은 " + BoardContentPolicy.MAX_TEXT_LENGTH + "자 이하로 입력해주세요."
        );
    }

    private String memberIdOrNull(CustomUserDetails userDetails) {
        return userDetails == null ? null : userDetails.getUsername();
    }
}
