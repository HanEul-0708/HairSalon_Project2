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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
    void recommendedSearchReturnsMatchedSalonsWithoutCoordinateFiltering() {
        SalonSearchRequest request = new SalonSearchRequest();

        Salon firstSalon = Salon.builder()
                .salonId(1)
                .name("First Salon")
                .address("Seoul Jung-gu")
                .reservable(true)
                .build();

        Salon secondSalon = Salon.builder()
                .salonId(2)
                .name("Second Salon")
                .address("Seoul Gangnam-gu")
                .reservable(true)
                .build();

        when(salonRepository.findAll(any(Specification.class))).thenReturn(List.of(firstSalon, secondSalon));

        List<SalonSummaryResponse> results = salonQueryService.search(request);

        assertThat(results).extracting(SalonSummaryResponse::getSalonId).containsExactly(1, 2);
    }

    @Test
    void keywordSearchUsesSalonServiceKeywordRepository() {
        SalonSearchRequest request = new SalonSearchRequest();
        request.setKeyword("perm");
        request.setServiceKeywordSearchEnabled(true);

        Salon salon = Salon.builder()
                .salonId(10)
                .name("Keyword Salon")
                .address("Seoul Seocho-gu")
                .reservable(true)
                .build();

        when(salonServiceRepository.findDistinctSalonIdsByKeyword("perm")).thenReturn(List.of(10));
        when(salonRepository.findAll(any(Specification.class))).thenReturn(List.of(salon));

        List<SalonSummaryResponse> results = salonQueryService.search(request);

        verify(salonServiceRepository).findDistinctSalonIdsByKeyword("perm");
        assertThat(results).hasSize(1);
    }

    @Test
    void defaultKeywordSearchDoesNotUseSalonServiceKeywordRepository() {
        SalonSearchRequest request = new SalonSearchRequest();
        request.setKeyword("gangnam");

        Salon salon = Salon.builder()
                .salonId(20)
                .name("Gangnam Salon")
                .address("Seoul Gangnam-gu")
                .reservable(true)
                .build();

        when(salonRepository.findAll(any(Specification.class))).thenReturn(List.of(salon));

        List<SalonSummaryResponse> results = salonQueryService.search(request);

        verify(salonServiceRepository, never()).findDistinctSalonIdsByKeyword(any());
        assertThat(results).hasSize(1);
    }
}
