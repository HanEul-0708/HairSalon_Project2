package com.hairsalonproject2.review.entity;

import com.hairsalonproject2.common.entity.BaseCreatedEntity;
import com.hairsalonproject2.member.entity.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "review_like",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_review_like_review_member", columnNames = {"review_id", "member_id"}),
                @UniqueConstraint(name = "uk_review_like_review_visitor", columnNames = {"review_id", "visitor_token"})
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewLike extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_like_id")
    private Integer reviewLikeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id", nullable = false)
    private Review review;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @Column(name = "visitor_token", length = 64)
    private String visitorToken;

    @Builder
    public ReviewLike(Review review, Member member, String visitorToken) {
        this.review = review;
        this.member = member;
        this.visitorToken = visitorToken;
    }
}
