package com.hairsalonproject2.salon.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "salon")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Salon {

 @Id
 @GeneratedValue(strategy = GenerationType.IDENTITY)
 @Column(name = "salon_id")
 private Integer salonId;

 @Column(name = "external_id", length = 100, unique = true)
 private String externalId;

 @Column(name = "source_type", length = 30)
 private String sourceType;

 @Column(nullable = false, length = 100)
 private String name;

 @Column(nullable = false, length = 255)
 private String address;

 @Column(name = "road_address", length = 255)
 private String roadAddress;

 @Column(length = 20)
 private String phone;

 @Column(columnDefinition = "TEXT")
 private String description;

 @Column(name = "image_url", length = 255)
 private String imageUrl;

 @Column(name = "place_url", length = 255)
 private String placeUrl;

 @Column(nullable = false)
 @Builder.Default
 private Boolean reservable = true;

 @Column(name = "average_rating", precision = 3, scale = 2, nullable = false)
 @Builder.Default
 private BigDecimal averageRating = BigDecimal.ZERO;

 @Column(name = "review_count", nullable = false)
 @Builder.Default
 private Integer reviewCount = 0;

 @Column(name = "like_count", nullable = false)
 @Builder.Default
 private Integer likeCount = 0;

 @Column(name = "created_at", insertable = false, updatable = false)
 private LocalDateTime createdAt;

 @Column(name = "updated_at", insertable = false, updatable = false)
 private LocalDateTime updatedAt;
}
