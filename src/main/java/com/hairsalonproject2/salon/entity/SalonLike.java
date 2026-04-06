package com.hairsalonproject2.salon.entity;

import com.hairsalonproject2.common.entity.BaseCreatedEntity;
import com.hairsalonproject2.member.entity.Member;
import jakarta.persistence.*;
import lombok.*;

/**
 * SalonLike
 *
 * salon_like 테이블과 매핑되는 엔티티
 *
 * 변경 포인트
 * 1. 기존 String memberId → Member member 연관관계로 변경
 * 2. member_id FK를 엔티티에서도 자연스럽게 사용하도록 수정
 * 3. created_at 은 BaseCreatedEntity 로 공통 처리
 */
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
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
}