package com.hairsalonproject2.reservation.controller;

import com.hairsalonproject2.designer.dto.request.DesignerSearchRequest;
import com.hairsalonproject2.designer.service.DesignerQueryService;
import com.hairsalonproject2.member.service.CustomUserDetails;
import com.hairsalonproject2.reservation.dto.ReservationResponse;
import com.hairsalonproject2.reservation.service.ReservationService;
import com.hairsalonproject2.review.repository.ReviewRepository;
import com.hairsalonproject2.salonservice.service.SalonServiceQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class ReservationPageController {

    private final DesignerQueryService designerQueryService;
    private final SalonServiceQueryService salonServiceQueryService;
    private final ReservationService reservationService;
    private final ReviewRepository reviewRepository;

    @GetMapping("/reservations")
    public String reservationList(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        // 로그인한 사용자의 예약만 조회해서 "내 예약 목록" 화면에 보여준다.
        List<ReservationResponse> reservations =
                reservationService.getMyReservations(userDetails.getMember().getMemberId());

        List<Integer> reservationIds = reservations.stream()
                .map(ReservationResponse::getReservationId)
                .toList();

        // 이미 리뷰를 작성한 예약 ID를 미리 계산해 두면 템플릿에서 버튼 분기가 쉬워진다.
        Set<Integer> reviewedReservationIds = reservationIds.isEmpty()
                ? Collections.emptySet()
                : reviewRepository.findByReservation_ReservationIdIn(reservationIds).stream()
                .map(review -> review.getReservation().getReservationId())
                .collect(Collectors.toSet());

        model.addAttribute("reservations", reservations);
        model.addAttribute("reviewedReservationIds", reviewedReservationIds);
        return "reservation/list";
    }

    @GetMapping("/reservations/new")
    public String reservationCreate(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        // 예약 생성 화면에서 바로 선택할 수 있도록 디자이너/시술 목록을 내려준다.
        model.addAttribute("designers", designerQueryService.search(new DesignerSearchRequest()));
        model.addAttribute("services", salonServiceQueryService.list(null));
        model.addAttribute("currentMemberId", userDetails.getMember().getMemberId());
        return "reservation/create";
    }
}
