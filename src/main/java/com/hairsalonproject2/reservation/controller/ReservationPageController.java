package com.hairsalonproject2.reservation.controller;

import com.hairsalonproject2.common.constant.PaymentMethod;
import com.hairsalonproject2.common.constant.ReservationStatus;
import com.hairsalonproject2.designer.dto.request.DesignerSearchRequest;
import com.hairsalonproject2.designer.dto.response.DesignerSummaryResponse;
import com.hairsalonproject2.designer.service.DesignerQueryService;
import com.hairsalonproject2.member.service.CustomUserDetails;
import com.hairsalonproject2.reservation.dto.ReservationResponse;
import com.hairsalonproject2.salon.dto.request.SalonSearchRequest;
import com.hairsalonproject2.salon.dto.response.SalonSummaryResponse;
import com.hairsalonproject2.reservation.service.ReservationService;
import com.hairsalonproject2.reservation.service.ReservationServiceImpl;
import com.hairsalonproject2.review.repository.ReviewRepository;
import com.hairsalonproject2.salon.dto.response.SalonDetailResponse;
import com.hairsalonproject2.salon.service.SalonQueryService;
import com.hairsalonproject2.salonservice.dto.response.SalonServiceSummaryResponse;
import com.hairsalonproject2.salonservice.service.SalonServiceQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class ReservationPageController {

    private static final int RESERVATIONS_PER_PAGE = 5;

    private final DesignerQueryService designerQueryService;
    private final SalonServiceQueryService salonServiceQueryService;
    private final SalonQueryService salonQueryService;
    private final ReservationService reservationService;
    private final ReservationServiceImpl reservationServiceImpl;
    private final ReviewRepository reviewRepository;

    @GetMapping("/reservations")
    public String reservationList(@AuthenticationPrincipal CustomUserDetails userDetails,
                                  @RequestParam(defaultValue = "1") int page,
                                  Model model) {
        List<ReservationResponse> reservations =
                reservationService.getMyReservations(userDetails.getMember().getMemberId()).stream()
                        .sorted(Comparator.comparing(ReservationResponse::getCreatedAt).reversed())
                        .toList();

        int totalReservations = reservations.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalReservations / RESERVATIONS_PER_PAGE));
        int currentPage = Math.min(Math.max(page, 1), totalPages);
        int fromIndex = (currentPage - 1) * RESERVATIONS_PER_PAGE;
        int toIndex = Math.min(fromIndex + RESERVATIONS_PER_PAGE, totalReservations);
        List<ReservationResponse> pagedReservations = totalReservations == 0
                ? Collections.emptyList()
                : reservations.subList(fromIndex, toIndex);

        List<Integer> reservationIds = pagedReservations.stream()
                .map(ReservationResponse::getReservationId)
                .toList();

        Set<Integer> reviewedReservationIds = reservationIds.isEmpty()
                ? Collections.emptySet()
                : reviewRepository.findByReservation_ReservationIdIn(reservationIds).stream()
                .map(review -> review.getReservation().getReservationId())
                .collect(Collectors.toSet());

        LocalDateTime now = LocalDateTime.now();
        Map<Integer, Boolean> reviewableReservationMap = pagedReservations.stream()
                .collect(Collectors.toMap(
                        ReservationResponse::getReservationId,
                        reservation -> isReviewableReservation(reservation, now)
                ));

        model.addAttribute("reservations", pagedReservations);
        model.addAttribute("reviewedReservationIds", reviewedReservationIds);
        model.addAttribute("reviewableReservationMap", reviewableReservationMap);
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageNumbers", java.util.stream.IntStream.rangeClosed(1, totalPages).boxed().toList());
        return "reservation/list";
    }

    @GetMapping("/reservations/new")
    public String reservationCreate(@AuthenticationPrincipal CustomUserDetails userDetails,
                                    @RequestParam(required = false) Integer salonId,
                                    Model model) {
        List<DesignerSummaryResponse> designers = designerQueryService.search(new DesignerSearchRequest());
        List<SalonServiceSummaryResponse> services = salonServiceQueryService.list(null);
        List<SalonSummaryResponse> salons = salonQueryService.search(new SalonSearchRequest());
        List<Integer> likedSalonIds = salonQueryService.getLikedSalonIds(userDetails.getMember().getMemberId());
        List<Integer> likedDesignerIds = designerQueryService.getLikedDesignerIds(userDetails.getMember().getMemberId());

        SalonDetailResponse selectedSalon = null;
        if (salonId != null) {
            selectedSalon = salonQueryService.getDetail(salonId);
        }

        model.addAttribute("selectedSalon", selectedSalon);
        model.addAttribute("selectedSalonId", salonId);
        model.addAttribute("currentMemberId", userDetails.getMember().getMemberId());
        model.addAttribute("salons", salons);
        model.addAttribute("designers", designers);
        model.addAttribute("services", services);
        model.addAttribute("likedSalonIds", likedSalonIds);
        model.addAttribute("likedDesignerIds", likedDesignerIds);
        model.addAttribute("paymentMethods", PaymentMethod.values());
        return "reservation/create";
    }

    @GetMapping("/reservations/{reservationId}/edit")
    public String reservationEdit(@AuthenticationPrincipal CustomUserDetails userDetails,
                                  @PathVariable Integer reservationId,
                                  Model model) {
        reservationServiceImpl.validateReservationAccess(
                reservationId,
                userDetails.getMember().getMemberId(),
                userDetails.getMember().getRole().name().equals("ADMIN")
        );

        model.addAttribute("reservation", reservationService.getReservation(reservationId));
        model.addAttribute("designers", designerQueryService.search(new DesignerSearchRequest()));
        model.addAttribute("services", salonServiceQueryService.list(null));
        model.addAttribute("paymentMethods", PaymentMethod.values());
        return "reservation/edit";
    }

    private boolean isReviewableReservation(ReservationResponse reservation, LocalDateTime now) {
        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            return false;
        }

        return !java.time.LocalDateTime.of(
                reservation.getReservationDate(),
                reservation.getReservationTime()
        ).isAfter(now);
    }
}
