package com.hairsalonproject2.repository;

import com.hairsalonproject2.entity.Salon;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SalonRepository extends JpaRepository<Salon, Integer>, JpaSpecificationExecutor<Salon> {
    Optional<Salon> findByExternalId(String externalId);

    @Query("""
            select s from Salon s
            order by s.averageRating desc, s.reviewCount desc, s.likeCount desc, s.salonId asc
            """)
    List<Salon> findRecommendedSalons(Pageable pageable);
}