package com.hairsalonproject2.salon.service;

import com.hairsalonproject2.common.integration.kakao.KakaoAddressSearchResult;
import com.hairsalonproject2.common.integration.kakao.KakaoLocalSearchClient;
import com.hairsalonproject2.designer.repository.DesignerRepository;
import com.hairsalonproject2.member.repository.MemberRepository;
import com.hairsalonproject2.review.repository.ReviewRepository;
import com.hairsalonproject2.salon.dto.request.SalonCreateRequest;
import com.hairsalonproject2.salon.entity.Salon;
import com.hairsalonproject2.salon.repository.SalonLikeRepository;
import com.hairsalonproject2.salon.repository.SalonRepository;
import com.hairsalonproject2.salonservice.repository.SalonServiceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

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

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private KakaoLocalSearchClient kakaoLocalSearchClient;

    @InjectMocks
    private SalonQueryService salonQueryService;

    @Test
    void createFillsMissingCoordinatesFromKakaoAddressSearch() {
        SalonCreateRequest request = new SalonCreateRequest();
        request.setName("Test Hair");
        request.setAddress("서울 강남구 역삼동 1");

        when(kakaoLocalSearchClient.searchAddress("서울 강남구 역삼동 1"))
                .thenReturn(Optional.of(KakaoAddressSearchResult.builder()
                        .addressName("서울 강남구 역삼동 1")
                        .roadAddressName("서울 강남구 테헤란로 1")
                        .latitude(new BigDecimal("37.1234567"))
                        .longitude(new BigDecimal("127.1234567"))
                        .build()));
        when(salonRepository.save(any(Salon.class))).thenAnswer(invocation -> {
            Salon salon = invocation.getArgument(0);
            salon.setSalonId(1);
            return salon;
        });

        Integer salonId = salonQueryService.create(request);

        ArgumentCaptor<Salon> salonCaptor = ArgumentCaptor.forClass(Salon.class);
        verify(salonRepository).save(salonCaptor.capture());
        assertThat(salonId).isEqualTo(1);
        assertThat(salonCaptor.getValue().getRoadAddress()).isEqualTo("서울 강남구 테헤란로 1");
        assertThat(salonCaptor.getValue().getLatitude()).isEqualByComparingTo(new BigDecimal("37.1234567"));
        assertThat(salonCaptor.getValue().getLongitude()).isEqualByComparingTo(new BigDecimal("127.1234567"));
    }

    @Test
    void createDoesNotSearchAddressWhenCoordinatesAlreadyExist() {
        SalonCreateRequest request = new SalonCreateRequest();
        request.setName("Test Hair");
        request.setAddress("서울 강남구 역삼동 1");
        request.setLatitude(new BigDecimal("37.1234567"));
        request.setLongitude(new BigDecimal("127.1234567"));

        when(salonRepository.save(any(Salon.class))).thenAnswer(invocation -> {
            Salon salon = invocation.getArgument(0);
            salon.setSalonId(1);
            return salon;
        });

        salonQueryService.create(request);

        verify(kakaoLocalSearchClient, never()).searchAddress(any());
    }
}
