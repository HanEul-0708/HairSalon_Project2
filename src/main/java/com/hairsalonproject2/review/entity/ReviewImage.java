package com.hairsalonproject2.review.entity;

import com.hairsalonproject2.common.entity.BaseCreatedEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * ReviewImage
 *
 * review_image 테이블과 매핑되는 엔티티
 *
 * 역할
 * - 리뷰에 첨부된 이미지 저장
 * - 리뷰 1개에 여러 이미지 연결 가능
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "review_image")
public class ReviewImage extends BaseCreatedEntity {

    /**
     * 이미지 PK
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "image_id")
    private Integer imageId;

    /**
     * 소속 리뷰
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id", nullable = false)
    private Review review;

    /**
     * 이미지 URL
     */
    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    /**
     * 출력 순서
     */
    @Column(name = "sort_order")
    private Integer sortOrder;

    // ==============================
    // 생성자
    // ==============================

    @Builder
    public ReviewImage(Review review, String imageUrl, Integer sortOrder) {
        this.review = review;
        this.imageUrl = imageUrl;
        this.sortOrder = sortOrder;
    }

    // ==============================
    // 연관관계 메서드
    // ==============================

    /**
     * 리뷰 연관관계 설정/변경
     */
    public void changeReview(Review review) {
        this.review = review;
    }
}