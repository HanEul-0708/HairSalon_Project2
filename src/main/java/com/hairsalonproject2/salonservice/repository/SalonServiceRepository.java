package com.hairsalonproject2.salonservice.repository;

import com.hairsalonproject2.salonservice.entity.SalonService;
import com.hairsalonproject2.salonservice.projection.ServicePriceCompareRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SalonServiceRepository extends JpaRepository<SalonService, Integer> {
    List<SalonService> findBySalonSalonId(Integer salonId);

    @Query("""
            select ss from SalonService ss
            where (:keyword is null or lower(ss.name) like lower(concat('%', :keyword, '%')))
            order by ss.price asc, ss.serviceId asc
            """)
    List<SalonService> searchByName(@Param("keyword") String keyword);

    @Query(value = """
            SELECT ss.service_id AS serviceId,
                   ss.name AS serviceName,
                   ss.price AS price,
                   ss.duration AS duration,
                   s.salon_id AS salonId,
                   s.name AS salonName,
                   s.address AS address,
                   s.average_rating AS averageRating
            FROM salon_service ss
            JOIN salon s ON s.salon_id = ss.salon_id
            WHERE (:serviceName IS NULL OR LOWER(ss.name) LIKE LOWER(CONCAT('%', :serviceName, '%')))
              AND (:region IS NULL OR LOWER(s.address) LIKE LOWER(CONCAT('%', :region, '%')))
            ORDER BY ss.price ASC, s.average_rating DESC, s.salon_id ASC
            """, nativeQuery = true)
    List<ServicePriceCompareRow> compareServices(@Param("serviceName") String serviceName, @Param("region") String region);
}