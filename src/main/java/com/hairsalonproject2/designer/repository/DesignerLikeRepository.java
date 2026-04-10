package com.hairsalonproject2.designer.repository;

import com.hairsalonproject2.designer.entity.DesignerLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DesignerLikeRepository extends JpaRepository<DesignerLike, Integer> {

    Optional<DesignerLike> findByMember_MemberIdAndDesigner_DesignerId(String memberId, Integer designerId);

    boolean existsByMember_MemberIdAndDesigner_DesignerId(String memberId, Integer designerId);

    List<DesignerLike> findAllByMember_MemberIdOrderByCreatedAtDesc(String memberId);

    List<DesignerLike> findAllByDesigner_Member_MemberIdOrderByCreatedAtDesc(String memberId);

    long countByDesigner_DesignerId(Integer designerId);
}
