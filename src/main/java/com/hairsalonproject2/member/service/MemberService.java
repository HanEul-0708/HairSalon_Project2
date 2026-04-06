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
import java.util.Locale;

/*
    회원 서비스

    해당 범위
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
        String normalizedMemberId = normalizeMemberId(request.getMemberId());
        String normalizedName = normalizeName(request.getName());
        String normalizedPhone = normalizePhone(request.getPhone());
        String normalizedEmail = normalizeEmail(request.getEmail());

        if (!request.getPassword().equals(request.getPasswordConfirm())) {
            throw new BusinessException(ErrorCode.PASSWORD_CONFIRM_NOT_MATCH);
        }

        if (memberRepository.existsByMemberId(normalizedMemberId)) {
            throw new BusinessException(ErrorCode.DUPLICATE_MEMBER_ID);
        }

        if (memberRepository.existsByEmail(normalizedEmail)) {
            throw new BusinessException(ErrorCode.DUPLICATE_MEMBER_EMAIL);
        }

        if (memberRepository.existsByPhone(normalizedPhone)) {
            throw new BusinessException(ErrorCode.DUPLICATE_MEMBER_PHONE);
        }

        Member member = Member.builder()
                .memberId(normalizedMemberId)
                .password(passwordEncoder.encode(request.getPassword()))
                .name(normalizedName)
                .phone(normalizedPhone)
                .email(normalizedEmail)
                .role(MemberRole.USER)
                .status(MemberStatus.ACTIVE)
                .build();

        memberRepository.save(member);
    }

    public MemberDetailResponse getMyDetail(String memberId) {
        return MemberDetailResponse.from(getMember(memberId));
    }

    public boolean isMemberIdAvailable(String memberId) {
        String normalizedMemberId = normalizeMemberId(memberId);
        if (normalizedMemberId == null) {
            return false;
        }

        return !memberRepository.existsByMemberId(normalizedMemberId);
    }

    public boolean isEmailAvailable(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail == null) {
            return false;
        }

        return !memberRepository.existsByEmail(normalizedEmail);
    }

    public boolean isPhoneAvailable(String phone) {
        String normalizedPhone = normalizePhone(phone);
        if (normalizedPhone == null) {
            return false;
        }

        return !memberRepository.existsByPhone(normalizedPhone);
    }

    public boolean isEmailAvailableForUpdate(String memberId, String email) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail == null) {
            return false;
        }

        return memberRepository.findByEmail(normalizedEmail)
                .map(found -> found.getMemberId().equals(memberId))
                .orElse(true);
    }

    public boolean isPhoneAvailableForUpdate(String memberId, String phone) {
        String normalizedPhone = normalizePhone(phone);
        if (normalizedPhone == null) {
            return false;
        }

        return memberRepository.findByPhone(normalizedPhone)
                .map(found -> found.getMemberId().equals(memberId))
                .orElse(true);
    }

    @Transactional
    public void updateMyProfile(String memberId, MemberUpdateRequest request) {
        Member member = getMember(memberId);
        String normalizedName = normalizeName(request.getName());
        String normalizedPhone = normalizePhone(request.getPhone());
        String normalizedEmail = normalizeEmail(request.getEmail());

        if (normalizedEmail != null) {
            memberRepository.findByEmail(normalizedEmail).ifPresent(found -> {
                if (!found.getMemberId().equals(memberId)) {
                    throw new BusinessException(ErrorCode.DUPLICATE_MEMBER_EMAIL);
                }
            });
        }

        if (normalizedPhone != null) {
            memberRepository.findByPhone(normalizedPhone).ifPresent(found -> {
                if (!found.getMemberId().equals(memberId)) {
                    throw new BusinessException(ErrorCode.DUPLICATE_MEMBER_PHONE);
                }
            });
        }

        member.updateProfile(normalizedName, normalizedPhone, normalizedEmail);
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

    private String normalizeMemberId(String memberId) {
        if (memberId == null) {
            return null;
        }

        String normalized = memberId.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeName(String name) {
        if (name == null) {
            return null;
        }

        String normalized = name.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizePhone(String phone) {
        if (phone == null) {
            return null;
        }

        String normalized = phone.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }

        String normalized = email.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }
}
