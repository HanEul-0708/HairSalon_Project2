package com.hairsalonproject2.salon.entity;

import com.hairsalonproject2.common.entity.BaseTimeEntity;
import com.hairsalonproject2.designer.entity.Designer;
import com.hairsalonproject2.salonservice.entity.SalonService;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Salon
 *
 * salon 테이블과 매핑되는 엔티티
 *
 * 주요 역할
 * 1. 미용실 기본 정보 저장
 * 2. 외부 API 연동용 식별값 저장
 * 3. 디자이너 / 시술 / 좋아요와 연결
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "salon",
        uniqueConstraints = {
                /**
                 * source_type + external_id 조합 유니크
                 *
                 * 예:
                 * - KAKAO + 12345
                 * - NAVER + 12345
                 *
                 * 서로 다른 출처면 같은 external_id가 있어도 구분 가능하도록
                 * 복합 유니크로 설정한다.
                 */
                @UniqueConstraint(
                        name = "uq_salon_source_external",
                        columnNames = {"source_type", "external_id"}
                )
        }
)
public class Salon extends BaseTimeEntity {

    /**
     * 미용실 PK
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "salon_id")
    private Integer salonId;

    /**
     * 외부 API 식별값
     */
    @Column(name = "external_id", length = 100)
    private String externalId;

    /**
     * 외부 API 출처
     * 예: KAKAO, NAVER
     */
    @Column(name = "source_type", length = 30)
    private String sourceType;

    /**
     * 미용실 이름
     */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /**
     * 기본 주소
     */
    @Column(name = "address", nullable = false, length = 255)
    private String address;

    /**
     * 도로명 주소
     */
    @Column(name = "road_address", length = 255)
    private String roadAddress;

    /**
     * 전화번호
     */
    @Column(name = "phone", length = 20)
    private String phone;

    /**
     * 미용실 소개
     */
    @Lob
    @Column(name = "description")
    private String description;

    /**
     * 위도
     *
     * DB:
     * DECIMAL(10,7)
     */
    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    /**
     * 경도
     *
     * DB:
     * DECIMAL(10,7)
     */
    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    /**
     * 대표 이미지 경로
     */
    @Column(name = "image_url", length = 255)
    private String imageUrl;

    /**
     * 외부 원본 페이지 URL
     */
    @Column(name = "place_url", length = 255)
    private String placeUrl;

    /**
     * 예약 가능 여부
     *
     * MySQL TINYINT(1) -> Java Boolean
     */
    @Column(name = "reservable")
    private Boolean reservable;

    /**
     * 평균 평점 캐시
     *
     * DB:
     * DECIMAL(3,2)
     */
    @Column(name = "average_rating", precision = 3, scale = 2)
    private BigDecimal averageRating;

    /**
     * 리뷰 수 캐시
     */
    @Column(name = "review_count")
    private Integer reviewCount;

    /**
     * 좋아요 수 캐시
     */
    @Column(name = "like_count")
    private Integer likeCount;

    // ==============================
    // 연관관계
    // ==============================

    /**
     * 소속 디자이너 목록
     */
    @OneToMany(mappedBy = "salon")
    private List<Designer> designers = new ArrayList<>();

    /**
     * 미용실 시술 목록
     */
    @OneToMany(mappedBy = "salon")
    private List<SalonService> services = new ArrayList<>();

    /**
     * 미용실 좋아요 목록
     */
    @OneToMany(mappedBy = "salon")
    private List<SalonLike> salonLikes = new ArrayList<>();

    // ==============================
    // 생성자
    // ==============================

    @Builder
    public Salon(String externalId, String sourceType, String name, String address,
                 String roadAddress, String phone, String description,
                 BigDecimal latitude, BigDecimal longitude,
                 String imageUrl, String placeUrl, Boolean reservable,
                 BigDecimal averageRating, Integer reviewCount, Integer likeCount) {
        this.externalId = externalId;
        this.sourceType = sourceType;
        this.name = name;
        this.address = address;
        this.roadAddress = roadAddress;
        this.phone = phone;
        this.description = description;
        this.latitude = latitude;
        this.longitude = longitude;
        this.imageUrl = imageUrl;
        this.placeUrl = placeUrl;
        this.reservable = reservable;
        this.averageRating = averageRating;
        this.reviewCount = reviewCount;
        this.likeCount = likeCount;
    }

    // ==============================
    // 비즈니스 메서드
    // ==============================

    /**
     * 미용실 기본 정보 수정
     */
    public void updateSalonInfo(String name, String address, String roadAddress,
                                String phone, String description,
                                String imageUrl, String placeUrl, Boolean reservable) {
        this.name = name;
        this.address = address;
        this.roadAddress = roadAddress;
        this.phone = phone;
        this.description = description;
        this.imageUrl = imageUrl;
        this.placeUrl = placeUrl;
        this.reservable = reservable;
    }

    /**
     * 평균 평점 / 리뷰 수 캐시 갱신
     */
    public void updateRatingInfo(BigDecimal averageRating, Integer reviewCount) {
        this.averageRating = averageRating;
        this.reviewCount = reviewCount;
    }

    /**
     * 좋아요 수 캐시 갱신
     */
    public void updateLikeCount(Integer likeCount) {
        this.likeCount = likeCount;
    }
}