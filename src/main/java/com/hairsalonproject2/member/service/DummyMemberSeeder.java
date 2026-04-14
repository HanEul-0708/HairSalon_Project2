package com.hairsalonproject2.member.service;

import com.hairsalonproject2.common.constant.MemberRole;
import com.hairsalonproject2.common.constant.MemberStatus;
import com.hairsalonproject2.member.entity.Member;
import com.hairsalonproject2.designer.entity.Designer;
import com.hairsalonproject2.member.repository.MemberRepository;
import com.hairsalonproject2.designer.repository.DesignerRepository;
import com.hairsalonproject2.salon.entity.Salon;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class DummyMemberSeeder implements ApplicationRunner {

    private final MemberRepository memberRepository;
    private final DesignerRepository designerRepository;
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
        created += seedDesignerMembers(encodedPassword, batchDesignerCount);
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

    private int seedDesignerMembers(String encodedPassword, int batchDesignerCount) {
        List<Designer> unlinkedDesigners = designerRepository.findAllByMemberIsNull();

        // 미용실별로 "경력이 가장 높은 디자이너" 1명만 뽑기
        Map<Integer, Designer> representativeBySalon = new LinkedHashMap<>();

        for (Designer designer : unlinkedDesigners) {
            Salon salon = designer.getSalon();
            if (salon == null || salon.getSalonId() == null) {
                continue;
            }

            Integer salonId = salon.getSalonId();

            // 이미 그 미용실에 계정 연결된 디자이너가 있으면 제외
            boolean alreadyLinked = designerRepository.existsBySalonSalonIdAndMemberIsNotNull(salonId);
            if (alreadyLinked) {
                continue;
            }

            Designer current = representativeBySalon.get(salonId);

            if (current == null || isHigherCareer(designer, current)) {
                representativeBySalon.put(salonId, designer);
            }
        }

        List<Designer> targets = representativeBySalon.values().stream()
                .limit(batchDesignerCount)
                .toList();

        int created = 0;

        for (Designer designer : targets) {
            Integer sequence = nextSequence("designer");
            String memberId = "designer" + sequence;

            Member member = Member.builder()
                    .memberId(memberId)
                    .password(encodedPassword)
                    .name("디자이너" + sequence)
                    .phone(buildPhone(2000 + sequence))
                    .email(memberId + "@example.com")
                    .role(MemberRole.DESIGNER)
                    .status(MemberStatus.ACTIVE)
                    .build();

            Member savedMember = memberRepository.save(member);

            designer.setMember(savedMember);
            created++;
        }

        return created;
    }

    private boolean isHigherCareer(Designer candidate, Designer current) {
        int candidateYears = safeCareerYears(candidate);
        int currentYears = safeCareerYears(current);

        if (candidateYears != currentYears) {
            return candidateYears > currentYears;
        }

        // 경력이 같으면 id가 작은 쪽을 우선
        if (candidate.getDesignerId() == null) {
            return false;
        }
        if (current.getDesignerId() == null) {
            return true;
        }

        return candidate.getDesignerId() < current.getDesignerId();
    }

    private int safeCareerYears(Designer designer) {
        if (designer == null || designer.getCareerYears() == null) {
            return 0;
        }
        return designer.getCareerYears();
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
