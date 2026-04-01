package com.hairsalonproject2.service;

import com.hairsalonproject2.dto.request.SalonServiceCreateRequest;
import com.hairsalonproject2.dto.request.SalonServiceUpdateRequest;

import com.hairsalonproject2.dto.response.SalonServiceDetailResponse;
import com.hairsalonproject2.dto.response.SalonServiceSummaryResponse;
import com.hairsalonproject2.dto.response.ServicePriceCompareResponse;

import com.hairsalonproject2.entity.Salon;
import com.hairsalonproject2.entity.SalonService;

import com.hairsalonproject2.repository.SalonRepository;
import com.hairsalonproject2.repository.SalonServiceRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalonServiceQueryService {
    private final SalonServiceRepository salonServiceRepository;
    private final SalonRepository salonRepository;

    public List<SalonServiceSummaryResponse> list(String keyword) {
        return salonServiceRepository.searchByName(keyword).stream()
                .map(this::toSummary)
                .toList();
    }

    public SalonServiceDetailResponse getDetail(Integer serviceId) {
        SalonService service = salonServiceRepository.findById(serviceId)
                .orElseThrow(() -> new EntityNotFoundException("SalonService not found: " + serviceId));
        return toDetail(service);
    }

    public List<ServicePriceCompareResponse> compare(String serviceName, String region) {
        return salonServiceRepository.compareServices(serviceName, region).stream()
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
                .name(service.getName())
                .price(service.getPrice())
                .duration(service.getDuration())
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
}