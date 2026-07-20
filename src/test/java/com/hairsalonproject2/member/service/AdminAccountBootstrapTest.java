package com.hairsalonproject2.member.service;

import com.hairsalonproject2.common.constant.MemberRole;
import com.hairsalonproject2.common.constant.MemberStatus;
import com.hairsalonproject2.member.entity.Member;
import com.hairsalonproject2.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminAccountBootstrapTest {

 @Mock
 private MemberRepository memberRepository;

 @Mock
 private PasswordEncoder passwordEncoder;

 @InjectMocks
 private AdminAccountBootstrap adminAccountBootstrap;

 @Test
 void ensureAdminAccountCreatesAdminWhenMissing() {
  when(memberRepository.existsByMemberId("admin1")).thenReturn(false);
  when(passwordEncoder.encode("12341234")).thenReturn("encoded-password");
  adminAccountBootstrap.ensureAdminAccount();
  ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
  verify(memberRepository).save(memberCaptor.capture());
  Member saved = memberCaptor.getValue();
  assertThat(saved.getMemberId()).isEqualTo("admin1");
  assertThat(saved.getPassword()).isEqualTo("encoded-password");
  assertThat(saved.getName()).isEqualTo("관리자1");
  assertThat(saved.getEmail()).isEqualTo("admin1@test.com");
  assertThat(saved.getPhone()).isEqualTo("010-1234-5678");
  assertThat(saved.getRole()).isEqualTo(MemberRole.ADMIN);
  assertThat(saved.getStatus()).isEqualTo(MemberStatus.ACTIVE);
 }

 @Test
 void ensureAdminAccountSkipsWhenAdminAlreadyExists() {
  when(memberRepository.existsByMemberId("admin1")).thenReturn(true);
  adminAccountBootstrap.ensureAdminAccount();
  verify(memberRepository, never()).save(org.mockito.ArgumentMatchers.any());
 }
}
