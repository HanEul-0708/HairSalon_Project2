package com.hairsalonproject2.member.repository;

import com.hairsalonproject2.common.constant.MemberRole;
import com.hairsalonproject2.member.entity.Member;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, String> {

    Optional<Member> findByEmail(String email);

    boolean existsByMemberId(String memberId);

    boolean existsByEmail(String email);

    long countByRole(MemberRole role);

    /**
     * 권한별 회원 조회
     */
    Page<Member> findByRole(MemberRole role, Pageable pageable);

    /**
     * 아이디 / 이름 / 이메일 키워드 검색
     */
    Page<Member> findByMemberIdContainingIgnoreCaseOrNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
            String memberIdKeyword,
            String nameKeyword,
            String emailKeyword,
            Pageable pageable
    );

    /**
     * 권한 + 키워드 검색
     */
    @Query("""
            SELECT m
            FROM Member m
            WHERE m.role = :role
              AND (
                    LOWER(m.memberId) LIKE LOWER(CONCAT('%', :keyword, '%'))
                 OR LOWER(m.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                 OR LOWER(m.email) LIKE LOWER(CONCAT('%', :keyword, '%'))
              )
            """)
    Page<Member> searchByKeywordAndRole(@Param("keyword") String keyword,
                                        @Param("role") MemberRole role,
                                        Pageable pageable);
}