package com.hairsalonproject2.member.service;

import com.hairsalonproject2.common.constant.MemberRole;
import com.hairsalonproject2.common.constant.MemberStatus;
import com.hairsalonproject2.designer.entity.Designer;
import com.hairsalonproject2.designer.repository.DesignerRepository;
import com.hairsalonproject2.member.entity.Member;
import com.hairsalonproject2.member.repository.MemberRepository;
import com.hairsalonproject2.salon.entity.Salon;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DummyMemberSeederTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private DesignerRepository designerRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private DummyMemberSeeder dummyMemberSeeder;

    @Test
    void runAddsConfiguredBatchCounts() throws Exception {
        ReflectionTestUtils.setField(dummyMemberSeeder, "enabled", true);
        ReflectionTestUtils.setField(dummyMemberSeeder, "defaultPassword", "12341234");
        ReflectionTestUtils.setField(dummyMemberSeeder, "batchDesignerCount", 3);
        ReflectionTestUtils.setField(dummyMemberSeeder, "batchUserCount", 4);

        when(passwordEncoder.encode("12341234")).thenReturn("encoded");
        when(memberRepository.findAllByMemberIdStartingWith("designer"))
                .thenReturn(List.of(existingMember("designer5")))
                .thenReturn(List.of(existingMember("designer5"), existingMember("designer6")))
                .thenReturn(List.of(existingMember("designer5"), existingMember("designer6"), existingMember("designer7")));
        when(memberRepository.findAllByMemberIdStartingWith("user"))
                .thenReturn(List.of(existingMember("user7")));
        when(memberRepository.existsByMemberId(any())).thenReturn(false);
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(designerRepository.findAllByMemberIsNull()).thenReturn(List.of(
                unlinkedDesigner(1, 1),
                unlinkedDesigner(2, 2),
                unlinkedDesigner(3, 3)
        ));
        when(designerRepository.existsBySalonSalonIdAndMemberIsNotNull(any())).thenReturn(false);

        dummyMemberSeeder.run(null);

        ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository, times(7)).save(memberCaptor.capture());
        assertThat(memberCaptor.getAllValues()).extracting(Member::getMemberId)
                .contains("designer6", "designer7", "designer8", "user8", "user9", "user10", "user11");
        assertThat(memberCaptor.getAllValues()).extracting(Member::getName)
                .contains("디자이너6", "디자이너7", "디자이너8", "회원8", "회원9", "회원10", "회원11");
    }

    @Test
    void runSkipsWhenSeederDisabled() throws Exception {
        ReflectionTestUtils.setField(dummyMemberSeeder, "enabled", false);

        dummyMemberSeeder.run(null);

        verify(memberRepository, never()).save(any());
    }

    private Member existingMember(String memberId) {
        return Member.builder()
                .memberId(memberId)
                .role(memberId.startsWith("designer") ? MemberRole.DESIGNER : MemberRole.USER)
                .status(MemberStatus.ACTIVE)
                .build();
    }

    private Designer unlinkedDesigner(Integer designerId, Integer salonId) {
        return Designer.builder()
                .designerId(designerId)
                .name("Designer" + designerId)
                .salon(Salon.builder().salonId(salonId).name("Salon" + salonId).build())
                .careerYears(designerId)
                .build();
    }
}
