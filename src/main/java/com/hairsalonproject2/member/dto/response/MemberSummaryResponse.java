package com.hairsalonproject2.member.dto.response;

import com.hairsalonproject2.common.constant.MemberRole;
import com.hairsalonproject2.member.entity.Member;
import lombok.Builder;
import lombok.Getter;

/**
 * 회원 목록용 응답 DTO
 */
@Getter
@Builder
public class MemberSummaryResponse {

    private String memberId;
    private String name;
    private String email;
    private MemberRole role;

    public static MemberSummaryResponse from(Member member) {
        return MemberSummaryResponse.builder()
                .memberId(member.getMemberId())
                .name(member.getName())
                .email(member.getEmail())
                .role(member.getRole())
                .build();
    }
}