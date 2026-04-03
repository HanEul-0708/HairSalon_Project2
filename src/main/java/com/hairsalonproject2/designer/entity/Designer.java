package com.hairsalonproject2.designer.entity;

import com.hairsalonproject2.member.entity.Member;
import com.hairsalonproject2.salon.entity.Salon;
import com.hairsalonproject2.common.entity.BaseCreatedEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * Designer
 *
 * designer 테이블과 매핑되는 엔티티
 *
 * 변경 포인트
 * 1. 기존 String memberId → Member member 연관관계로 변경
 * 2. member 테이블과 실제 FK 관계를 엔티티에도 반영
 * 3. created_at 은 BaseCreatedEntity 로 공통 처리
 */
@Entity
@Table(name = "designer")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "salon_id", nullable = false)
    private Salon salon;

    /**
     * 연결된 회원 계정
     *
     * DB에서 member_id 는 UNIQUE 이므로
     * 디자이너와 회원은 1:1 관계로 본다.
     *
     * nullable 허용:
     * 아직 회원 계정과 연결되지 않은 디자이너일 수 있음
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", unique = true)
    private Member member;

    /**
     * 디자이너 이름
     */
    @Column(nullable = false, length = 50)
    private String name;

    /**
     * 프로필 이미지 경로
     */
    @Column(name = "profile_image", length = 255)
    private String profileImage;

    /**
     * 소개글
     */
    @Column(columnDefinition = "TEXT")
    private String introduction;

    /**
     * 경력 연차
     */
    @Column(name = "career_years", nullable = false)
    @Builder.Default
    private Integer careerYears = 0;
}