package com.hairsalonproject2.designer.entity;

import com.hairsalonproject2.common.entity.BaseCreatedEntity;
import com.hairsalonproject2.member.entity.Member;
import com.hairsalonproject2.reservation.entity.Reservation;
import com.hairsalonproject2.reservation.entity.ReservationSlot;
import com.hairsalonproject2.review.entity.Review;
import com.hairsalonproject2.salon.entity.Salon;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Designer
 *
 * designer 테이블과 매핑되는 엔티티
 *
 * 주요 역할
 * 1. 미용실(salon)에 소속되는 디자이너 정보 저장
 * 2. member 계정과 1:1 연결 가능
 * 3. 예약 / 리뷰 / 예약 슬롯과 연결
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "designer")
public class Designer extends BaseCreatedEntity {

    /**
     * 디자이너 PK
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "designer_id")
    private Integer designerId;

    /**
     * 소속 미용실
     *
     * designer.salon_id -> salon.salon_id
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "salon_id", nullable = false)
    private Salon salon;

    /**
     * 연결된 회원 계정
     *
     * designer.member_id -> member.member_id
     *
     * DB에서 member_id는 UNIQUE 이므로 1:1 관계로 매핑한다.
     * 값이 없을 수도 있으므로 nullable 허용 상태다.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", unique = true)
    private Member member;

    /**
     * 디자이너 이름
     */
    @Column(name = "name", nullable = false, length = 50)
    private String name;

    /**
     * 프로필 이미지 경로
     */
    @Column(name = "profile_image", length = 255)
    private String profileImage;

    /**
     * 소개글
     */
    @Lob
    @Column(name = "introduction")
    private String introduction;

    /**
     * 경력 연수
     */
    @Column(name = "career_years")
    private Integer careerYears;

    // ==============================
    // 연관관계
    // ==============================

    /**
     * 디자이너가 받은 예약 목록
     */
    @OneToMany(mappedBy = "designer")
    private List<Reservation> reservations = new ArrayList<>();

    /**
     * 디자이너 예약 슬롯 목록
     */
    @OneToMany(mappedBy = "designer")
    private List<ReservationSlot> reservationSlots = new ArrayList<>();

    /**
     * 디자이너가 대상인 리뷰 목록
     */
    @OneToMany(mappedBy = "designer")
    private List<Review> reviews = new ArrayList<>();

    // ==============================
    // 생성자
    // ==============================

    @Builder
    public Designer(Salon salon, Member member, String name,
                    String profileImage, String introduction, Integer careerYears) {
        this.salon = salon;
        this.member = member;
        this.name = name;
        this.profileImage = profileImage;
        this.introduction = introduction;
        this.careerYears = careerYears;
    }

    // ==============================
    // 비즈니스 메서드
    // ==============================

    /**
     * 디자이너 기본 정보 수정
     */
    public void updateDesignerInfo(Salon salon, String name, String profileImage,
                                   String introduction, Integer careerYears) {
        this.salon = salon;
        this.name = name;
        this.profileImage = profileImage;
        this.introduction = introduction;
        this.careerYears = careerYears;
    }

    /**
     * 연결된 회원 계정 변경
     */
    public void changeMember(Member member) {
        this.member = member;
    }
}