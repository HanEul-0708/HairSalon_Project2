package com.hairsalonproject2.board.controller;

import com.hairsalonproject2.board.dto.request.BoardCreateRequest;
import com.hairsalonproject2.board.dto.request.BoardReplyRequest;
import com.hairsalonproject2.board.dto.request.BoardSearchRequest;
import com.hairsalonproject2.board.dto.request.BoardUpdateRequest;
import com.hairsalonproject2.board.dto.response.BoardDetailResponse;
import com.hairsalonproject2.board.dto.response.BoardResponse;
import com.hairsalonproject2.board.service.BoardService;
import com.hairsalonproject2.board.service.BoardViewGuard;
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
                               HttpServletRequest request,
                               HttpServletResponse response,
                               Model model) {
        BoardDetailResponse board = getBoardDetailWithViewGuard(boardId, BoardType.NOTICE, request, response);
        model.addAttribute("board", board);
        return "board/notice-detail";
    }

    @GetMapping("/qna")
    public String qnaList(BoardSearchRequest request,
                          @RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = "10") int size,
                          @AuthenticationPrincipal CustomUserDetails userDetails,
                          Model model) {
        Page<BoardResponse> boardPage = boardService.getQnaPage(
                page,
                size,
                request.getKeyword(),
                userDetails == null ? null : userDetails.getUsername(),
                canViewAllQna(userDetails)
        );
        applyBoardPageModel(model, boardPage, request.getKeyword(), BoardType.QNA, size);
        return "board/qna-list";
    }

    @GetMapping("/qna/{boardId}")
    public String qnaDetail(@PathVariable Integer boardId,
                            HttpServletRequest request,
                            HttpServletResponse response,
                            @AuthenticationPrincipal CustomUserDetails userDetails,
                            Model model) {
        if (userDetails == null) {
            return "redirect:/members/login";
        }

        BoardDetailResponse board = getQnaDetailWithViewGuard(boardId, request, response, userDetails);
        model.addAttribute("board", board);
        return "board/qna-detail";
    }

    @PostMapping("/qna/{boardId}/report")
    @PreAuthorize("isAuthenticated()")
    public String reportQna(@PathVariable Integer boardId,
                            @AuthenticationPrincipal CustomUserDetails userDetails) {
        getPublicBoardDetailWithAccessCheck(boardId, BoardType.QNA, userDetails);

        boolean reported = boardService.reportBoard(boardId, userDetails.getUsername());
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
    public String qnaCreate(@Valid BoardCreateRequest request,
                            BindingResult bindingResult,
                            @RequestParam(value = "files", required = false) List<MultipartFile> files,
                            @AuthenticationPrincipal CustomUserDetails userDetails,
                            Model model) {
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
                           @Valid BoardReplyRequest request,
                           BindingResult bindingResult,
                           @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (request.getParentId() == null || boardId.intValue() != request.getParentId().intValue()) {
            return "redirect:/boards/qna/" + boardId;
        }
        if (bindingResult.hasErrors()) {
            return "redirect:/boards/qna/" + boardId + "?error=validation";
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
    public String noticeCreate(@Valid BoardCreateRequest request,
                               BindingResult bindingResult,
                               @RequestParam(value = "files", required = false) List<MultipartFile> files,
                               @AuthenticationPrincipal CustomUserDetails userDetails,
                               Model model) {
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
        if (!canEditBoard(boardId, boardType, userDetails)) {
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
                       @Valid BoardUpdateRequest request,
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
        BoardDetailResponse board = getPublicBoardDetailWithAccessCheck(boardId, BoardType.QNA, userDetails);
        if (!boardViewGuard.shouldIncreaseView(boardId, BoardType.QNA, request, response)) {
            return board;
        }
        return requireQnaAccess(
                boardService.getPublicDetailAndIncreaseView(boardId, BoardType.QNA),
                userDetails
        );
    }

    private String buildDetailPath(Integer boardId, BoardType boardType) {
        return boardType == BoardType.NOTICE
                ? "/boards/notices/" + boardId
                : "/boards/qna/" + boardId;
    }

    private boolean canEditBoard(Integer boardId, BoardType boardType, CustomUserDetails userDetails) {
        if (boardType == BoardType.NOTICE && isAdmin(userDetails)) {
            return true;
        }

        return boardService.isMyBoard(boardId, userDetails.getUsername());
    }

    private boolean isAdmin(CustomUserDetails userDetails) {
        return userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    private boolean canViewAllQna(CustomUserDetails userDetails) {
        return userDetails != null && userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                        || a.getAuthority().equals("ROLE_DESIGNER"));
    }

    private BoardDetailResponse getPublicBoardDetailWithAccessCheck(Integer boardId,
                                                                    BoardType boardType,
                                                                    CustomUserDetails userDetails) {
        BoardDetailResponse board = boardService.getPublicDetail(boardId, boardType);
        return requireQnaAccess(board, userDetails);
    }

    private BoardDetailResponse requireQnaAccess(BoardDetailResponse board, CustomUserDetails userDetails) {
        if (canViewAllQna(userDetails) || board.getMemberId().equals(userDetails.getUsername())) {
            return board;
        }
        throw new org.springframework.security.access.AccessDeniedException("문의글을 조회할 권한이 없습니다.");
    }
}
