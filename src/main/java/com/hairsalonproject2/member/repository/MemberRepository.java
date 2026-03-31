package com.hairsalonproject2.member.repository;

import com.hairsalonproject2.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 회원 Repository
 *
 * 역할
 * 1. 회원 기본 CRUD 처리
 * 2. 이메일 조회
 * 3. 아이디/이메일 중복 체크
 */
public interface MemberRepository extends JpaRepository<Member, String> {

    /**
     * 이메일로 회원 조회
     */
    Optional<Member> findByEmail(String email);

    /**
     * 회원 아이디 중복 여부 확인
     */
    boolean existsByMemberId(String memberId);

    /**
     * 이메일 중복 여부 확인
     */
    boolean existsByEmail(String email);
}