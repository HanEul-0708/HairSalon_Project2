package com.hairsalonproject2.admin.controller;

import com.hairsalonproject2.admin.dto.response.BoardDeleteLogResponse;
import com.hairsalonproject2.admin.support.AdminPaginationUtils;
import com.hairsalonproject2.board.dto.response.BoardResponse;
import com.hairsalonproject2.board.service.BoardDummySeeder;
import com.hairsalonproject2.board.service.BoardService;
import com.hairsalonproject2.common.constant.BoardType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/boards")
@PreAuthorize("hasRole('ADMIN')")
public class AdminBoardController {

    private static final int PAGE_SIZE = 10;

    private final BoardService boardService;
    private final BoardDummySeeder boardDummySeeder;

    @GetMapping
    public String boardList(@RequestParam(required = false) String type,
                            @RequestParam(required = false) String keyword,
                            @RequestParam(defaultValue = "1") int page,
                            Model model) {
        BoardType boardType = null;
        if (type != null && !type.isBlank()) {
            try {
                boardType = BoardType.valueOf(type.toUpperCase());
            } catch (IllegalArgumentException e) {
                boardType = null;
            }
        }

        int safePage = AdminPaginationUtils.normalizePageNumber(page);
        Page<BoardResponse> boardPage = boardService.getAdminBoardPage(boardType, keyword, safePage - 1, PAGE_SIZE);
        if (boardPage.getTotalPages() > 0 && safePage > boardPage.getTotalPages()) {
            boardPage = boardService.getAdminBoardPage(boardType, keyword, boardPage.getTotalPages() - 1, PAGE_SIZE);
        }

        List<BoardDeleteLogResponse> deleteLogs = boardService.getRecentDeleteLogs();

        model.addAttribute("boardPage", boardPage);
        model.addAttribute("boardList", boardPage.getContent());
        model.addAttribute("pageNumbers", AdminPaginationUtils.getPageNumbers(boardPage));
        model.addAttribute("currentPage", boardPage.getNumber() + 1);
        model.addAttribute("previousGroupPage", AdminPaginationUtils.getPreviousGroupPage(boardPage));
        model.addAttribute("nextGroupPage", AdminPaginationUtils.getNextGroupPage(boardPage));
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

    @PostMapping("/seed/notices")
    public String seedNotices(RedirectAttributes redirectAttributes) {
        int created = boardDummySeeder.seedNoticeBoards();
        redirectAttributes.addFlashAttribute(
                "successMessage",
                created > 0 ? created + "개의 공지사항 더미를 생성했습니다." : "생성 가능한 공지사항 작성 관리자 계정이 없습니다."
        );
        return "redirect:/admin/boards?type=NOTICE";
    }

    @PostMapping("/seed/qna")
    public String seedQna(RedirectAttributes redirectAttributes) {
        int created = boardDummySeeder.seedQnaBoards();
        redirectAttributes.addFlashAttribute(
                "successMessage",
                created > 0 ? created + "개의 문의사항 더미를 생성했습니다." : "생성 가능한 문의 작성 회원 계정이 없습니다."
        );
        return "redirect:/admin/boards?type=QNA";
    }

    @PostMapping("/seed/qna-replies")
    public String seedQnaReplies(RedirectAttributes redirectAttributes) {
        int created = boardDummySeeder.seedQnaReplies();
        redirectAttributes.addFlashAttribute(
                "successMessage",
                created > 0 ? created + "개의 문의 답변 더미를 생성했습니다." : "답변할 문의사항이 없거나 사용할 디자이너 계정이 없습니다."
        );
        return "redirect:/admin/boards?type=QNA";
    }
}
