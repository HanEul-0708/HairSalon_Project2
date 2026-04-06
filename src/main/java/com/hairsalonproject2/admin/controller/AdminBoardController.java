package com.hairsalonproject2.admin.controller;

import com.hairsalonproject2.admin.dto.response.BoardDeleteLogResponse;
import com.hairsalonproject2.board.dto.response.BoardResponse;
import com.hairsalonproject2.board.service.BoardService;
import com.hairsalonproject2.common.constant.BoardType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * AdminBoardController
 * 관리자 게시글 관리 화면
 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/boards")
@PreAuthorize("hasRole('ADMIN')")
public class AdminBoardController {

    private final BoardService boardService;

    @GetMapping
    public String boardList(@RequestParam(required = false) String type,
                            @RequestParam(required = false) String keyword,
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "10") int size,
                            Model model) {
        BoardType boardType = null;
        if (type != null && !type.isBlank()) {
            try {
                boardType = BoardType.valueOf(type.toUpperCase());
            } catch (IllegalArgumentException e) {
                boardType = null;
            }
        }

        Page<BoardResponse> boardPage = boardService.getAdminBoardPage(boardType, keyword, page, size);
        List<BoardDeleteLogResponse> deleteLogs = boardService.getRecentDeleteLogs();

        model.addAttribute("boardPage", boardPage);
        model.addAttribute("boardList", boardPage.getContent());
        model.addAttribute("pageNumbers", getPageNumbers(boardPage));
        model.addAttribute("size", size);
        model.addAttribute("noticeCount", boardService.countOriginalBoards(BoardType.NOTICE));
        model.addAttribute("qnaCount", boardService.countOriginalBoards(BoardType.QNA));
        model.addAttribute("todayCount", boardService.countTodayBoards());
        model.addAttribute("hiddenCount", boardService.countHiddenBoards());
        model.addAttribute("reportedCount", boardService.countReportedBoards());
        model.addAttribute("deleteLogs", deleteLogs);
        model.addAttribute("selectedType", type);
        model.addAttribute("keyword", keyword);
        model.addAttribute("currentMenu", "boards");
        return "admin/boards";
    }

    @PostMapping("/{boardId}/hide")
    public String hide(@PathVariable Integer boardId,
                       @RequestParam(required = false) String reason) {
        boardService.hideBoard(boardId, reason);
        return "redirect:/admin/boards";
    }

    @PostMapping("/{boardId}/unhide")
    public String unhide(@PathVariable Integer boardId) {
        boardService.unhideBoard(boardId);
        return "redirect:/admin/boards";
    }

    @PostMapping("/{boardId}/reports/reset")
    public String resetReports(@PathVariable Integer boardId) {
        boardService.resetReportCount(boardId);
        return "redirect:/admin/boards";
    }

    @DeleteMapping("/{boardId}")
    public String delete(@PathVariable Integer boardId,
                         @RequestParam(required = false) String reason,
                         @AuthenticationPrincipal UserDetails userDetails) {
        boardService.adminDelete(boardId, userDetails.getUsername(), reason);
        return "redirect:/admin/boards";
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
}