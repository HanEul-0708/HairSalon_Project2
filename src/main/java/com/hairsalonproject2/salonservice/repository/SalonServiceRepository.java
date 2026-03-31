package com.hairsalonproject2.salonservice.repository;

import com.hairsalonproject2.salonservice.entity.SalonService;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * SalonServiceRepository
 *
 * 시술 엔티티 DB 접근용 Repository
 */
public interface SalonServiceRepository extends JpaRepository<SalonService, Integer> {
}