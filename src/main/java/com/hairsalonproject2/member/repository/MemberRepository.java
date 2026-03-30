package com.hairsalonproject2.member.repository;

import com.hairsalonproject2.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, String> {
}