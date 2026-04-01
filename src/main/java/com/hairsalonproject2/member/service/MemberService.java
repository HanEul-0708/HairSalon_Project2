package com.hairsalonproject2.member.service;

import com.hairsalonproject2.common.constant.MemberRole;
import com.hairsalonproject2.common.constant.MemberStatus;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import com.hairsalonproject2.designer.repository.DesignerRepository;
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

    /**
     * 회원가입
     *
     * 처리 순서
     * 1. 비밀번호 / 비밀번호 확인 일치 여부 검사
     * 2. 아이디 중복 체크
     * 3. 이메일 중복 체크
     * 4. 비밀번호 암호화
     * 5. USER 권한으로 저장
     */
    @Transactional
    public void signup(MemberSignupRequest request) {

        // 비밀번호와 비밀번호 확인 일치 여부 검사
        if (!request.getPassword().equals(request.getPasswordConfirm())) {
            throw new BusinessException(ErrorCode.PASSWORD_CONFIRM_NOT_MATCH);
        }

        // 아이디 중복 검사
        if (memberRepository.existsByMemberId(request.getMemberId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_MEMBER_ID);
        }

        // 이메일 중복 검사
        if (memberRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.DUPLICATE_MEMBER_EMAIL);
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
     * 관리자용 회원 검색 + 페이징
     *
     * @param keyword 검색어
     * @param role 권한
     * @param page 페이지 번호(0부터 시작)
     * @return 회원 목록 페이지
     */
    public Page<MemberSummaryResponse> searchMembers(String keyword, MemberRole role, int page) {

        // createdAt 내림차순 정렬
        Pageable pageable = PageRequest.of(page, 10, Sort.by(Sort.Direction.DESC, "createdAt"));

        String trimmedKeyword = (keyword == null) ? null : keyword.trim();

        Page<Member> memberPage;

        // 1. 검색 조건이 모두 없으면 전체 조회
        if ((trimmedKeyword == null || trimmedKeyword.isBlank()) && role == null) {
            memberPage = memberRepository.findAll(pageable);
        }
        // 2. 권한만 선택한 경우
        else if ((trimmedKeyword == null || trimmedKeyword.isBlank()) && role != null) {
            memberPage = memberRepository.findByRole(role, pageable);
        }
        // 3. 검색어만 입력한 경우
        else if (role == null) {
            memberPage = memberRepository
                    .findByMemberIdContainingIgnoreCaseOrNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                            trimmedKeyword, trimmedKeyword, trimmedKeyword, pageable
                    );
        }
        // 4. 검색어 + 권한 둘 다 있는 경우
        else {
            memberPage = memberRepository.searchByKeywordAndRole(trimmedKeyword, role, pageable);
        }

        return memberPage.map(MemberSummaryResponse::from);
    }

    /**
     * 관리자용 회원 삭제
     *
     * @param adminMemberId 현재 로그인한 관리자 아이디
     * @param targetMemberId 삭제 대상 회원 아이디
     */
    @Transactional
    public void deleteMemberByAdmin(String adminMemberId, String targetMemberId) {

        // 1. 관리자가 자기 자신을 삭제하려는 경우 차단
        if (adminMemberId.equals(targetMemberId)) {
            throw new BusinessException(ErrorCode.CANNOT_DELETE_MYSELF);
        }

        // 2. 삭제 대상 회원 조회
        Member targetMember = getMember(targetMemberId);

        // 3. 마지막 관리자 삭제 방지
        if (targetMember.getRole() == MemberRole.ADMIN) {
            long adminCount = memberRepository.countByRole(MemberRole.ADMIN);

            if (adminCount <= 1) {
                throw new BusinessException(ErrorCode.LAST_ADMIN_CANNOT_BE_DELETED);
            }
        }

        // 4. 실제 삭제 대신 상태 변경
        targetMember.changeStatus(MemberStatus.DELETED);
    }

    /**
     * 관리자용 회원 권한 변경
     *
     * 규칙
     * 1. 관리자는 자기 자신의 권한을 변경할 수 없다.
     * 2. 디자이너에 연결된 계정은 DESIGNER 권한만 유지할 수 있다.
     */
    @Transactional
    public void changeMemberRoleByAdmin(String adminMemberId,
                                        String targetMemberId,
                                        MemberRole role) {

        // 1. 자기 자신의 권한 변경 금지
        if (adminMemberId.equals(targetMemberId)) {
            throw new BusinessException(ErrorCode.CANNOT_CHANGE_MY_ROLE);
        }

        // 2. 대상 회원 조회
        Member member = getMember(targetMemberId);

        // 3. 마지막 관리자 권한 변경 방지
        if (member.getRole() == MemberRole.ADMIN && role != MemberRole.ADMIN) {
            long adminCount = memberRepository.countByRole(MemberRole.ADMIN);

            if (adminCount <= 1) {
                throw new BusinessException(ErrorCode.LAST_ADMIN_ROLE_CANNOT_BE_CHANGED);
            }
        }

        // 4. 디자이너 연결 여부 확인
        boolean isLinkedDesigner = designerRepository.existsByMember_MemberId(targetMemberId);

        // 5. 디자이너에 연결된 계정은 DESIGNER 권한만 허용
        if (isLinkedDesigner && role != MemberRole.DESIGNER) {
            throw new BusinessException(ErrorCode.CONNECTED_DESIGNER_ACCOUNT_ROLE_CHANGE_NOT_ALLOWED);
        }

        // 6. 권한 변경
        member.changeRole(role);
    }

    /**
     * 공통 회원 조회 메서드
     */
    private Member getMember(String memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    /**
     * 관리자용 회원 상태 변경
     *
     * 규칙
     * 1. 관리자는 자기 자신의 상태를 변경할 수 없다.
     * 2. 대상 회원 상태를 ACTIVE / INACTIVE / DELETED 로 변경한다.
     *
     * @param adminMemberId 현재 로그인한 관리자 아이디
     * @param targetMemberId 상태 변경 대상 회원 아이디
     * @param status 변경할 상태
     */
    @Transactional
    public void changeMemberStatusByAdmin(String adminMemberId,
                                          String targetMemberId,
                                          MemberStatus status) {

        // 1. 자기 자신의 상태 변경 금지
        if (adminMemberId.equals(targetMemberId)) {
            throw new BusinessException(ErrorCode.CANNOT_CHANGE_MY_STATUS);
        }

        // 2. 대상 회원 조회
        Member member = getMember(targetMemberId);

        // 3. 상태 변경
        member.changeStatus(status);
    }
}