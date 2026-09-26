package com.hairsalonproject2.salonservice.service;

import com.hairsalonproject2.common.catalog.CatalogIntegrityService;
import com.hairsalonproject2.salon.entity.Salon;
import com.hairsalonproject2.salon.repository.SalonRepository;
import com.hairsalonproject2.salonservice.dto.request.SalonServiceCreateRequest;
import com.hairsalonproject2.salonservice.dto.request.SalonServiceSearchRequest;
import com.hairsalonproject2.salonservice.dto.request.SalonServiceUpdateRequest;
import com.hairsalonproject2.salonservice.dto.response.SalonServiceDetailResponse;
import com.hairsalonproject2.salonservice.dto.response.SalonServiceSummaryResponse;
import com.hairsalonproject2.salonservice.dto.response.ServicePriceCompareResponse;
import com.hairsalonproject2.salonservice.entity.SalonService;
import com.hairsalonproject2.salonservice.projection.ServicePriceCompareRow;
import com.hairsalonproject2.salonservice.repository.SalonServiceRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalonServiceQueryService {
 private final SalonServiceRepository salonServiceRepository;
 private final SalonRepository salonRepository;
 private final CatalogIntegrityService catalogIntegrityService;

 public List<SalonServiceSummaryResponse> list(SalonServiceSearchRequest request) {
  SalonServiceSearchRequest safeRequest = request == null ? new SalonServiceSearchRequest() : request;
  List<SalonServiceSummaryResponse> results = searchServices(safeRequest);
  return results.stream().sorted(serviceComparator(normalizeSort(safeRequest.getSortBy()), buildTrendFrequency(results))).toList();
 }

 public Page<SalonServiceSummaryResponse> list(SalonServiceSearchRequest request, int page, int size) {
  SalonServiceSearchRequest safeRequest = request == null ? new SalonServiceSearchRequest() : request;
  String sort = normalizeSort(safeRequest.getSortBy());
  if (!"trend".equals(sort)) {
   return fetchPage(page, size, pageable -> salonServiceRepository.searchServices(
           normalizeFilter(safeRequest.getKeyword()), normalizeFilter(safeRequest.getSalonKeyword()),
           normalizeFilter(safeRequest.getRegion()), safeRequest.getMaxPrice(), safeRequest.getMaxDuration(),
           sort, pageable)).map(this::toSummary);
  }
  List<SalonServiceSummaryResponse> searchResults = searchServices(safeRequest);
  Map<String, Long> trendFrequency = buildTrendFrequency(searchResults);
  List<SalonServiceSummaryResponse> filtered = searchResults.stream().sorted(serviceComparator(sort, trendFrequency)).toList();
  int safeSize = Math.max(size, 1);
  int maxPage = filtered.isEmpty() ? 0 : (filtered.size() - 1) / safeSize;
  int safePage = Math.min(Math.max(page, 0), maxPage);
  int fromIndex = Math.min(safePage * safeSize, filtered.size());
  int toIndex = (int) Math.min((long) fromIndex + safeSize, filtered.size());
  return new PageImpl<>(filtered.subList(fromIndex, toIndex), PageRequest.of(safePage, safeSize), filtered.size());
 }

 public SalonServiceDetailResponse getDetail(Integer serviceId) {
  SalonService service = salonServiceRepository.findById(serviceId).orElseThrow(() -> new EntityNotFoundException("SalonService not found: " + serviceId));
  return toDetail(service);
 }

 public boolean existsSalon(Integer salonId) {
  return salonId != null && salonId > 0 && salonRepository.existsById(salonId);
 }

 public List<ServicePriceCompareResponse> compare(String serviceName, String region) {
  return salonServiceRepository.compareServices(normalizeFilter(serviceName), normalizeFilter(region))
          .stream().map(this::toComparison).toList();
 }

 public Page<ServicePriceCompareResponse> compare(String serviceName, String region, int page, int size) {
  return fetchPage(page, size, pageable -> salonServiceRepository.compareServices(
          normalizeFilter(serviceName), normalizeFilter(region), pageable)).map(this::toComparison);
 }

 private ServicePriceCompareResponse toComparison(ServicePriceCompareRow row) {
  return ServicePriceCompareResponse.builder()
                  .serviceId(row.getServiceId())
                  .salonId(row.getSalonId())
                  .salonName(row.getSalonName())
                  .address(row.getAddress())
                  .roadAddress(row.getRoadAddress())
                  .serviceName(row.getServiceName())
                  .price(row.getPrice())
                  .duration(row.getDuration())
                  .averageRating(row.getAverageRating() == null ? BigDecimal.ZERO : row.getAverageRating())
                  .build();
 }

 @Transactional
 public Integer create(SalonServiceCreateRequest request) {
  Salon salon = salonRepository.findById(request.getSalonId()).orElseThrow(() -> new EntityNotFoundException("Salon not found: " + request.getSalonId()));
  SalonService service = SalonService.builder().salon(salon).name(request.getName()).price(request.getPrice()).duration(request.getDuration()).description(request.getDescription()).build();
  return salonServiceRepository.save(service).getServiceId();
 }

 @Transactional
 public void update(Integer serviceId, SalonServiceUpdateRequest request) {
  catalogIntegrityService.assertServiceMoveAllowed(serviceId, request.getSalonId());
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
  catalogIntegrityService.assertServiceDeletable(serviceId);
  salonServiceRepository.deleteById(serviceId);
 }

 private SalonServiceSummaryResponse toSummary(SalonService service) {
  return SalonServiceSummaryResponse.builder().serviceId(service.getServiceId()).salonId(service.getSalon().getSalonId()).salonName(service.getSalon().getName()).address(service.getSalon().getAddress()).name(service.getName()).price(service.getPrice()).duration(service.getDuration()).averageRating(service.getSalon().getAverageRating() == null ? BigDecimal.ZERO : service.getSalon().getAverageRating()).description(service.getDescription()).build();
 }

 private SalonServiceDetailResponse toDetail(SalonService service) {
  return SalonServiceDetailResponse.builder().serviceId(service.getServiceId()).salonId(service.getSalon().getSalonId()).salonName(service.getSalon().getName()).name(service.getName()).price(service.getPrice()).duration(service.getDuration()).description(service.getDescription()).build();
 }

 private Comparator<SalonServiceSummaryResponse> serviceComparator(String sortBy, Map<String, Long> trendFrequency) {
  if ("duration".equalsIgnoreCase(sortBy))
   return Comparator.comparing(SalonServiceSummaryResponse::getDuration).thenComparing(SalonServiceSummaryResponse::getPrice).thenComparing(SalonServiceSummaryResponse::getName, String.CASE_INSENSITIVE_ORDER).thenComparing(SalonServiceSummaryResponse::getServiceId);
  if ("rating".equalsIgnoreCase(sortBy))
   return Comparator.comparing(SalonServiceSummaryResponse::getAverageRating, Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(SalonServiceSummaryResponse::getPrice).thenComparing(SalonServiceSummaryResponse::getName, String.CASE_INSENSITIVE_ORDER).thenComparing(SalonServiceSummaryResponse::getServiceId);
  if ("name".equalsIgnoreCase(sortBy))
   return Comparator.comparing(SalonServiceSummaryResponse::getName, String.CASE_INSENSITIVE_ORDER).thenComparing(SalonServiceSummaryResponse::getPrice).thenComparing(SalonServiceSummaryResponse::getServiceId);
  if ("trend".equalsIgnoreCase(sortBy))
   return Comparator.comparing((SalonServiceSummaryResponse service) -> trendFrequency.getOrDefault(normalizeServiceName(service.getName()), 0L), Comparator.reverseOrder()).thenComparing(SalonServiceSummaryResponse::getAverageRating, Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(SalonServiceSummaryResponse::getPrice).thenComparing(SalonServiceSummaryResponse::getName, String.CASE_INSENSITIVE_ORDER).thenComparing(SalonServiceSummaryResponse::getServiceId);
  return Comparator.comparing(SalonServiceSummaryResponse::getPrice).thenComparing(SalonServiceSummaryResponse::getDuration).thenComparing(SalonServiceSummaryResponse::getName, String.CASE_INSENSITIVE_ORDER).thenComparing(SalonServiceSummaryResponse::getServiceId);
 }

 private List<SalonServiceSummaryResponse> searchServices(SalonServiceSearchRequest request) {
  return salonServiceRepository.searchServices(normalizeFilter(request.getKeyword()), normalizeFilter(request.getSalonKeyword()), normalizeFilter(request.getRegion()), request.getMaxPrice(), request.getMaxDuration()).stream().map(this::toSummary).toList();
 }

 private Map<String, Long> buildTrendFrequency(List<SalonServiceSummaryResponse> services) {
  return services.stream().collect(Collectors.groupingBy(service -> normalizeServiceName(service.getName()), Collectors.counting()));
 }

 private String normalizeServiceName(String name) {
  if (name == null) return "";
  return name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
 }

 private String normalizeFilter(String value) {
  return value == null || value.isBlank() ? null : value.trim();
 }

 private String normalizeSort(String sortBy) {
  String value = sortBy == null ? "price" : sortBy.trim().toLowerCase(Locale.ROOT);
  return List.of("price", "duration", "rating", "name", "trend").contains(value) ? value : "price";
 }

 private <T> Page<T> fetchPage(int page, int size, Function<PageRequest, Page<T>> fetcher) {
  int safeSize = Math.max(size, 1);
  // JPA's first-result offset is an int, even though Pageable uses a long.
  int safePage = Math.min(Math.max(page, 0), Integer.MAX_VALUE / safeSize);
  Page<T> result = fetcher.apply(PageRequest.of(safePage, safeSize));
  if (safePage > 0 && result.isEmpty()) {
   int lastPage = Math.max(result.getTotalPages() - 1, 0);
   if (lastPage < safePage) return fetcher.apply(PageRequest.of(lastPage, safeSize));
  }
  return result;
 }
}
