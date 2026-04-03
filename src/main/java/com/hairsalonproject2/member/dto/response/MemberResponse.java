package com.hairsalonproject2.member.dto.response;

import com.hairsalonproject2.common.constant.MemberRole;
import com.hairsalonproject2.member.entity.Member;
import lombok.Builder;
import lombok.Getter;

/**
 * 회원 기본 응답 DTO
 */
@Getter
@Builder
public class MemberResponse {

    private String memberId;
    private String name;
    private String phone;
    private String email;
    private MemberRole role;

    public static MemberResponse from(Member member) {
        return MemberResponse.builder()
                .memberId(member.getMemberId())
                .name(member.getName())
                .phone(member.getPhone())
                .email(member.getEmail())
                .role(member.getRole())
                .build();
    }
}