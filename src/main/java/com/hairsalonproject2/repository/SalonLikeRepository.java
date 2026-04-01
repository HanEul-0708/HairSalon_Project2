package com.hairsalonproject2.repository;

import com.hairsalonproject2.entity.SalonLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SalonLikeRepository extends JpaRepository<SalonLike, Integer> {
    Optional<SalonLike> findByMemberIdAndSalonSalonId(String memberId, Integer salonId);
    long countBySalonSalonId(Integer salonId);
}
