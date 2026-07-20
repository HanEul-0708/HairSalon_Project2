package com.hairsalonproject2.member.service;

import com.hairsalonproject2.common.constant.MemberRole;
import com.hairsalonproject2.common.constant.MemberStatus;
import com.hairsalonproject2.member.entity.Member;
import com.hairsalonproject2.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class DummyMemberSeeder implements ApplicationRunner {

 private final MemberRepository memberRepository;
 private final PasswordEncoder passwordEncoder;

 @Value("${app.seed.members.enabled:false}")
 private boolean enabled;

 @Value("${app.seed.members.default-password:1234}")
 private String defaultPassword;

 @Value("${app.seed.members.target-designer-count:20}")
 private int targetDesignerCount;

 @Value("${app.seed.members.target-user-count:40}")
 private int targetUserCount;

 @Override
 @Transactional
 public void run(ApplicationArguments args) {
  if (!enabled) {
   return;
  }
  seedMembers();
 }

 @Transactional
 public int seedMembers() {
  String encodedPassword = passwordEncoder.encode(defaultPassword);
  int created = 0;
  created += seedRole(MemberRole.DESIGNER, "designer", "디자이너", targetDesignerCount, encodedPassword, 2000);
  created += seedRole(MemberRole.USER, "user", "회원", targetUserCount, encodedPassword, 3000);
  if (created > 0) {
   log.info("Seeded {} dummy members for local profile.", created);
  }
  return created;
 }

 private int seedRole(MemberRole role, String idPrefix, String namePrefix, int targetCount, String encodedPassword, int phoneBase) {
  long existingCount = memberRepository.countByRoleAndStatus(role, MemberStatus.ACTIVE);
  if (existingCount >= targetCount) {
   return 0;
  }
  int created = 0;
  int sequence = 1;
  while (existingCount + created < targetCount) {
   String memberId = idPrefix + sequence;
   sequence++;
   if (memberRepository.existsByMemberId(memberId)) {
	continue;
   }
   Member member = Member.builder().memberId(memberId).password(encodedPassword).name(namePrefix + (sequence - 1)).phone(buildPhone(phoneBase + sequence - 1)).email(memberId + "@example.com").role(role).status(MemberStatus.ACTIVE).build();
   memberRepository.save(member);
   created++;
  }
  return created;
 }

 private String buildPhone(int number) {
  int middle = (number / 10000) % 10000;
  int last = number % 10000;
  return String.format("010-%04d-%04d", middle, last);
 }
}
