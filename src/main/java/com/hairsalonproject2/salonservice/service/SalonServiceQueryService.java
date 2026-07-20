package com.hairsalonproject2.salonservice.service;

import com.hairsalonproject2.salon.entity.Salon;
import com.hairsalonproject2.salon.repository.SalonRepository;
import com.hairsalonproject2.salonservice.dto.request.SalonServiceCreateRequest;
import com.hairsalonproject2.salonservice.dto.request.SalonServiceSearchRequest;
import com.hairsalonproject2.salonservice.dto.request.SalonServiceUpdateRequest;
import com.hairsalonproject2.salonservice.dto.response.SalonServiceDetailResponse;
import com.hairsalonproject2.salonservice.dto.response.SalonServiceSummaryResponse;
import com.hairsalonproject2.salonservice.dto.response.ServicePriceCompareResponse;
import com.hairsalonproject2.salonservice.entity.SalonService;
import com.hairsalonproject2.salonservice.repository.SalonServiceRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalonServiceQueryService {
 private final SalonServiceRepository salonServiceRepository;
 private final SalonRepository salonRepository;

 public List<SalonServiceSummaryResponse> list(SalonServiceSearchRequest request) {
  SalonServiceSearchRequest safeRequest = request == null ? new SalonServiceSearchRequest() : request;
  return list(safeRequest, 0, Integer.MAX_VALUE).getContent();
 }

 public Page<SalonServiceSummaryResponse> list(SalonServiceSearchRequest request, int page, int size) {
  SalonServiceSearchRequest safeRequest = request == null ? new SalonServiceSearchRequest() : request;
  List<SalonServiceSummaryResponse> searchResults = searchServices(safeRequest);
  Map<String, Long> trendFrequency = buildTrendFrequency(searchResults);
  List<SalonServiceSummaryResponse> filtered = searchResults.stream().sorted(serviceComparator(safeRequest.getSortBy(), trendFrequency)).toList(); int safeSize = Math.max(size, 1); int maxPage = filtered.isEmpty() ? 0 : (filtered.size() - 1) / safeSize; int safePage = Math.min(Math.max(page, 0), maxPage); int fromIndex = Math.min(safePage * safeSize, filtered.size());
  int toIndex = Math.min(fromIndex + safeSize, filtered.size());
  return new PageImpl<>(filtered.subList(fromIndex, toIndex), PageRequest.of(safePage, safeSize), filtered.size());
 }//입력된 검색 파라미터(키워드, 미용실명, 지역, 상한 가격, 상한 시간)를 결합하여 다차원 동적 필터링 조회를 수행합니다. 조회된 결과 내에서 시술명의 출현 빈도를 실시간 계산해 트렌드 점수를 산출하며, 요청된 정렬 기준(가격순, 시간순, 평점순, 이름순, 트렌드순)에 따라 컬렉션을 재정렬합니다. 시작 인덱스와 끝 인덱스의 경계값을 검증 및 보정한 후 가공된 서브리스트를 Spring Data의 페이징 규격 객체(PageImpl)로 캡슐화하여 서빙합니다.

 public SalonServiceDetailResponse getDetail(Integer serviceId) {
  SalonService service = salonServiceRepository.findById(serviceId).orElseThrow(() -> new EntityNotFoundException("SalonService not found: " + serviceId)); return toDetail(service);
 }//다건 조회가 아닌 데이터베이스 내의 단일 프라이머리 키(serviceId) 매칭에만 집중합니다. 엔티티 부재 시 시스템 노이즈를 방지하기 위해 EntityNotFoundException 예외를 명시적으로 발생시키는 단독 예외 처리 방어선 역할을 합니다. 시술에 대한 기본 정보 외에도 연계된 미용실의 고유 ID 및 명칭을 결합한 상세 전송 객체(SalonServiceDetailResponse)를 빌드하여 반환합니다.

 public List<ServicePriceCompareResponse> compare(String serviceName, String region) {
  return salonServiceRepository.compareServices(serviceName, region).stream().map(row -> ServicePriceCompareResponse.builder().serviceId(row.getServiceId()).salonId(row.getSalonId()).salonName(row.getSalonName()).address(row.getAddress()).serviceName(row.getServiceName()).price(row.getPrice()).duration(row.getDuration()).averageRating(row.getAverageRating()).build()).toList();
 }//개별 ID가 아닌 텍스트 기반의 시술 명칭(serviceName)과 행정 구역명(region)만을 바인딩 파라미터로 사용하여 리포지토리의 커스텀 집계 메서드를 호출합니다. 목록 조회 메서드와 달리 내부적인 트렌드 빈도수 계산, 별도 조건 분기 정렬 로직 및 페이징 처리를 일절 배제하고 데이터베이스에서 추출된 비교 레코드셋 전체를 일괄 취합합니다. 각 가맹점별 단가(price), 시술 소요 시간(duration), 미용실 평균 평점(averageRating)을 한눈에 대조할 수 있도록 특화된 데이터 구조(ServicePriceCompareResponse)의 플랫 리스트를 서빙합니다.

 @Transactional
 public Integer create(SalonServiceCreateRequest request) {
  Salon salon = salonRepository.findById(request.getSalonId()).orElseThrow(() -> new EntityNotFoundException("Salon not found: " + request.getSalonId()));
  SalonService service = SalonService.builder().salon(salon).name(request.getName()).price(request.getPrice()).duration(request.getDuration()).description(request.getDescription()).build();
  return salonServiceRepository.save(service).getServiceId();
 }

 @Transactional
 public void update(Integer serviceId, SalonServiceUpdateRequest request) {
  SalonService service = salonServiceRepository.findById(serviceId).orElseThrow(() -> new EntityNotFoundException("SalonService not found: " + serviceId));
  Salon salon = salonRepository.findById(request.getSalonId()).orElseThrow(() -> new EntityNotFoundException("Salon not found: " + request.getSalonId()));
  service.setSalon(salon);
  service.setName(request.getName());
  service.setPrice(request.getPrice());
  service.setDuration(request.getDuration());
  service.setDescription(request.getDescription());
 }

 @Transactional
 public void delete(Integer serviceId) {
  salonServiceRepository.deleteById(serviceId);
 }

 private SalonServiceSummaryResponse toSummary(SalonService service) {
  return SalonServiceSummaryResponse.builder().serviceId(service.getServiceId()).salonId(service.getSalon().getSalonId()).salonName(service.getSalon().getName()).address(service.getSalon().getAddress()).name(service.getName()).price(service.getPrice()).duration(service.getDuration()).averageRating(service.getSalon().getAverageRating() == null ? 0 : service.getSalon().getAverageRating().intValue()).description(service.getDescription()).build();
 }

 private SalonServiceDetailResponse toDetail(SalonService service) {
  return SalonServiceDetailResponse.builder().serviceId(service.getServiceId()).salonId(service.getSalon().getSalonId()).salonName(service.getSalon().getName()).name(service.getName()).price(service.getPrice()).duration(service.getDuration()).description(service.getDescription()).build();
 }

 private Comparator<SalonServiceSummaryResponse> serviceComparator(String sortBy, Map<String, Long> trendFrequency) {
  if ("duration".equalsIgnoreCase(sortBy)) return Comparator.comparing(SalonServiceSummaryResponse::getDuration).thenComparing(SalonServiceSummaryResponse::getPrice).thenComparing(SalonServiceSummaryResponse::getName, String.CASE_INSENSITIVE_ORDER);
  if ("rating".equalsIgnoreCase(sortBy)) return Comparator.comparing(SalonServiceSummaryResponse::getAverageRating, Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(SalonServiceSummaryResponse::getPrice).thenComparing(SalonServiceSummaryResponse::getName, String.CASE_INSENSITIVE_ORDER);
  if ("name".equalsIgnoreCase(sortBy)) return Comparator.comparing(SalonServiceSummaryResponse::getName, String.CASE_INSENSITIVE_ORDER).thenComparing(SalonServiceSummaryResponse::getPrice);
  if ("trend".equalsIgnoreCase(sortBy)) return Comparator.comparing((SalonServiceSummaryResponse service) -> trendFrequency.getOrDefault(normalizeServiceName(service.getName()), 0L), Comparator.reverseOrder()).thenComparing(SalonServiceSummaryResponse::getAverageRating, Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(SalonServiceSummaryResponse::getPrice).thenComparing(SalonServiceSummaryResponse::getName, String.CASE_INSENSITIVE_ORDER);
  return Comparator.comparing(SalonServiceSummaryResponse::getPrice).thenComparing(SalonServiceSummaryResponse::getDuration).thenComparing(SalonServiceSummaryResponse::getName, String.CASE_INSENSITIVE_ORDER);
 }

 private List<SalonServiceSummaryResponse> searchServices(SalonServiceSearchRequest request) {
  return salonServiceRepository.searchServices(request.getKeyword(), request.getSalonKeyword(), request.getRegion(), request.getMaxPrice(), request.getMaxDuration()).stream().map(this::toSummary).toList();
 }

 private Map<String, Long> buildTrendFrequency(List<SalonServiceSummaryResponse> services) {
  return services.stream().collect(Collectors.groupingBy(service -> normalizeServiceName(service.getName()), Collectors.counting()));
 }

 private String normalizeServiceName(String name) {
  if (name == null) {
   return "";
  }
  return name.trim().replaceAll("\\s+", " ").toLowerCase();
 }
}
