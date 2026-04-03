package com.hairsalonproject2.salon.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "salon_like", uniqueConstraints = {@UniqueConstraint(name = "uq_salon_like", columnNames = {"member_id", "salon_id"})})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalonLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "like_id")
    private Integer likeId;

    @Column(name = "member_id", nullable = false, length = 30)
    private String memberId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "salon_id", nullable = false)
    private Salon salon;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}