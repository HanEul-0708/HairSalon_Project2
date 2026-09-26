package com.hairsalonproject2.designer.repository;

import com.hairsalonproject2.designer.entity.Designer;
import com.hairsalonproject2.designer.projection.DesignerRatingRow;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DesignerRepository extends JpaRepository<Designer, Integer>, JpaSpecificationExecutor<Designer> {
 @Override
 @EntityGraph(attributePaths = "salon")
 List<Designer> findAll(Specification<Designer> specification);

 @Override
 @EntityGraph(attributePaths = "salon")
 List<Designer> findAll();

 @EntityGraph(attributePaths = {"salon", "member"})
 Optional<Designer> findDetailByDesignerId(Integer designerId);

 @Lock(LockModeType.PESSIMISTIC_READ)
 @Query("select d from Designer d where d.designerId = :designerId")
 Optional<Designer> findByIdForReservation(Integer designerId);

 List<Designer> findBySalonSalonId(Integer salonId);

 boolean existsBySalonSalonId(Integer salonId);

 boolean existsByMember_MemberId(String memberId);

 long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

 @Query(value = """
		 SELECT d.designer_id AS designerId,
		        COALESCE(AVG(r.rating), 0) AS averageRating,
		        COUNT(r.review_id) AS reviewCount
		 FROM designer d
		 LEFT JOIN review r ON r.designer_id = d.designer_id
		 GROUP BY d.designer_id
		 """, nativeQuery = true)
 List<DesignerRatingRow> findDesignerRatingRows();

 @Query(value = """
   SELECT d.designer_id AS designerId,
          COALESCE(AVG(r.rating), 0) AS averageRating,
          COUNT(r.review_id) AS reviewCount
   FROM designer d
   LEFT JOIN review r ON r.designer_id = d.designer_id
   WHERE d.designer_id IN (:designerIds)
   GROUP BY d.designer_id
   """, nativeQuery = true)
 List<DesignerRatingRow> findDesignerRatingRowsByIds(List<Integer> designerIds);
}
