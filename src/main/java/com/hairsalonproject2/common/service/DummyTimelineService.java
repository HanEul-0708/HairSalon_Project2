package com.hairsalonproject2.common.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DummyTimelineService {

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void alignSalonSeedTimeline(Integer salonId,
                                       List<Integer> designerIds,
                                       List<Integer> serviceIds) {
        if (salonId == null) {
            return;
        }

        LocalDateTime salonCreatedAt = LocalDate.now()
                .minusDays(45L + (salonId % 20))
                .atTime(9, 0)
                .plusMinutes((salonId % 6) * 10L);

        updateTimeTable("salon", "salon_id", salonId, salonCreatedAt);

        for (int i = 0; i < designerIds.size(); i++) {
            Integer designerId = designerIds.get(i);
            if (designerId == null) {
                continue;
            }
            LocalDateTime designerCreatedAt = salonCreatedAt.plusHours(2L + i);
            updateCreatedTable("designer", "designer_id", designerId, designerCreatedAt);
        }

        for (int i = 0; i < serviceIds.size(); i++) {
            Integer serviceId = serviceIds.get(i);
            if (serviceId == null) {
                continue;
            }
            LocalDateTime serviceCreatedAt = salonCreatedAt.plusHours(6L + i);
            updateCreatedTable("salon_service", "service_id", serviceId, serviceCreatedAt);
        }
    }

    @Transactional
    public void alignReservationTimeline(Integer reservationId,
                                         Integer slotId,
                                         LocalDate reservationDate,
                                         LocalTime reservationTime,
                                         LocalDateTime minimumCreatedAt) {
        if (reservationId == null || reservationDate == null || reservationTime == null || minimumCreatedAt == null) {
            return;
        }

        LocalDateTime desiredReservationCreatedAt = reservationDate.atTime(reservationTime).minusDays(2).minusHours(3);
        LocalDateTime reservationCreatedAt = desiredReservationCreatedAt.isAfter(minimumCreatedAt)
                ? desiredReservationCreatedAt
                : minimumCreatedAt.plusHours(2);

        updateTimeTable("reservation", "reservation_id", reservationId, reservationCreatedAt);

        if (slotId != null) {
            updateCreatedTable("reservation_slot", "slot_id", slotId, reservationCreatedAt.plusMinutes(5));
        }
    }

    @Transactional
    public void alignReviewTimeline(Integer reviewId,
                                    Integer reservationId,
                                    LocalDate reservationDate,
                                    LocalTime reservationTime) {
        if (reviewId == null || reservationId == null || reservationDate == null || reservationTime == null) {
            return;
        }

        LocalDateTime reservationCreatedAt = jdbcTemplate.queryForObject(
                "select created_at from reservation where reservation_id = ?",
                (rs, rowNum) -> rs.getTimestamp(1).toLocalDateTime(),
                reservationId
        );
        if (reservationCreatedAt == null) {
            return;
        }

        LocalDateTime desiredReviewCreatedAt = reservationDate.atTime(reservationTime).plusHours(2);
        LocalDateTime reviewCreatedAt = desiredReviewCreatedAt.isAfter(reservationCreatedAt)
                ? desiredReviewCreatedAt
                : reservationCreatedAt.plusHours(1);

        updateCreatedTable("review", "review_id", reviewId, reviewCreatedAt);
    }

    private void updateTimeTable(String tableName, String idColumn, Integer id, LocalDateTime createdAt) {
        jdbcTemplate.update(
                "update " + tableName + " set created_at = ?, updated_at = ? where " + idColumn + " = ?",
                Timestamp.valueOf(createdAt),
                Timestamp.valueOf(createdAt),
                id
        );
    }

    private void updateCreatedTable(String tableName, String idColumn, Integer id, LocalDateTime createdAt) {
        jdbcTemplate.update(
                "update " + tableName + " set created_at = ? where " + idColumn + " = ?",
                Timestamp.valueOf(createdAt),
                id
        );
    }
}
