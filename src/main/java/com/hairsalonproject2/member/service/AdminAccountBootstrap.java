package com.hairsalonproject2.member.service;

import com.hairsalonproject2.common.constant.MemberRole;
import com.hairsalonproject2.common.constant.MemberStatus;
import com.hairsalonproject2.member.entity.Member;
import com.hairsalonproject2.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAccountBootstrap implements ApplicationRunner {

 private static final String ADMIN_ID = "admin1";
 private static final String ADMIN_PASSWORD = "12341234";
 private static final String ADMIN_NAME = "관리자1";
 private static final String ADMIN_EMAIL = "admin1@test.com";
 private static final String ADMIN_PHONE = "010-1234-5678";

 private final MemberRepository memberRepository;
 private final PasswordEncoder passwordEncoder;

 @Override
 @Transactional
 public void run(ApplicationArguments args) {
  ensureAdminAccount();
 }

 @Transactional
 public void ensureAdminAccount() {
  if (memberRepository.existsByMemberId(ADMIN_ID)) {
   return;
  }
  Member admin = Member.builder().memberId(ADMIN_ID).password(passwordEncoder.encode(ADMIN_PASSWORD)).name(ADMIN_NAME).phone(ADMIN_PHONE).email(ADMIN_EMAIL).role(MemberRole.ADMIN).status(MemberStatus.ACTIVE).build();
  memberRepository.save(admin);
  log.info("Bootstrapped default admin account '{}'.", ADMIN_ID);
 }
}
