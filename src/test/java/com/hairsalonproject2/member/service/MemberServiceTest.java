package com.hairsalonproject2.member.service;

import com.hairsalonproject2.common.constant.MemberRole;
import com.hairsalonproject2.common.constant.MemberStatus;
import com.hairsalonproject2.common.exception.BusinessException;
import com.hairsalonproject2.common.exception.ErrorCode;
import com.hairsalonproject2.designer.entity.Designer;
import com.hairsalonproject2.designer.repository.DesignerRepository;
import com.hairsalonproject2.member.dto.request.MemberPasswordChangeRequest;
import com.hairsalonproject2.member.dto.request.MemberSignupRequest;
import com.hairsalonproject2.member.entity.Member;
import com.hairsalonproject2.member.repository.MemberRepository;
import com.hairsalonproject2.salon.entity.Salon;
import com.hairsalonproject2.salon.repository.SalonRepository;
import com.hairsalonproject2.salon.service.SalonQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private DesignerRepository designerRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private SalonRepository salonRepository;

    @Mock
    private SalonQueryService salonQueryService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private MemberService memberService;

    @Test
    void signupSavesEncodedUserMember() {
        MemberSignupRequest request = new MemberSignupRequest();
        request.setMemberId("  user01  ");
        request.setPassword("password123");
        request.setPasswordConfirm("password123");
        request.setName(" Tester ");
        request.setPhone(" 010-1234-5678 ");
        request.setEmail(" TESTER@example.com ");

        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");

        memberService.signup(request);

        ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(memberCaptor.capture());

        Member savedMember = memberCaptor.getValue();
        assertThat(savedMember.getMemberId()).isEqualTo("user01");
        assertThat(savedMember.getPassword()).isEqualTo("encoded-password");
        assertThat(savedMember.getName()).isEqualTo("Tester");
        assertThat(savedMember.getPhone()).isEqualTo("010-1234-5678");
        assertThat(savedMember.getEmail()).isEqualTo("tester@example.com");
        assertThat(savedMember.getRole()).isEqualTo(MemberRole.USER);
        assertThat(savedMember.getStatus()).isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    void designerSignupUsesSalonNameAndLinksRepresentativeDesigner() {
        MemberSignupRequest request = new MemberSignupRequest();
        request.setRole(MemberRole.DESIGNER);
        request.setMemberId("designer-salon-01");
        request.setPassword("password123");
        request.setPasswordConfirm("password123");
        request.setPhone("010-1111-2222");
        request.setEmail("designer@example.com");
        request.setSalonId(7);

        Salon salon = Salon.builder().salonId(7).name("광안 살롱").address("부산광역시 수영구 광안동").build();
        Designer designer = Designer.builder().designerId(70).salon(salon).name("대표 디자이너").careerYears(11).build();

        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(salonRepository.findById(7)).thenReturn(Optional.of(salon));
        when(designerRepository.existsBySalonSalonIdAndMemberIsNotNull(7)).thenReturn(false);
        when(memberRepository.existsByNameAndRoleAndStatusNot("광안 살롱", MemberRole.DESIGNER, MemberStatus.DELETED)).thenReturn(false);
        when(designerRepository.findFirstBySalonSalonIdOrderByCareerYearsDescDesignerIdDesc(7)).thenReturn(Optional.of(designer));
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        memberService.signup(request);

        ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(memberCaptor.capture());
        Member savedMember = memberCaptor.getValue();
        assertThat(savedMember.getName()).isEqualTo("광안 살롱");
        assertThat(savedMember.getRole()).isEqualTo(MemberRole.DESIGNER);
        assertThat(designer.getMember()).isEqualTo(savedMember);
    }

    @Test
    void designerSignupRejectsSalonThatAlreadyHasLinkedAccount() {
        MemberSignupRequest request = new MemberSignupRequest();
        request.setRole(MemberRole.DESIGNER);
        request.setMemberId("designer-salon-01");
        request.setPassword("password123");
        request.setPasswordConfirm("password123");
        request.setPhone("010-1111-2222");
        request.setEmail("designer@example.com");
        request.setSalonId(7);

        Salon salon = Salon.builder().salonId(7).name("광안 살롱").build();

        when(salonRepository.findById(7)).thenReturn(Optional.of(salon));
        when(designerRepository.existsBySalonSalonIdAndMemberIsNotNull(7)).thenReturn(true);

        assertThatThrownBy(() -> memberService.signup(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DESIGNER_SIGNUP_ACCOUNT_ALREADY_EXISTS);
    }

    @Test
    void searchMembersAppliesNameSort() {
        when(memberRepository.searchMembers(any(), any(), any(), any(Pageable.class))).thenReturn(Page.empty());

        memberService.searchMembers(null, null, null, "name", 0);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(memberRepository).searchMembers(any(), any(), any(), pageableCaptor.capture());

        Sort sort = pageableCaptor.getValue().getSort();
        assertThat(sort.getOrderFor("name")).isNotNull();
        assertThat(sort.getOrderFor("name").getDirection()).isEqualTo(Sort.Direction.ASC);
        assertThat(sort.getOrderFor("createdAt")).isNotNull();
        assertThat(sort.getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void isEmailAvailableReturnsFalseWhenEmailExists() {
        when(memberRepository.existsByEmail("tester@example.com")).thenReturn(true);

        boolean available = memberService.isEmailAvailable("tester@example.com");

        assertThat(available).isFalse();
    }

    @Test
    void isPhoneAvailableReturnsFalseWhenPhoneExists() {
        when(memberRepository.existsByPhone("010-1234-5678")).thenReturn(true);

        boolean available = memberService.isPhoneAvailable("010-1234-5678");

        assertThat(available).isFalse();
    }

    @Test
    void signupRejectsDuplicatePhone() {
        MemberSignupRequest request = new MemberSignupRequest();
        request.setMemberId("user01");
        request.setPassword("password123");
        request.setPasswordConfirm("password123");
        request.setName("Tester");
        request.setPhone("010-1234-5678");
        request.setEmail("tester@example.com");

        when(memberRepository.existsByPhone("010-1234-5678")).thenReturn(true);

        assertThatThrownBy(() -> memberService.signup(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DUPLICATE_MEMBER_PHONE);
    }

    @Test
    void changePasswordRejectsWhenConfirmationDoesNotMatch() {
        Member member = createMember("user01", MemberRole.USER, MemberStatus.ACTIVE);
        MemberPasswordChangeRequest request = new MemberPasswordChangeRequest();
        request.setCurrentPassword("password123");
        request.setNewPassword("newpassword123");
        request.setNewPasswordConfirm("different123");

        when(memberRepository.findById("user01")).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> memberService.changePassword("user01", request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PASSWORD_CONFIRM_NOT_MATCH);
    }

    @Test
    void changeMemberRoleByAdminRejectsNonDesignerRoleForLinkedDesignerAccount() {
        Member targetMember = createMember("designer01", MemberRole.DESIGNER, MemberStatus.ACTIVE);

        when(memberRepository.findById("designer01")).thenReturn(Optional.of(targetMember));
        when(designerRepository.existsByMember_MemberId("designer01")).thenReturn(true);

        assertThatThrownBy(() -> memberService.changeMemberRoleByAdmin("admin01", "designer01", MemberRole.USER))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONNECTED_DESIGNER_ACCOUNT_ROLE_CHANGE_NOT_ALLOWED);
    }

    @Test
    void softDeleteSelfMarksMemberAsDeleted() {
        Member member = createMember("user01", MemberRole.USER, MemberStatus.ACTIVE);
        when(memberRepository.findById("user01")).thenReturn(Optional.of(member));

        memberService.softDeleteSelf("user01");

        assertThat(member.getStatus()).isEqualTo(MemberStatus.DELETED);
    }

    @Test
    void softDeleteByAdminDeletesMemberFromRepository() {
        Member member = createMember("user01", MemberRole.USER, MemberStatus.ACTIVE);
        when(memberRepository.findById("user01")).thenReturn(Optional.of(member));

        memberService.softDeleteByAdmin("admin01", "user01");

        verify(memberRepository).delete(member);
    }

    @Test
    void softDeleteByAdminDeletesAlreadyDeletedMemberFromRepository() {
        Member member = createMember("user01", MemberRole.USER, MemberStatus.DELETED);
        when(memberRepository.findById("user01")).thenReturn(Optional.of(member));

        memberService.softDeleteByAdmin("admin01", "user01");

        verify(memberRepository).delete(member);
    }

    @Test
    void changeStatusByAdminRejectsDeletedMember() {
        Member deletedMember = createMember("user01", MemberRole.USER, MemberStatus.DELETED);
        when(memberRepository.findById("user01")).thenReturn(Optional.of(deletedMember));

        assertThatThrownBy(() -> memberService.changeStatusByAdmin("admin01", "user01", MemberStatus.ACTIVE))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DELETED_MEMBER_MODIFICATION_NOT_ALLOWED);
    }

    @Test
    void changeMemberRoleByAdminRejectsChangingLastAdminRole() {
        Member adminMember = createMember("admin01", MemberRole.ADMIN, MemberStatus.ACTIVE);

        when(memberRepository.findById("admin01")).thenReturn(Optional.of(adminMember));
        when(memberRepository.countByRole(MemberRole.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> memberService.changeMemberRoleByAdmin("root", "admin01", MemberRole.USER))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LAST_ADMIN_ROLE_CANNOT_BE_CHANGED);
    }

    private Member createMember(String memberId, MemberRole role, MemberStatus status) {
        return Member.builder()
                .memberId(memberId)
                .password("encoded-password")
                .name("Tester")
                .phone("010-1234-5678")
                .email(memberId + "@example.com")
                .role(role)
                .status(status)
                .build();
    }
}
