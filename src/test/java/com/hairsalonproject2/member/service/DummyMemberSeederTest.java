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
import org.springframework.test.util.ReflectionTestUtils;

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
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private DummyMemberSeeder dummyMemberSeeder;

    @Test
    void runSeedsMissingMembersUpToTargetCounts() throws Exception {
        ReflectionTestUtils.setField(dummyMemberSeeder, "enabled", true);
        ReflectionTestUtils.setField(dummyMemberSeeder, "defaultPassword", "12341234");
        ReflectionTestUtils.setField(dummyMemberSeeder, "targetDesignerCount", 3);
        ReflectionTestUtils.setField(dummyMemberSeeder, "targetUserCount", 4);

        when(passwordEncoder.encode("12341234")).thenReturn("encoded");
        when(memberRepository.countByRoleAndStatus(MemberRole.DESIGNER, MemberStatus.ACTIVE)).thenReturn(1L);
        when(memberRepository.countByRoleAndStatus(MemberRole.USER, MemberStatus.ACTIVE)).thenReturn(2L);
        when(memberRepository.existsByMemberId(any())).thenReturn(false);

        dummyMemberSeeder.run(null);

        ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository, times(4)).save(memberCaptor.capture());
        assertThat(memberCaptor.getAllValues()).extracting(Member::getMemberId)
                .contains("designer1", "designer2", "user1", "user2");
        assertThat(memberCaptor.getAllValues()).extracting(Member::getName)
                .contains("디자이너1", "디자이너2", "회원1", "회원2");
    }

    @Test
    void runSkipsWhenSeederDisabled() throws Exception {
        ReflectionTestUtils.setField(dummyMemberSeeder, "enabled", false);

        dummyMemberSeeder.run(null);

        verify(memberRepository, never()).save(any());
    }
}
