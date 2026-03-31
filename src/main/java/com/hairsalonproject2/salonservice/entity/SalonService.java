package com.hairsalonproject2.salonservice.entity;

import com.hairsalonproject2.common.entity.BaseCreatedEntity;
import com.hairsalonproject2.reservation.entity.Reservation;
import com.hairsalonproject2.salon.entity.Salon;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * SalonService
 *
 * salon_service 테이블과 매핑되는 엔티티
 *
 * 주의🔥
 * - 클래스 이름을 Service로 하면 Spring의 service 패키지와 충돌
 * - 그래서 SalonService라는 이름 사용
 *
 * 역할
 * 1. 미용실별 시술 메뉴 저장
 * 2. 가격 / 소요시간 관리
 * 3. 예약과 연결
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "salon_service")
public class SalonService extends BaseCreatedEntity {

    /**
     * 시술 PK
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "service_id")
    private Integer serviceId;

    /**
     * 소속 미용실
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "salon_id", nullable = false)
    private Salon salon;

    /**
     * 시술 이름
     */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /**
     * 가격
     */
    @Column(name = "price", nullable = false)
    private Integer price;

    /**
     * 소요 시간 (분 단위)
     */
    @Column(name = "duration", nullable = false)
    private Integer duration;

    /**
     * 시술 설명
     */
    @Lob
    @Column(name = "description")
    private String description;

    // ==============================
    // 연관관계
    // ==============================

    /**
     * 이 시술로 생성된 예약 목록
     */
    @OneToMany(mappedBy = "salonService")
    private List<Reservation> reservations = new ArrayList<>();

    // ==============================
    // 생성자
    // ==============================

    @Builder
    public SalonService(Salon salon, String name, Integer price,
                        Integer duration, String description) {
        this.salon = salon;
        this.name = name;
        this.price = price;
        this.duration = duration;
        this.description = description;
    }

    // ==============================
    // 비즈니스 메서드
    // ==============================

    /**
     * 시술 정보 수정
     */
    public void updateServiceInfo(Salon salon, String name,
                                  Integer price, Integer duration, String description) {
        this.salon = salon;
        this.name = name;
        this.price = price;
        this.duration = duration;
        this.description = description;
    }
}