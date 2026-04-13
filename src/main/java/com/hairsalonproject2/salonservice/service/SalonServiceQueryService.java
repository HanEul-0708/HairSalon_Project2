package com.hairsalonproject2.salonservice.service;

import com.hairsalonproject2.common.support.PageUtils;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

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
        List<SalonServiceSummaryResponse> filtered = salonServiceRepository.searchServices(
                        safeRequest.getKeyword(),
                        safeRequest.getSalonKeyword(),
                        safeRequest.getRegion(),
                        safeRequest.getCity(),
                        safeRequest.getDistrict(),
                        safeRequest.getNeighborhood(),
                        safeRequest.getMaxPrice(),
                        safeRequest.getMaxDuration()
                ).stream()
                .map(this::toSummary)
                .sorted(serviceComparator(safeRequest.getSortBy()))
                .toList();

        return PageUtils.sliceZeroBased(filtered, page, size);
    }

    public SalonServiceDetailResponse getDetail(Integer serviceId) {
        SalonService service = salonServiceRepository.findById(serviceId)
                .orElseThrow(() -> new EntityNotFoundException("SalonService not found: " + serviceId));
        return toDetail(service);
    }

    public List<ServicePriceCompareResponse> compare(String serviceName,
                                                     String city,
                                                     String district,
                                                     String neighborhood) {
        return salonServiceRepository.compareServices(serviceName, city, district, neighborhood).stream()
                .map(row -> ServicePriceCompareResponse.builder()
                        .serviceId(row.getServiceId())
                        .salonId(row.getSalonId())
                        .salonName(row.getSalonName())
                        .address(row.getAddress())
                        .serviceName(row.getServiceName())
                        .price(row.getPrice())
                        .duration(row.getDuration())
                        .averageRating(row.getAverageRating())
                        .build())
                .toList();
    }

    public List<String> getServiceNameOptions() {
        return salonServiceRepository.findAll().stream()
                .map(SalonService::getName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    @Transactional
    public Integer create(SalonServiceCreateRequest request) {
        Salon salon = salonRepository.findById(request.getSalonId())
                .orElseThrow(() -> new EntityNotFoundException("Salon not found: " + request.getSalonId()));

        SalonService service = SalonService.builder()
                .salon(salon)
                .name(request.getName())
                .price(request.getPrice())
                .duration(request.getDuration())
                .description(request.getDescription())
                .build();
        return salonServiceRepository.save(service).getServiceId();
    }

    @Transactional
    public void update(Integer serviceId, SalonServiceUpdateRequest request) {
        SalonService service = salonServiceRepository.findById(serviceId)
                .orElseThrow(() -> new EntityNotFoundException("SalonService not found: " + serviceId));
        Salon salon = salonRepository.findById(request.getSalonId())
                .orElseThrow(() -> new EntityNotFoundException("Salon not found: " + request.getSalonId()));

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
        return SalonServiceSummaryResponse.builder()
                .serviceId(service.getServiceId())
                .salonId(service.getSalon().getSalonId())
                .salonName(service.getSalon().getName())
                .address(service.getSalon().getAddress())
                .name(service.getName())
                .price(service.getPrice())
                .duration(service.getDuration())
                .averageRating(service.getSalon().getAverageRating() == null ? 0 : service.getSalon().getAverageRating().intValue())
                .description(service.getDescription())
                .build();
    }

    private SalonServiceDetailResponse toDetail(SalonService service) {
        return SalonServiceDetailResponse.builder()
                .serviceId(service.getServiceId())
                .salonId(service.getSalon().getSalonId())
                .salonName(service.getSalon().getName())
                .name(service.getName())
                .price(service.getPrice())
                .duration(service.getDuration())
                .description(service.getDescription())
                .build();
    }

    private Comparator<SalonServiceSummaryResponse> serviceComparator(String sortBy) {
        if ("duration".equalsIgnoreCase(sortBy)) {
            return Comparator.comparing(
                            SalonServiceSummaryResponse::getDuration,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(
                            SalonServiceSummaryResponse::getPrice,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(
                            SalonServiceSummaryResponse::getName,
                            Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        }

        if ("price".equalsIgnoreCase(sortBy)) {
            return Comparator.comparing(
                            SalonServiceSummaryResponse::getPrice,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(
                            SalonServiceSummaryResponse::getDuration,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(
                            SalonServiceSummaryResponse::getName,
                            Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        }

        return Comparator.comparing(
                        SalonServiceSummaryResponse::getAverageRating,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(
                        SalonServiceSummaryResponse::getPrice,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(
                        SalonServiceSummaryResponse::getName,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
    }
}
