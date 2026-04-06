package com.hairsalonproject2.member.service;

import com.hairsalonproject2.common.constant.MemberRole;
import com.hairsalonproject2.common.constant.MemberStatus;
import com.hairsalonproject2.common.exception.BusinessException;
import com.hairsalonproject2.common.exception.ErrorCode;
import com.hairsalonproject2.designer.repository.DesignerRepository;
import com.hairsalonproject2.member.dto.request.MemberPasswordChangeRequest;
import com.hairsalonproject2.member.dto.request.MemberSignupRequest;
import com.hairsalonproject2.member.dto.request.MemberUpdateRequest;
import com.hairsalonproject2.member.dto.response.MemberDetailResponse;
import com.hairsalonproject2.member.dto.response.MemberSummaryResponse;
import com.hairsalonproject2.member.entity.Member;
import com.hairsalonproject2.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

    private final DesignerRepository designerRepository;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void signup(MemberSignupRequest request) {
        if (!request.getPassword().equals(request.getPasswordConfirm())) {
            throw new BusinessException(ErrorCode.PASSWORD_CONFIRM_NOT_MATCH);
        }

        if (memberRepository.existsByMemberId(request.getMemberId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_MEMBER_ID);
        }

        if (memberRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.DUPLICATE_MEMBER_EMAIL);
        }

        if (memberRepository.existsByPhone(request.getPhone())) {
            throw new BusinessException(ErrorCode.DUPLICATE_MEMBER_PHONE);
        }

        Member member = Member.builder()
                .memberId(request.getMemberId())
                .password(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .phone(request.getPhone())
                .email(request.getEmail())
                .role(MemberRole.USER)
                .status(MemberStatus.ACTIVE)
                .build();

        memberRepository.save(member);
    }

    public MemberDetailResponse getMyDetail(String memberId) {
        return MemberDetailResponse.from(getMember(memberId));
    }

    public boolean isMemberIdAvailable(String memberId) {
        if (memberId == null || memberId.isBlank()) {
            return false;
        }

        return !memberRepository.existsByMemberId(memberId.trim());
    }

    public boolean isEmailAvailable(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }

        return !memberRepository.existsByEmail(email.trim());
    }

    public boolean isPhoneAvailable(String phone) {
        if (phone == null || phone.isBlank()) {
            return false;
        }

        return !memberRepository.existsByPhone(phone.trim());
    }

    public boolean isEmailAvailableForUpdate(String memberId, String email) {
        if (email == null || email.isBlank()) {
            return false;
        }

        String trimmedEmail = email.trim();

        return memberRepository.findByEmail(trimmedEmail)
                .map(found -> found.getMemberId().equals(memberId))
                .orElse(true);
    }

    public boolean isPhoneAvailableForUpdate(String memberId, String phone) {
        if (phone == null || phone.isBlank()) {
            return false;
        }

        String trimmedPhone = phone.trim();

        return memberRepository.findByPhone(trimmedPhone)
                .map(found -> found.getMemberId().equals(memberId))
                .orElse(true);
    }

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

        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            memberRepository.findByPhone(request.getPhone()).ifPresent(found -> {
                if (!found.getMemberId().equals(memberId)) {
                    throw new BusinessException(ErrorCode.DUPLICATE_MEMBER_PHONE);
                }
            });
        }

        member.updateProfile(
                request.getName(),
                request.getPhone(),
                request.getEmail()
        );
    }

    @Transactional
    public void changePassword(String memberId, MemberPasswordChangeRequest request) {
        Member member = getMember(memberId);

        if (!request.getNewPassword().equals(request.getNewPasswordConfirm())) {
            throw new BusinessException(ErrorCode.PASSWORD_CONFIRM_NOT_MATCH);
        }

        if (!passwordEncoder.matches(request.getCurrentPassword(), member.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD);
        }

        member.changePassword(passwordEncoder.encode(request.getNewPassword()));
    }

    public List<MemberSummaryResponse> getAllMembers() {
        return memberRepository.findAll()
                .stream()
                .map(MemberSummaryResponse::from)
                .toList();
    }

    public MemberDetailResponse getMemberDetailByAdmin(String memberId) {
        return MemberDetailResponse.from(getMember(memberId));
    }

    public Page<MemberSummaryResponse> searchMembers(String keyword,
                                                    MemberRole role,
                                                    MemberStatus status,
                                                    String sort,
                                                    int page) {
        Pageable pageable = PageRequest.of(page, 10, createSort(sort));
        String trimmedKeyword = (keyword == null) ? null : keyword.trim();

        return memberRepository.searchMembers(trimmedKeyword, role, status, pageable)
                .map(MemberSummaryResponse::from);
    }

    @Transactional
    public void deleteMemberByAdmin(String adminMemberId, String targetMemberId) {
        if (adminMemberId.equals(targetMemberId)) {
            throw new BusinessException(ErrorCode.CANNOT_DELETE_MYSELF);
        }

        Member targetMember = getMember(targetMemberId);

        if (targetMember.getRole() == MemberRole.ADMIN) {
            long adminCount = memberRepository.countByRole(MemberRole.ADMIN);
            if (adminCount <= 1) {
                throw new BusinessException(ErrorCode.LAST_ADMIN_CANNOT_BE_DELETED);
            }
        }

        targetMember.changeStatus(MemberStatus.DELETED);
    }

    @Transactional
    public void changeMemberRoleByAdmin(String adminMemberId,
                                        String targetMemberId,
                                        MemberRole role) {
        if (adminMemberId.equals(targetMemberId)) {
            throw new BusinessException(ErrorCode.CANNOT_CHANGE_MY_ROLE);
        }

        Member member = getMember(targetMemberId);

        if (member.getRole() == MemberRole.ADMIN && role != MemberRole.ADMIN) {
            long adminCount = memberRepository.countByRole(MemberRole.ADMIN);
            if (adminCount <= 1) {
                throw new BusinessException(ErrorCode.LAST_ADMIN_ROLE_CANNOT_BE_CHANGED);
            }
        }

        boolean isLinkedDesigner = designerRepository.existsByMember_MemberId(targetMemberId);
        if (isLinkedDesigner && role != MemberRole.DESIGNER) {
            throw new BusinessException(ErrorCode.CONNECTED_DESIGNER_ACCOUNT_ROLE_CHANGE_NOT_ALLOWED);
        }

        member.changeRole(role);
    }

    private Member getMember(String memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private Sort createSort(String sort) {
        if ("name".equalsIgnoreCase(sort)) {
            return Sort.by(Sort.Order.asc("name"), Sort.Order.desc("createdAt"));
        }

        if ("role".equalsIgnoreCase(sort)) {
            return Sort.by(Sort.Order.asc("role"), Sort.Order.desc("createdAt"));
        }

        return Sort.by(Sort.Order.desc("createdAt"));
    }

    @Transactional
    public void changeMemberStatusByAdmin(String adminMemberId,
                                          String targetMemberId,
                                          MemberStatus status) {
        if (adminMemberId.equals(targetMemberId)) {
            throw new BusinessException(ErrorCode.CANNOT_CHANGE_MY_STATUS);
        }

        Member member = getMember(targetMemberId);
        member.changeStatus(status);
    }

    @Transactional
    public void delete(String memberId) {
        Member member = getMember(memberId);
        member.changeStatus(MemberStatus.DELETED);
    }
}
