package com.hairsalonproject2.member.repository;

import com.hairsalonproject2.common.constant.MemberRole;
import com.hairsalonproject2.common.constant.MemberStatus;
import com.hairsalonproject2.member.entity.Member;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

public interface MemberRepository extends JpaRepository<Member, String> {

    Optional<Member> findByEmail(String email);

    Optional<Member> findByPhone(String phone);

    boolean existsByMemberId(String memberId);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    long countByRole(MemberRole role);
    long countByRoleAndStatus(MemberRole role, MemberStatus status);
    List<Member> findByRoleAndStatusOrderByCreatedAtAsc(MemberRole role, MemberStatus status);
    Optional<Member> findFirstByRoleAndStatusAndDesignerIsNullOrderByCreatedAtAscMemberIdAsc(MemberRole role, MemberStatus status);

    @Query("""
            SELECT m
            FROM Member m
            WHERE (:role IS NULL OR m.role = :role)
              AND (:status IS NULL OR m.status = :status)
              AND (
                    :keyword IS NULL OR :keyword = ''
                 OR LOWER(m.memberId) LIKE LOWER(CONCAT('%', :keyword, '%'))
                 OR LOWER(m.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                 OR LOWER(m.email) LIKE LOWER(CONCAT('%', :keyword, '%'))
              )
            """)
    Page<Member> searchMembers(@Param("keyword") String keyword,
                               @Param("role") MemberRole role,
                               @Param("status") MemberStatus status,
                               Pageable pageable);
}
