package com.hairsalonproject2.admin.controller;

import com.hairsalonproject2.admin.support.AdminPaginationUtils;
import com.hairsalonproject2.common.constant.ReservationStatus;
import com.hairsalonproject2.reservation.dto.ReservationResponse;
import com.hairsalonproject2.reservation.service.ReservationDummySeeder;
import com.hairsalonproject2.reservation.service.ReservationService;
import com.hairsalonproject2.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Locale;

@Controller
@RequestMapping("/admin/reservations")
@RequiredArgsConstructor
public class AdminReservationController {

    private static final int PAGE_SIZE = 10;

    private final ReservationService reservationService;
    private final ReservationDummySeeder reservationDummySeeder;
    private final ReviewRepository reviewRepository;

    @GetMapping
    public String reservationList(@RequestParam(required = false) String keyword,
                                  @RequestParam(required = false) ReservationStatus status,
                                  @RequestParam(defaultValue = "1") int page,
                                  Model model) {
        List<ReservationResponse> allReservations = reservationService.getAllReservations();
        List<ReservationResponse> filteredReservations = allReservations.stream()
                .filter(reservation -> status == null || reservation.getStatus() == status)
                .filter(reservation -> matchesKeyword(reservation, keyword))
                .toList();
        Page<ReservationResponse> reservationPage = AdminPaginationUtils.slice(filteredReservations, page, PAGE_SIZE);

        model.addAttribute("reservationPage", reservationPage);
        model.addAttribute("reservations", reservationPage.getContent());
        model.addAttribute("pageNumbers", AdminPaginationUtils.getPageNumbers(reservationPage));
        model.addAttribute("currentPage", reservationPage.getNumber() + 1);
        model.addAttribute("previousGroupPage", AdminPaginationUtils.getPreviousGroupPage(reservationPage));
        model.addAttribute("nextGroupPage", AdminPaginationUtils.getNextGroupPage(reservationPage));
        model.addAttribute("keyword", keyword);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("statuses", ReservationStatus.values());
        model.addAttribute("currentMenu", "reservations");
        model.addAttribute("reservationCount", allReservations.size());
        model.addAttribute("completedReservationCount", allReservations.stream().filter(r -> r.getStatus() == ReservationStatus.COMPLETED).count());
        model.addAttribute("reservedReservationCount", allReservations.stream().filter(r -> r.getStatus() == ReservationStatus.RESERVED).count());
        model.addAttribute("cancelledReservationCount", allReservations.stream().filter(r -> r.getStatus() == ReservationStatus.CANCELLED).count());
        model.addAttribute("reviewCount", reviewRepository.count());
        return "admin/reservations";
    }

    @PostMapping("/seed")
    public String seedReservations(RedirectAttributes redirectAttributes) {
        int created = reservationDummySeeder.seedReservations();
        if (created > 0) {
            redirectAttributes.addFlashAttribute("successMessage", created + "개의 예약 더미를 생성했습니다.");
        } else {
            redirectAttributes.addFlashAttribute("successMessage", "추가로 생성할 예약 더미가 없습니다.");
        }
        return "redirect:/admin/reservations";
    }

    private boolean matchesKeyword(ReservationResponse reservation, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }

        String normalized = keyword.trim().toLowerCase(Locale.ROOT);
        return contains(reservation.getMemberId(), normalized)
                || contains(reservation.getDesignerName(), normalized)
                || contains(reservation.getServiceName(), normalized)
                || contains(String.valueOf(reservation.getReservationId()), normalized);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }
}
