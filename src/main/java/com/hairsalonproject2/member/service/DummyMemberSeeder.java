package com.hairsalonproject2.member.service;

import com.hairsalonproject2.common.constant.MemberRole;
import com.hairsalonproject2.common.constant.MemberStatus;
import com.hairsalonproject2.member.entity.Member;
import com.hairsalonproject2.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class DummyMemberSeeder implements ApplicationRunner {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.members.enabled:false}")
    private boolean enabled;

    @Value("${app.seed.members.default-password:1234}")
    private String defaultPassword;

    @Value("${app.seed.members.batch-designer-count:${app.seed.members.target-designer-count:20}}")
    private int batchDesignerCount;

    @Value("${app.seed.members.batch-user-count:${app.seed.members.target-user-count:40}}")
    private int batchUserCount;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        seedMembers();
    }

    @Transactional
    public int seedMembers() {
        String encodedPassword = passwordEncoder.encode(defaultPassword);
        int created = 0;
        created += seedRole(MemberRole.DESIGNER, "designer", "디자이너", batchDesignerCount, encodedPassword, 2000);
        created += seedRole(MemberRole.USER, "user", "회원", batchUserCount, encodedPassword, 3000);

        if (created > 0) {
            log.info("Seeded {} dummy members for local profile.", created);
        }
        return created;
    }

    private int seedRole(MemberRole role,
                         String idPrefix,
                         String namePrefix,
                         int batchCount,
                         String encodedPassword,
                         int phoneBase) {
        if (batchCount <= 0) {
            return 0;
        }

        int sequence = nextSequence(idPrefix);
        int created = 0;
        while (created < batchCount) {
            String memberId = idPrefix + sequence;
            if (memberRepository.existsByMemberId(memberId)) {
                sequence++;
                continue;
            }

            Member member = Member.builder()
                    .memberId(memberId)
                    .password(encodedPassword)
                    .name(namePrefix + sequence)
                    .phone(buildPhone(phoneBase + sequence))
                    .email(memberId + "@example.com")
                    .role(role)
                    .status(MemberStatus.ACTIVE)
                    .build();
            memberRepository.save(member);
            created++;
            sequence++;
        }
        return created;
    }

    private int nextSequence(String idPrefix) {
        return memberRepository.findAllByMemberIdStartingWith(idPrefix).stream()
                .map(Member::getMemberId)
                .map(memberId -> memberId.substring(idPrefix.length()))
                .filter(suffix -> !suffix.isBlank())
                .mapToInt(this::parseSequence)
                .max()
                .orElse(0) + 1;
    }

    private int parseSequence(String suffix) {
        try {
            return Integer.parseInt(suffix);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String buildPhone(int number) {
        int middle = (number / 10000) % 10000;
        int last = number % 10000;
        return String.format("010-%04d-%04d", middle, last);
    }
}
