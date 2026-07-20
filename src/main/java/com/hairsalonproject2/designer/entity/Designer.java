package com.hairsalonproject2.designer.entity;

import com.hairsalonproject2.common.entity.BaseCreatedEntity;
import com.hairsalonproject2.designer.constant.DesignerSpecialty;
import com.hairsalonproject2.member.entity.Member;
import com.hairsalonproject2.salon.entity.Salon;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "designer")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Designer extends BaseCreatedEntity {

 @Id
 @GeneratedValue(strategy = GenerationType.IDENTITY)
 @Column(name = "designer_id")
 private Integer designerId;

 @ManyToOne(fetch = FetchType.LAZY)
 @JoinColumn(name = "salon_id", nullable = false)
 private Salon salon;

 @OneToOne(fetch = FetchType.LAZY)
 @JoinColumn(name = "member_id", unique = true)
 private Member member;

 @Column(nullable = false, length = 50)
 private String name;

 @Column(name = "profile_image", length = 255)
 private String profileImage;

 @Column(columnDefinition = "TEXT")
 private String introduction;

 @Column(name = "career_years", nullable = false)
 @Builder.Default
 private Integer careerYears = 0;

 @Enumerated(EnumType.STRING)
 @Column(name = "specialty", nullable = false, length = 20)
 @Builder.Default
 private DesignerSpecialty specialty = DesignerSpecialty.CUT;

 @Column(name = "like_count", nullable = false)
 @Builder.Default
 private Integer likeCount = 0;
}
