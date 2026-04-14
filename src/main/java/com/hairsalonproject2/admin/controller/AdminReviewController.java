package com.hairsalonproject2.admin.controller;

import com.hairsalonproject2.admin.support.AdminPaginationUtils;
import com.hairsalonproject2.review.dto.ReviewResponse;
import com.hairsalonproject2.review.repository.ReviewRepository;
import com.hairsalonproject2.review.service.ReviewDummySeeder;
import com.hairsalonproject2.review.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Controller
@RequestMapping("/admin/reviews")
@RequiredArgsConstructor
public class AdminReviewController {

    private static final int PAGE_SIZE = 10;

    private final ReviewService reviewService;
    private final ReviewRepository reviewRepository;
    private final ReviewDummySeeder reviewDummySeeder;

    @GetMapping
    public String reviewList(@RequestParam(required = false) String keyword,
                             @RequestParam(required = false) String filterBy,
                             @RequestParam(defaultValue = "false") boolean todayOnly,
                             @RequestParam(defaultValue = "latest") String sortBy,
                             @RequestParam(defaultValue = "1") int page,
                             Model model) {
        List<ReviewResponse> filteredReviews = reviewService.getAllReviews(null, null, sortBy).stream()
                .filter(review -> !todayOnly || isToday(review.getCreatedAt()))
                .filter(review -> matchesReviewFilter(review, keyword, filterBy))
                .toList();
        Page<ReviewResponse> reviewPage = AdminPaginationUtils.slice(filteredReviews, page, PAGE_SIZE);

        model.addAttribute("reviewPage", reviewPage);
        model.addAttribute("reviews", reviewPage.getContent());
        model.addAttribute("pageNumbers", AdminPaginationUtils.getPageNumbers(reviewPage));
        model.addAttribute("currentPage", reviewPage.getNumber() + 1);
        model.addAttribute("previousGroupPage", AdminPaginationUtils.getPreviousGroupPage(reviewPage));
        model.addAttribute("nextGroupPage", AdminPaginationUtils.getNextGroupPage(reviewPage));
        model.addAttribute("keyword", keyword);
        model.addAttribute("filterBy", filterBy);
        model.addAttribute("selectedSort", sortBy);
        model.addAttribute("totalReviewCount", reviewRepository.count());
        model.addAttribute("todayReviewCount", reviewRepository.countByCreatedAtBetween(todayStart(), tomorrowStart()));
        model.addAttribute("todayOnly", todayOnly);
        model.addAttribute("currentMenu", "reviews");
        return "admin/reviews";
    }

    @PostMapping("/seed")
    public String seedReviews(RedirectAttributes redirectAttributes) {
        int created = reviewDummySeeder.seedReviewsFromCompletedReservations();
        if (created > 0) {
            redirectAttributes.addFlashAttribute("successMessage", created + "개의 리뷰 더미를 생성했습니다.");
        } else {
            redirectAttributes.addFlashAttribute("successMessage", "리뷰를 생성할 완료 예약이 없습니다.");
        }
        return "redirect:/admin/reviews";
    }

    @PostMapping("/seed/replies")
    public String seedReviewReplies(RedirectAttributes redirectAttributes) {
        int created = reviewDummySeeder.seedReviewReplies();
        if (created > 0) {
            redirectAttributes.addFlashAttribute("successMessage", created + "개의 리뷰 답변 더미를 생성했습니다.");
        } else {
            redirectAttributes.addFlashAttribute("successMessage", "답변할 리뷰가 없습니다.");
        }
        return "redirect:/admin/reviews";
    }

    private boolean matchesReviewFilter(ReviewResponse review, String keyword, String filterBy) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }

        String normalizedKeyword = keyword.trim().toLowerCase(Locale.ROOT);
        return switch (filterBy == null ? "all" : filterBy) {
            case "member" -> contains(review.getMemberName(), normalizedKeyword);
            case "designer" -> contains(review.getDesignerName(), normalizedKeyword);
            case "salon" -> contains(review.getSalonName(), normalizedKeyword);
            case "service" -> contains(review.getServiceName(), normalizedKeyword);
            default -> contains(review.getMemberName(), normalizedKeyword)
                    || contains(review.getDesignerName(), normalizedKeyword)
                    || contains(review.getSalonName(), normalizedKeyword)
                    || contains(review.getServiceName(), normalizedKeyword)
                    || contains(review.getContent(), normalizedKeyword);
        };
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private LocalDateTime todayStart() {
        return LocalDate.now().atStartOfDay();
    }

    private LocalDateTime tomorrowStart() {
        return LocalDate.now().plusDays(1).atStartOfDay();
    }

    private boolean isToday(LocalDateTime value) {
        return value != null && !value.isBefore(todayStart()) && value.isBefore(tomorrowStart());
    }
}
