package com.hairsalonproject2.member.dto.response;

import com.hairsalonproject2.common.constant.MemberRole;
import com.hairsalonproject2.common.constant.MemberStatus;
import com.hairsalonproject2.member.entity.Member;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 회원 상세 응답 DTO
 */
@Getter
@Builder
public class MemberDetailResponse {

    private String memberId;
    private String name;
    private String phone;
    private String email;
    private MemberRole role;
    private LocalDateTime createdAt;
    private MemberStatus status;

    public static MemberDetailResponse from(Member member) {
        return MemberDetailResponse.builder()
                .memberId(member.getMemberId())
                .name(member.getName())
                .phone(member.getPhone())
                .email(member.getEmail())
                .role(member.getRole())
                .createdAt(member.getCreatedAt())
                .status(member.getStatus())
                .build();
    }
}