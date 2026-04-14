package com.hairsalonproject2.member.service;

import com.hairsalonproject2.common.constant.MemberStatus;
import com.hairsalonproject2.member.entity.Member;
import com.hairsalonproject2.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Spring Security 로그인용 회원 조회 서비스
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final MemberRepository memberRepository;

    @Override
    public UserDetails loadUserByUsername(String memberId) throws UsernameNotFoundException {

        // 1. 회원 조회
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new UsernameNotFoundException("존재하지 않는 회원입니다."));

        // 2. 탈퇴 회원 로그인 차단
        if (member.getStatus() == MemberStatus.DELETED) {
            throw new DisabledException("탈퇴한 회원은 로그인할 수 없습니다.");
        }

        // 3. 비활성 회원 로그인 차단
        if (member.getStatus() == MemberStatus.INACTIVE) {
            throw new DisabledException("비활성화된 회원은 로그인할 수 없습니다.");
        }

        // 4. 정상 회원만 인증 객체 생성
        return new CustomUserDetails(member);
    }
}
