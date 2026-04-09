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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
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

import java.util.ArrayList;
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
                          Model model) {
        Page<BoardResponse> boardPage = boardService.getQnaPage(page, size, request.getKeyword());
        applyBoardPageModel(model, boardPage, request.getKeyword(), BoardType.QNA, size);
        return "board/qna-list";
    }

    @GetMapping("/qna/{boardId}")
    public String qnaDetail(@PathVariable Integer boardId,
                            HttpServletRequest request,
                            HttpServletResponse response,
                            Model model) {
        BoardDetailResponse board = getBoardDetailWithViewGuard(boardId, BoardType.QNA, request, response);
        model.addAttribute("board", board);
        return "board/qna-detail";
    }

    @PostMapping("/qna/{boardId}/report")
    @PreAuthorize("isAuthenticated()")
    public String reportQna(@PathVariable Integer boardId,
                            @AuthenticationPrincipal UserDetails userDetails) {
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
                            @AuthenticationPrincipal UserDetails userDetails,
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
                           @AuthenticationPrincipal UserDetails userDetails) {
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
                               @AuthenticationPrincipal UserDetails userDetails,
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
                           @AuthenticationPrincipal UserDetails userDetails,
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
                       @AuthenticationPrincipal UserDetails userDetails,
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
                         @AuthenticationPrincipal UserDetails userDetails) {
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
        model.addAttribute("pageNumbers", getPageNumbers(boardPage));
    }

    private List<Integer> getPageNumbers(Page<?> page) {
        if (page.getTotalPages() <= 0) {
            return List.of();
        }
        int current = page.getNumber();
        int start = Math.max(0, current - 2);
        int end = Math.min(page.getTotalPages() - 1, current + 2);
        List<Integer> numbers = new ArrayList<>();
        for (int i = start; i <= end; i++) {
            numbers.add(i);
        }
        return numbers;
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

    private String buildDetailPath(Integer boardId, BoardType boardType) {
        return boardType == BoardType.NOTICE
                ? "/boards/notices/" + boardId
                : "/boards/qna/" + boardId;
    }

    private boolean canEditBoard(Integer boardId, BoardType boardType, UserDetails userDetails) {
        if (boardType == BoardType.NOTICE && isAdmin(userDetails)) {
            return true;
        }

        return boardService.isMyBoard(boardId, userDetails.getUsername());
    }

    private boolean isAdmin(UserDetails userDetails) {
        return userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
