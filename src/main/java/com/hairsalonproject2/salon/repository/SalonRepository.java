package com.hairsalonproject2.salon.repository;

import com.hairsalonproject2.salon.entity.Salon;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SalonRepository extends JpaRepository<Salon, Integer>, JpaSpecificationExecutor<Salon> {
 Optional<Salon> findByExternalId(String externalId);
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 Optional<Salon> findBySourceTypeAndExternalId(String sourceType, String externalId);

 long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

 @Query("""
		 select s from Salon s
		 order by s.averageRating desc, s.reviewCount desc, s.likeCount desc, s.salonId asc
		 """)
 List<Salon> findRecommendedSalons(Pageable pageable);
}
