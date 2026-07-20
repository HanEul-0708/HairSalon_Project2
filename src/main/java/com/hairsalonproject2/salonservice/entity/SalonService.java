package com.hairsalonproject2.salonservice.entity;

import com.hairsalonproject2.salon.entity.Salon;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "salon_service")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalonService {

 @Id
 @GeneratedValue(strategy = GenerationType.IDENTITY)
 @Column(name = "service_id")
 private Integer serviceId;

 @ManyToOne(fetch = FetchType.LAZY)
 @JoinColumn(name = "salon_id", nullable = false)
 private Salon salon;

 @Column(nullable = false, length = 100)
 private String name;

 @Column(nullable = false)
 private Integer price;

 @Column(nullable = false)
 private Integer duration;

 @Column(columnDefinition = "TEXT")
 private String description;

 @Column(name = "created_at", insertable = false, updatable = false)
 private LocalDateTime createdAt;
}