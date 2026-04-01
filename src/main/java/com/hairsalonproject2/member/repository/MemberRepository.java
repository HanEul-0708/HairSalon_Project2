package com.hairsalonproject2.member.repository;

import com.hairsalonproject2.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * MemberRepository
 * 회원 조회/저장용 저장소
 */
public interface MemberRepository extends JpaRepository<Member, String> {
}