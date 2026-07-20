package com.hairsalonproject2.designer.entity;

import com.hairsalonproject2.common.entity.BaseCreatedEntity;
import com.hairsalonproject2.member.entity.Member;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "designer_like", uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "designer_id"}))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class DesignerLike extends BaseCreatedEntity {

 @Id
 @GeneratedValue(strategy = GenerationType.IDENTITY)
 private Integer id;

 @ManyToOne(fetch = FetchType.LAZY)
 @JoinColumn(name = "member_id", nullable = false)
 private Member member;

 @ManyToOne(fetch = FetchType.LAZY)
 @JoinColumn(name = "designer_id", nullable = false)
 private Designer designer;
}
