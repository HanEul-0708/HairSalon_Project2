package com.hairsalonproject2.admin.controller;

import com.hairsalonproject2.common.constant.MemberRole;
import com.hairsalonproject2.member.dto.response.MemberDetailResponse;
import com.hairsalonproject2.member.dto.response.MemberSummaryResponse;
import com.hairsalonproject2.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/**
 * 관리자 회원 관리 컨트롤러
 */
@Controller
@RequestMapping("/admin/members")
@RequiredArgsConstructor
public class AdminMemberController {

    private final MemberService memberService;

    /**
     * 회원 목록 조회
     */
    @GetMapping
    public String memberList(Model model) {
        java.util.List<MemberSummaryResponse> members = memberService.getAllMembers();
        model.addAttribute("members", members);
        return "admin/member-list";
    }

    /**
     * 회원 상세 조회
     */
    @GetMapping("/{memberId}")
    public String memberDetail(@PathVariable String memberId, Model model) {
        MemberDetailResponse member = memberService.getMemberDetailByAdmin(memberId);
        model.addAttribute("member", member);
        model.addAttribute("roles", MemberRole.values());
        return "admin/member-detail";
    }

    /**
     * 회원 삭제
     */
    @PostMapping("/{memberId}/delete")
    public String deleteMember(@PathVariable String memberId) {
        memberService.deleteMemberByAdmin(memberId);
        return "redirect:/admin/members?deleted=true";
    }

    /**
     * 회원 권한 변경
     */
    @PostMapping("/{memberId}/role")
    public String changeRole(@PathVariable String memberId,
                             @RequestParam MemberRole role) {
        memberService.changeMemberRoleByAdmin(memberId, role);
        return "redirect:/admin/members/{memberId}?roleChanged=true";
    }
}