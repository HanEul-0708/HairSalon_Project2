package com.hairsalonproject2.salon.service;

import com.hairsalonproject2.designer.constant.DesignerSpecialty;
import com.hairsalonproject2.designer.entity.Designer;
import com.hairsalonproject2.salon.entity.Salon;
import com.hairsalonproject2.salonservice.entity.SalonService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SalonSeedDataFactoryTest {

    private final SalonSeedDataFactory factory = new SalonSeedDataFactory();

    @Test
    void createDesignersGeneratesCareerSpecialtyBasedProfiles() {
        Salon salon = Salon.builder()
                .externalId("kakao-100")
                .name("시드헤어")
                .address("서울 강남")
                .build();

        List<Designer> designers = factory.createDesigners(salon);

        assertThat(designers).hasSize(3);
        assertThat(designers).extracting(Designer::getName)
                .containsExactly(
                        designers.get(0).getName(),
                        designers.get(1).getName(),
                        designers.get(2).getName()
                );
        assertThat(designers.get(0).getName()).endsWith("점장");
        assertThat(designers.get(1).getName()).endsWith("부점장");
        assertThat(designers.get(2).getName()).endsWith("디자이너");
        assertThat(designers).allSatisfy(designer -> {
            assertThat(designer.getCareerYears()).isBetween(1, 10);
            assertThat(designer.getSpecialty()).isIn((Object[]) DesignerSpecialty.values());
            assertThat(designer.getIntroduction()).contains("경력");
            assertThat(designer.getIntroduction()).contains("전문 디자이너");
            assertThat(designer.getIntroduction()).containsAnyOf("커트", "펌", "염색", "스타일링");
        });
    }

    @Test
    void createServicesUsesRuleBasedPricing() {
        Salon salon = Salon.builder()
                .externalId("kakao-101")
                .name("시드헤어")
                .address("서울 강남")
                .build();

        List<SalonService> services = factory.createServices(salon);

        assertThat(services).hasSize(5);
        assertThat(services).extracting(SalonService::getName)
                .containsExactly("여성 커트", "남성 커트", "염색", "펌", "스타일링");
        assertThat(services).allSatisfy(service -> {
            assertThat(service.getPrice()).isPositive();
            assertThat(service.getDescription()).contains("시술");
        });
    }
}
