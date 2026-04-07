package com.hairsalonproject2.salon.service;

import com.hairsalonproject2.designer.repository.DesignerRepository;
import com.hairsalonproject2.member.repository.MemberRepository;
import com.hairsalonproject2.salon.dto.request.SalonSearchRequest;
import com.hairsalonproject2.salon.dto.response.SalonSummaryResponse;
import com.hairsalonproject2.salon.entity.Salon;
import com.hairsalonproject2.salon.repository.SalonLikeRepository;
import com.hairsalonproject2.salon.repository.SalonRepository;
import com.hairsalonproject2.salonservice.repository.SalonServiceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalonQueryServiceTest {

    @Mock
    private SalonRepository salonRepository;

    @Mock
    private DesignerRepository designerRepository;

    @Mock
    private SalonServiceRepository salonServiceRepository;

    @Mock
    private SalonLikeRepository salonLikeRepository;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private SalonQueryService salonQueryService;

    @Test
    void distanceSortExcludesSalonsWithoutCoordinates() {
        SalonSearchRequest request = new SalonSearchRequest();
        request.setLatitude(BigDecimal.valueOf(37.5665));
        request.setLongitude(BigDecimal.valueOf(126.9780));
        request.setSort("distance");

        Salon nearbySalon = Salon.builder()
                .salonId(1)
                .name("가까운 살롱")
                .address("서울 중구")
                .latitude(BigDecimal.valueOf(37.5670))
                .longitude(BigDecimal.valueOf(126.9785))
                .reservable(true)
                .build();

        Salon noCoordinateSalon = Salon.builder()
                .salonId(2)
                .name("좌표 없는 살롱")
                .address("서울 강남구")
                .reservable(true)
                .build();

        when(salonRepository.findAll(any(Specification.class))).thenReturn(List.of(nearbySalon, noCoordinateSalon));

        List<SalonSummaryResponse> results = salonQueryService.search(request);

        assertThat(results).extracting(SalonSummaryResponse::getSalonId).containsExactly(1);
    }

    @Test
    void keywordSearchUsesSalonServiceKeywordRepository() {
        SalonSearchRequest request = new SalonSearchRequest();
        request.setKeyword("펌");

        Salon salon = Salon.builder()
                .salonId(10)
                .name("펌 전문 살롱")
                .address("서울 서초구")
                .reservable(true)
                .build();

        when(salonServiceRepository.findDistinctSalonIdsByKeyword("펌")).thenReturn(List.of(10));
        when(salonRepository.findAll(any(Specification.class))).thenReturn(List.of(salon));

        List<SalonSummaryResponse> results = salonQueryService.search(request);

        verify(salonServiceRepository).findDistinctSalonIdsByKeyword("펌");
        assertThat(results).hasSize(1);
    }
}
