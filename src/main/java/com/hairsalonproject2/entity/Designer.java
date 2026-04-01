package com.hairsalonproject2.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "designer")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Designer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "designer_id")
    private Integer designerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "salon_id", nullable = false)
    private Salon salon;

    @Column(name = "member_id", length = 30)
    private String memberId;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "profile_image", length = 255)
    private String profileImage;

    @Column(columnDefinition = "TEXT")
    private String introduction;

    @Column(name = "career_years", nullable = false)
    @Builder.Default
    private Integer careerYears = 0;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}