package com.hairsalonproject2.member.service;

import com.hairsalonproject2.member.entity.Member;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Spring Security 로그인 사용자 객체
 */
@Getter
public class CustomUserDetails implements UserDetails {

 private final Member member;

 public CustomUserDetails(Member member) {
  this.member = member;
 }

 /**
  * 권한 목록 반환
  * <p>
  * Spring Security 권한 규칙에 맞춰 ROLE_ 접두어를 붙인다.
  */
 @Override
 public Collection<? extends GrantedAuthority> getAuthorities() {
  return List.of(new SimpleGrantedAuthority("ROLE_" + member.getRole().name()));
 }

 /**
  * 암호화된 비밀번호 반환
  */
 @Override
 public String getPassword() {
  return member.getPassword();
 }

 /**
  * 로그인 아이디 반환
  * <p>
  * 이 프로젝트에서는 memberId를 username 개념으로 사용한다.
  */
 @Override
 public String getUsername() {
  return member.getMemberId();
 }

 @Override
 public boolean isAccountNonExpired() {
  return true;
 }

 @Override
 public boolean isAccountNonLocked() {
  return true;
 }

 @Override
 public boolean isCredentialsNonExpired() {
  return true;
 }

 @Override
 public boolean isEnabled() {
  return true;
 }
}
