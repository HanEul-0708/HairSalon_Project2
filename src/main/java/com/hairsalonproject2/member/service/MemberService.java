package com.hairsalonproject2.member.service;

import com.hairsalonproject2.common.constant.MemberRole;
import com.hairsalonproject2.exception.BusinessException;
import com.hairsalonproject2.exception.ErrorCode;
import com.hairsalonproject2.member.dto.request.MemberPasswordChangeRequest;
import com.hairsalonproject2.member.dto.request.MemberSignupRequest;
import com.hairsalonproject2.member.dto.request.MemberUpdateRequest;
import com.hairsalonproject2.member.dto.response.MemberDetailResponse;
import com.hairsalonproject2.member.dto.response.MemberSummaryResponse;
import com.hairsalonproject2.member.entity.Member;
import com.hairsalonproject2.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/*
    회원 서비스

    담당 범위
    1. 회원가입
    2. 내 정보 조회
    3. 내 정보 수정
    4. 비밀번호 변경
    5. 관리자 회원 목록/삭제/권한 변경
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 회원가입
     *
     * 처리 순서
     * 1. 아이디 중복 체크
     * 2. 이메일 중복 체크
     * 3. 비밀번호 암호화
     * 4. USER 권한으로 저장
     */
    @Transactional
    public void signup(MemberSignupRequest request) {

        if (memberRepository.existsByMemberId(request.getMemberId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_MEMBER_ID);
        }

        if (request.getEmail() != null && !request.getEmail().isBlank()
                && memberRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.DUPLICATE_MEMBER_EMAIL);
        }

        Member member = Member.builder()
                .memberId(request.getMemberId())
                .password(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .phone(request.getPhone())
                .email(request.getEmail())
                .role(MemberRole.USER)
                .build();

        memberRepository.save(member);
    }

    /**
     * 내 정보 상세 조회
     */
    public MemberDetailResponse getMyDetail(String memberId) {
        return MemberDetailResponse.from(getMember(memberId));
    }

    /**
     * 내 정보 수정
     */
    @Transactional
    public void updateMyProfile(String memberId, MemberUpdateRequest request) {
        Member member = getMember(memberId);

        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            memberRepository.findByEmail(request.getEmail()).ifPresent(found -> {
                if (!found.getMemberId().equals(memberId)) {
                    throw new BusinessException(ErrorCode.DUPLICATE_MEMBER_EMAIL);
                }
            });
        }

        member.updateProfile(
                request.getName(),
                request.getPhone(),
                request.getEmail()
        );
    }

    /**
     * 비밀번호 변경
     *
     * 1. 현재 비밀번호 일치 여부 검사
     * 2. 새 비밀번호 암호화 후 저장
     */
    @Transactional
    public void changePassword(String memberId, MemberPasswordChangeRequest request) {
        Member member = getMember(memberId);

        if (!passwordEncoder.matches(request.getCurrentPassword(), member.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD);
        }

        member.changePassword(passwordEncoder.encode(request.getNewPassword()));
    }

    /**
     * 관리자용 회원 목록 조회
     */
    public List<MemberSummaryResponse> getAllMembers() {
        return memberRepository.findAll()
                .stream()
                .map(MemberSummaryResponse::from)
                .toList();
    }

    /**
     * 관리자용 회원 상세 조회
     */
    public MemberDetailResponse getMemberDetailByAdmin(String memberId) {
        return MemberDetailResponse.from(getMember(memberId));
    }

    /**
     * 관리자용 회원 삭제
     */
    @Transactional
    public void deleteMemberByAdmin(String memberId) {
        memberRepository.delete(getMember(memberId));
    }

    /**
     * 관리자용 회원 권한 변경
     */
    @Transactional
    public void changeMemberRoleByAdmin(String memberId, MemberRole role) {
        Member member = getMember(memberId);
        member.changeRole(role);
    }

    /**
     * 공통 회원 조회 메서드
     */
    private Member getMember(String memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }
}