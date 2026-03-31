package com.hairsalonproject2.salon.entity;

import com.hairsalonproject2.common.entity.BaseCreatedEntity;
import com.hairsalonproject2.member.entity.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * SalonLike
 *
 * 회원이 미용실에 누른 좋아요(찜) 정보 엔티티
 *
 * 특징
 * 1. member + salon 조합은 DB에서 UNIQUE로 관리
 * 2. member / salon 양쪽에서 조회 가능
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "salon_like",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_salon_like",
                        columnNames = {"member_id", "salon_id"}
                )
        }
)
public class SalonLike extends BaseCreatedEntity {

    /**
     * 좋아요 PK
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "like_id")
    private Integer likeId;

    /**
     * 좋아요를 누른 회원
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    /**
     * 좋아요 대상 미용실
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "salon_id", nullable = false)
    private Salon salon;

    @Builder
    public SalonLike(Member member, Salon salon) {
        this.member = member;
        this.salon = salon;
    }
}