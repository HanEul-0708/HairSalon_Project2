package com.hairsalonproject2.salon.repository;

import com.hairsalonproject2.salon.entity.SalonLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SalonLikeRepository extends JpaRepository<SalonLike, Integer> {

    Optional<SalonLike> findByMember_MemberIdAndSalon_SalonId(String memberId, Integer salonId);

    boolean existsByMember_MemberIdAndSalon_SalonId(String memberId, Integer salonId);

    List<SalonLike> findAllByMember_MemberIdOrderByCreatedAtDesc(String memberId);

    long countBySalon_SalonId(Integer salonId);
}
