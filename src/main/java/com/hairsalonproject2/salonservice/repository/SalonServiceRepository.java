package com.hairsalonproject2.salonservice.repository;

import com.hairsalonproject2.salonservice.entity.SalonService;
import com.hairsalonproject2.salonservice.projection.ServicePriceCompareRow;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SalonServiceRepository extends JpaRepository<SalonService, Integer> {
 @Override
 @EntityGraph(attributePaths = "salon")
 Optional<SalonService> findById(Integer id);

 @Lock(LockModeType.PESSIMISTIC_READ)
 @Query("select ss from SalonService ss where ss.serviceId = :id")
 Optional<SalonService> findByIdForReservation(@Param("id") Integer id);

 @EntityGraph(attributePaths = "salon")
 List<SalonService> findBySalonSalonIdInOrderByServiceIdAsc(List<Integer> salonIds);

 List<SalonService> findBySalonSalonId(Integer salonId);

 boolean existsBySalonSalonId(Integer salonId);

 @Query("""
		 select distinct ss.salon.salonId from SalonService ss
		 where lower(ss.name) like lower(concat('%', :keyword, '%'))
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
		 join fetch ss.salon s
		 where (:keyword is null
		         or lower(ss.name) like lower(concat('%', :keyword, '%'))
		         or lower(coalesce(ss.description, '')) like lower(concat('%', :keyword, '%')))
		   and (:salonKeyword is null or lower(s.name) like lower(concat('%', :salonKeyword, '%')))
		   and (:region is null
		         or lower(s.address) like lower(concat('%', :region, '%'))
		         or lower(coalesce(s.roadAddress, '')) like lower(concat('%', :region, '%')))
		   and (:maxPrice is null or ss.price <= :maxPrice)
		   and (:maxDuration is null or ss.duration <= :maxDuration)
		 """)
 List<SalonService> searchServices(@Param("keyword") String keyword, @Param("salonKeyword") String salonKeyword, @Param("region") String region, @Param("maxPrice") Integer maxPrice, @Param("maxDuration") Integer maxDuration);

 String SEARCH_FILTER = """
         where (:keyword is null
                 or lower(ss.name) like lower(concat('%', :keyword, '%'))
                 or lower(coalesce(ss.description, '')) like lower(concat('%', :keyword, '%')))
           and (:salonKeyword is null or lower(s.name) like lower(concat('%', :salonKeyword, '%')))
           and (:region is null
                 or lower(s.address) like lower(concat('%', :region, '%'))
                 or lower(coalesce(s.roadAddress, '')) like lower(concat('%', :region, '%')))
           and (:maxPrice is null or ss.price <= :maxPrice)
           and (:maxDuration is null or ss.duration <= :maxDuration)
         """;

 @Query(value = "select ss from SalonService ss join fetch ss.salon s " + SEARCH_FILTER + """
         order by
           case when :sortBy = 'duration' then ss.duration end asc,
           case when :sortBy = 'rating' then coalesce(s.averageRating, 0) end desc,
           case when :sortBy = 'name' then lower(ss.name) end asc,
           ss.price asc,
           case when :sortBy = 'price' then ss.duration end asc,
           lower(ss.name) asc, ss.serviceId asc
         """, countQuery = "select count(ss) from SalonService ss join ss.salon s " + SEARCH_FILTER)
 Page<SalonService> searchServices(@Param("keyword") String keyword,
                                  @Param("salonKeyword") String salonKeyword,
                                  @Param("region") String region,
                                  @Param("maxPrice") Integer maxPrice,
                                  @Param("maxDuration") Integer maxDuration,
                                  @Param("sortBy") String sortBy, Pageable pageable);

 @Query(value = """
		 SELECT ss.service_id AS serviceId,
		        ss.name AS serviceName,
		        ss.price AS price,
		        ss.duration AS duration,
		        s.salon_id AS salonId,
		        s.name AS salonName,
		        s.address AS address,
		        s.road_address AS roadAddress,
		        s.average_rating AS averageRating
		 FROM salon_service ss
		 JOIN salon s ON s.salon_id = ss.salon_id
		 WHERE (:serviceName IS NULL OR LOWER(ss.name) LIKE LOWER(CONCAT('%', :serviceName, '%')))
		   AND (:region IS NULL OR LOWER(s.address) LIKE LOWER(CONCAT('%', :region, '%'))
                   OR LOWER(COALESCE(s.road_address, '')) LIKE LOWER(CONCAT('%', :region, '%')))
		 ORDER BY ss.price ASC, COALESCE(s.average_rating, 0) DESC, s.salon_id ASC, ss.service_id ASC
		 """, nativeQuery = true)
 List<ServicePriceCompareRow> compareServices(@Param("serviceName") String serviceName, @Param("region") String region);

 @Query(value = """
         SELECT ss.service_id AS serviceId, ss.name AS serviceName, ss.price AS price,
                ss.duration AS duration, s.salon_id AS salonId, s.name AS salonName,
                s.address AS address, s.road_address AS roadAddress, s.average_rating AS averageRating
         FROM salon_service ss JOIN salon s ON s.salon_id = ss.salon_id
         WHERE (:serviceName IS NULL OR LOWER(ss.name) LIKE LOWER(CONCAT('%', :serviceName, '%')))
           AND (:region IS NULL OR LOWER(s.address) LIKE LOWER(CONCAT('%', :region, '%'))
                OR LOWER(COALESCE(s.road_address, '')) LIKE LOWER(CONCAT('%', :region, '%')))
         ORDER BY ss.price ASC, COALESCE(s.average_rating, 0) DESC, s.salon_id ASC, ss.service_id ASC
         """, countQuery = """
         SELECT COUNT(*) FROM salon_service ss JOIN salon s ON s.salon_id = ss.salon_id
         WHERE (:serviceName IS NULL OR LOWER(ss.name) LIKE LOWER(CONCAT('%', :serviceName, '%')))
           AND (:region IS NULL OR LOWER(s.address) LIKE LOWER(CONCAT('%', :region, '%'))
                OR LOWER(COALESCE(s.road_address, '')) LIKE LOWER(CONCAT('%', :region, '%')))
         """, nativeQuery = true)
 Page<ServicePriceCompareRow> compareServices(@Param("serviceName") String serviceName,
                                            @Param("region") String region, Pageable pageable);
}
