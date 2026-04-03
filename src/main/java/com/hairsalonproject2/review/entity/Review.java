package com.hairsalonproject2.review.entity;

import com.hairsalonproject2.common.entity.BaseCreatedEntity;
import com.hairsalonproject2.designer.entity.Designer;
import com.hairsalonproject2.member.entity.Member;
import com.hairsalonproject2.reservation.entity.Reservation;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Review
 *
 * review 테이블과 매핑되는 엔티티
 *
 * 특징
 * 1. 예약 1건당 리뷰 1개
 * 2. 디자이너 답글 가능
 * 3. 리뷰 이미지 여러 개 연결 가능
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "review")
public class Review extends BaseCreatedEntity {

    /**
     * 리뷰 PK
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_id")
    private Integer reviewId;

    /**
     * 어떤 예약에 대한 리뷰인지
     *
     * reservation_id 가 UNIQUE 이므로 1:1 매핑
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false, unique = true)
    private Reservation reservation;

    /**
     * 리뷰 작성 회원
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    /**
     * 리뷰 대상 디자이너
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "designer_id", nullable = false)
    private Designer designer;

    /**
     * 평점 (1~5)
     *
     * DB는 TINYINT 이므로 Byte 사용
     */
    @Column(name = "rating", nullable = false)
    private Byte rating;

    /**
     * 리뷰 내용
     */
    @Lob
    @Column(name = "content")
    private String content;

    /**
     * 디자이너 답글 내용
     */
    @Lob
    @Column(name = "reply_content")
    private String replyContent;

    /**
     * 답글 작성일시
     */
    @Column(name = "reply_created_at")
    private LocalDateTime replyCreatedAt;

    // ==============================
    // 연관관계
    // ==============================

    /**
     * 리뷰 이미지 목록
     */
    @OneToMany(mappedBy = "review",
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    private List<ReviewImage> reviewImages = new ArrayList<>();

    // ==============================
    // 생성자
    // ==============================

    @Builder
    public Review(Reservation reservation, Member member, Designer designer,
                  Byte rating, String content,
                  String replyContent, LocalDateTime replyCreatedAt) {
        this.reservation = reservation;
        this.member = member;
        this.designer = designer;
        this.rating = rating;
        this.content = content;
        this.replyContent = replyContent;
        this.replyCreatedAt = replyCreatedAt;
    }

    // ==============================
    // 비즈니스 메서드
    // ==============================

    /**
     * 리뷰 수정
     */
    public void updateReview(Byte rating, String content) {
        this.rating = rating;
        this.content = content;
    }

    /**
     * 디자이너 답글 등록/수정
     */
    public void updateReply(String replyContent, LocalDateTime replyCreatedAt) {
        this.replyContent = replyContent;
        this.replyCreatedAt = replyCreatedAt;
    }

    /**
     * 리뷰 이미지 추가
     *
     * 양방향 연관관계 유지
     */
    public void addReviewImage(ReviewImage reviewImage) {
        this.reviewImages.add(reviewImage);
        reviewImage.changeReview(this);
    }
}