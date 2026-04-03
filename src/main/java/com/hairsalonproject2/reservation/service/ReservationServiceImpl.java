package com.hairsalonproject2.reservation.service;

import com.hairsalonproject2.common.constant.ReservationStatus;
import com.hairsalonproject2.designer.entity.Designer;
import com.hairsalonproject2.designer.repository.DesignerRepository;
import com.hairsalonproject2.member.entity.Member;
import com.hairsalonproject2.member.repository.MemberRepository;
import com.hairsalonproject2.reservation.dto.ReservationCreateRequest;
import com.hairsalonproject2.reservation.dto.ReservationResponse;
import com.hairsalonproject2.reservation.entity.Reservation;
import com.hairsalonproject2.reservation.entity.ReservationSlot;
import com.hairsalonproject2.reservation.repository.ReservationRepository;
import com.hairsalonproject2.reservation.repository.ReservationSlotRepository;
import com.hairsalonproject2.salonservice.entity.SalonService;
import com.hairsalonproject2.salonservice.repository.SalonServiceRepository;
import com.hairsalonproject2.reservation.dto.ReservationStatusUpdateRequest;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationServiceImpl implements ReservationService {

    /**
     * 예약 정보 DB 처리용 Repository
     */
    private final ReservationRepository reservationRepository;

    /**
     * 예약 슬롯 DB 처리용 Repository
     *
     * 같은 디자이너 / 같은 날짜 / 같은 시간에
     * 이미 예약이 있는지 확인할 때 사용
     */
    private final ReservationSlotRepository reservationSlotRepository;

    /**
     * 회원 조회용 Repository
     */
    private final MemberRepository memberRepository;

    /**
     * 디자이너 조회용 Repository
     */
    private final DesignerRepository designerRepository;

    /**
     * 시술 조회용 Repository
     */
    private final SalonServiceRepository salonServiceRepository;

    /**
     * 예약 생성
     *
     * [기존 방식]
     * - Reservation 엔티티를 통째로 받음
     *
     * [수정 방식]
     * - ReservationCreateRequest DTO를 받음
     * - 필요한 엔티티(Member, Designer, SalonService)를 조회해서
     *   Reservation 엔티티를 서버 내부에서 직접 생성함
     *
     * 이렇게 바꾸는 이유
     * 1. 엔티티를 외부에 직접 노출하지 않기 위해
     * 2. 요청값을 더 안전하게 관리하기 위해
     * 3. 리뷰 쪽 DTO 구조와 통일하기 위해
     */
    @Override
    @Transactional
    public ReservationResponse createReservation(ReservationCreateRequest request) {

        /**
         * 1. 요청으로 받은 memberId를 이용해서 회원 조회
         *
         * memberId가 DB에 없으면 예외 발생
         */
        Member member = memberRepository.findById(request.getMemberId())
                .orElseThrow(() -> new IllegalArgumentException("회원이 존재하지 않습니다."));

        /**
         * 2. 요청으로 받은 designerId를 이용해서 디자이너 조회
         */
        Designer designer = designerRepository.findById(request.getDesignerId())
                .orElseThrow(() -> new IllegalArgumentException("디자이너가 존재하지 않습니다."));

        /**
         * 3. 요청으로 받은 salonServiceId를 이용해서 시술 조회
         */
        SalonService salonService = salonServiceRepository.findById(request.getSalonServiceId())
                .orElseThrow(() -> new IllegalArgumentException("시술이 존재하지 않습니다."));

        /**
         * 4. 같은 디자이너 / 같은 날짜 / 같은 시간에
         * 이미 예약이 있는지 확인
         *
         * 있으면 중복 예약이므로 예외 발생
         */
        boolean exists = reservationSlotRepository.existsByDesigner_DesignerIdAndReservationDateAndSlotTime(
                designer.getDesignerId(),
                request.getReservationDate(),
                request.getReservationTime()
        );

        if (exists) {
            throw new IllegalArgumentException("이미 예약된 시간입니다.");
        }

        /**
         * 5. Reservation 엔티티 생성
         *
         * 여기서 중요한 점:
         * 클라이언트가 엔티티를 직접 보내는 게 아니라
         * 서버가 DTO 값을 바탕으로 엔티티를 직접 만들어줌
         */
        Reservation reservation = Reservation.builder()
                .member(member)
                .designer(designer)
                .salonService(salonService)
                .reservationDate(request.getReservationDate())
                .reservationTime(request.getReservationTime())
                .status(ReservationStatus.RESERVED) // 예약 생성 시 기본 상태
                .totalPrice(request.getTotalPrice())
                .build();

        /**
         * 6. 예약 저장
         */
        Reservation savedReservation = reservationRepository.save(reservation);

        /**
         * 7. 예약 슬롯 생성
         *
         * reservation_slot 테이블은
         * "이 시간은 이미 예약되었다"는 걸 관리하는 역할
         */
        ReservationSlot slot = ReservationSlot.builder()
                .reservation(savedReservation)
                .designer(savedReservation.getDesigner())
                .reservationDate(savedReservation.getReservationDate())
                .slotTime(savedReservation.getReservationTime())
                .build();

        /**
         * 8. 양방향 연관관계 연결
         *
         * Reservation 엔티티 안의 addReservationSlot() 메서드를 호출해서
         * 예약 <-> 슬롯 관계를 맞춰줌
         */
        savedReservation.addReservationSlot(slot);

        /**
         * 9. 슬롯 저장
         */
        reservationSlotRepository.save(slot);

        /**
         * 10. 엔티티를 그대로 반환하지 않고
         * 응답용 DTO로 변환해서 반환
         */
        return toResponse(savedReservation);
    }

    /**
     * 예약 1건 조회
     *
     * reservationId로 예약을 찾고
     * ReservationResponse DTO로 변환해서 반환
     */
    @Override
    public ReservationResponse getReservation(Integer reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("해당 예약이 존재하지 않습니다."));

        return toResponse(reservation);
    }

    /**
     * 전체 예약 목록 조회
     *
     * Reservation 엔티티 리스트를 그대로 반환하지 않고
     * ReservationResponse 리스트로 바꿔서 반환
     */
    @Override
    public List<ReservationResponse> getAllReservations() {
        return reservationRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * 특정 회원의 예약 목록 조회
     *
     * memberId로 예약 목록을 찾고
     * DTO 리스트로 변환해서 반환
     */
    @Override
    public List<ReservationResponse> getReservationsByMember(String memberId) {
        return reservationRepository.findByMember_MemberId(memberId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * 내 예약 목록 조회
     *
     * 현재는 로그인 연동 전이라
     * memberId를 받아서 해당 회원의 예약 목록을 조회한다.
     *
     * 나중에 Spring Security / 세션 로그인 붙으면
     * 로그인한 사용자 ID를 꺼내서 자동 조회하도록 바꾸면 된다.
     */
    @Override
    public List<ReservationResponse> getMyReservations(String memberId) {
        return reservationRepository.findByMember_MemberId(memberId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * 예약 취소
     *
     * 현재는 예약 상태만 CANCELLED로 변경
     *
     * 나중에 더 다듬을 부분:
     * - 취소 시 reservation_slot도 삭제할지
     * - 또는 중복 예약 체크에서 CANCELLED는 제외할지
     */
    @Override
    @Transactional
    public void cancelReservation(Integer reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("해당 예약이 존재하지 않습니다."));

        // 이미 취소된 예약이면 다시 취소하지 않도록 막기
        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new IllegalArgumentException("이미 취소된 예약입니다.");
        }

        // 1. 예약 상태를 취소로 변경
        reservation.changeStatus(ReservationStatus.CANCELLED);
        reservationRepository.save(reservation);

        // 2. 예약에 연결된 슬롯 삭제
        reservationSlotRepository.deleteByReservation_ReservationId(reservationId);
    }

    /**
     * Reservation 엔티티 -> ReservationResponse DTO 변환 메서드
     *
     * 왜 필요하냐?
     * - 엔티티를 그대로 반환하지 않기 위해
     * - 필요한 값만 골라서 응답으로 보내기 위해
     * - API 응답 구조를 깔끔하게 유지하기 위해
     */
    private ReservationResponse toResponse(Reservation reservation) {

        return ReservationResponse.builder()
                .reservationId(reservation.getReservationId())
                .memberId(reservation.getMember().getMemberId())
                .designerId(reservation.getDesigner().getDesignerId())
                .designerName(reservation.getDesigner().getName())
                .salonServiceId(reservation.getSalonService().getServiceId())
                .serviceName(reservation.getSalonService().getName())
                .reservationDate(reservation.getReservationDate())
                .reservationTime(reservation.getReservationTime())
                .status(reservation.getStatus())
                .totalPrice(reservation.getTotalPrice())
                .createdAt(reservation.getCreatedAt())
                .updatedAt(reservation.getUpdatedAt())

                .build();
    }

    /**
     * 예약 상태 변경
     *
     * 예:
     * RESERVED -> COMPLETED
     * RESERVED -> CANCELLED
     *
     * 처리 순서
     * 1. reservationId로 예약 조회
     * 2. 요청 DTO에서 변경할 상태값 확인
     * 3. 예약 상태 변경
     * 4. 저장 후 응답 DTO 반환
     */
    @Override
    @Transactional
    public ReservationResponse updateReservationStatus(
            Integer reservationId,
            ReservationStatusUpdateRequest request
    ) {
        /**
         * 예약 조회
         * 없으면 예외 발생
         */
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("해당 예약이 존재하지 않습니다."));

        /**
         * 상태 변경
         *
         * Reservation 엔티티 안에 있는
         * changeStatus() 메서드를 사용한다.
         */
        reservation.changeStatus(request.getStatus());

        /**
         * 변경된 예약 저장
         */
        Reservation updatedReservation = reservationRepository.save(reservation);

        /**
         * 응답 DTO로 변환해서 반환
         */
        return toResponse(updatedReservation);
    }
}