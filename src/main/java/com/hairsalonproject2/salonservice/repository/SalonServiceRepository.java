package com.hairsalonproject2.salonservice.repository;

import com.hairsalonproject2.salonservice.entity.SalonService;
import com.hairsalonproject2.salonservice.projection.ServicePriceCompareRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SalonServiceRepository extends JpaRepository<SalonService, Integer> {
    List<SalonService> findBySalonSalonId(Integer salonId);
    boolean existsBySalonSalonId(Integer salonId);

    @Query("""
            select distinct ss.salon.salonId
            from SalonService ss
            where lower(ss.name) like lower(concat('%', :keyword, '%'))
               or lower(coalesce(ss.description, '')) like lower(concat('%', :keyword, '%'))
            order by ss.salon.salonId asc
            """)
    List<Integer> findDistinctSalonIdsByKeyword(@Param("keyword") String keyword);

    @Query("""
            select ss from SalonService ss
            where (:keyword is null or lower(ss.name) like lower(concat('%', :keyword, '%')))
            order by ss.price asc, ss.serviceId asc
            """)
    List<SalonService> searchByName(@Param("keyword") String keyword);

    @Query("""
            select ss
            from SalonService ss
            join ss.salon s
            where (:keyword is null
                    or lower(ss.name) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(ss.description, '')) like lower(concat('%', :keyword, '%'))
                    or lower(s.name) like lower(concat('%', :keyword, '%')))
              and (:salonKeyword is null or lower(s.name) like lower(concat('%', :salonKeyword, '%')))
              and (:region is null
                    or lower(s.address) like lower(concat('%', :region, '%'))
                    or lower(coalesce(s.roadAddress, '')) like lower(concat('%', :region, '%')))
              and (:city is null
                    or lower(s.address) like lower(concat(:city, '%'))
                    or lower(coalesce(s.roadAddress, '')) like lower(concat(:city, '%')))
              and (:district is null
                    or lower(s.address) like lower(concat('% ', :district, '%'))
                    or lower(coalesce(s.roadAddress, '')) like lower(concat('% ', :district, '%')))
              and (:neighborhood is null
                    or lower(s.address) like lower(concat('% ', :neighborhood, '%'))
                    or lower(coalesce(s.roadAddress, '')) like lower(concat('% ', :neighborhood, '%')))
              and (:maxPrice is null or ss.price <= :maxPrice)
              and (:maxDuration is null or ss.duration <= :maxDuration)
            """)
    List<SalonService> searchServices(@Param("keyword") String keyword,
                                      @Param("salonKeyword") String salonKeyword,
                                      @Param("region") String region,
                                      @Param("city") String city,
                                      @Param("district") String district,
                                      @Param("neighborhood") String neighborhood,
                                      @Param("maxPrice") Integer maxPrice,
                                      @Param("maxDuration") Integer maxDuration);

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
              AND (:city IS NULL OR LOWER(s.address) LIKE LOWER(CONCAT(:city, '%')))
              AND (:district IS NULL OR LOWER(s.address) LIKE LOWER(CONCAT('% ', :district, '%')))
              AND (:neighborhood IS NULL OR LOWER(s.address) LIKE LOWER(CONCAT('% ', :neighborhood, '%')))
            ORDER BY ss.price ASC, s.average_rating DESC, s.salon_id ASC
            """, nativeQuery = true)
    List<ServicePriceCompareRow> compareServices(@Param("serviceName") String serviceName,
                                                 @Param("city") String city,
                                                 @Param("district") String district,
                                                 @Param("neighborhood") String neighborhood);
}
