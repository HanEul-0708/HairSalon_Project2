package com.hairsalonproject2.salon.repository;

import com.hairsalonproject2.salon.entity.SalonLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * SalonLikeRepository
 *
 * 미용실 좋아요(찜) 관련 Repository
 *
 * 변경 포인트
 * 1. SalonLike 엔티티의 memberId(String) → Member member 로 변경됨
 * 2. 따라서 파생 메서드도 member.memberId 기준으로 수정
 */
public interface SalonLikeRepository extends JpaRepository<SalonLike, Integer> {

    /**
     * 특정 회원이 특정 미용실에 누른 좋아요 조회
     */
    Optional<SalonLike> findByMember_MemberIdAndSalon_SalonId(String memberId, Integer salonId);

    /**
     * 특정 미용실의 좋아요 수 조회
     */
    long countBySalon_SalonId(Integer salonId);
}