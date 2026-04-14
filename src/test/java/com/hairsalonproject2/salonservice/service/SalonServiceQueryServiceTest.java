package com.hairsalonproject2.salonservice.service;

import com.hairsalonproject2.salon.entity.Salon;
import com.hairsalonproject2.salon.repository.SalonRepository;
import com.hairsalonproject2.salonservice.dto.request.SalonServiceSearchRequest;
import com.hairsalonproject2.salonservice.entity.SalonService;
import com.hairsalonproject2.salonservice.repository.SalonServiceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalonServiceQueryServiceTest {

    @Mock
    private SalonServiceRepository salonServiceRepository;

    @Mock
    private SalonRepository salonRepository;

    @InjectMocks
    private SalonServiceQueryService salonServiceQueryService;

    @Test
    void keywordFiltersByServiceNameOnly() {
        Salon salon = Salon.builder()
                .salonId(1)
                .name("수영 헤어")
                .address("부산광역시 수영구 광안동")
                .averageRating(BigDecimal.valueOf(4.7))
                .build();
        SalonService perm = SalonService.builder()
                .serviceId(1)
                .salon(salon)
                .name("펌")
                .price(80000)
                .duration(150)
                .description("펌 시술입니다.")
                .build();
        SalonService colorByPermDesigner = SalonService.builder()
                .serviceId(2)
                .salon(salon)
                .name("염색")
                .price(65000)
                .duration(120)
                .description("펌 전문 디자이너가 진행하는 염색 시술입니다.")
                .build();
        SalonService cutByPermDesigner = SalonService.builder()
                .serviceId(3)
                .salon(salon)
                .name("여성 커트")
                .price(30000)
                .duration(60)
                .description("펌 전문 디자이너가 진행하는 커트 시술입니다.")
                .build();

        when(salonServiceRepository.searchServices(
                eq("펌"), any(), any(), eq("부산"), eq("수영구"), any(), any(), any()))
                .thenReturn(List.of(perm, colorByPermDesigner, cutByPermDesigner));

        SalonServiceSearchRequest request = new SalonServiceSearchRequest();
        request.setKeyword("펌");
        request.setCity("부산");
        request.setDistrict("수영구");

        var result = salonServiceQueryService.list(request, 0, 10);

        assertThat(result.getContent())
                .extracting("name")
                .containsExactly("펌");
    }
}
