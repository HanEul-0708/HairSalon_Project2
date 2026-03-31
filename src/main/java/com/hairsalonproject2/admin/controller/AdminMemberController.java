package com.hairsalonproject2.admin.controller;

import com.hairsalonproject2.common.constant.MemberRole;
import com.hairsalonproject2.exception.BusinessException;
import com.hairsalonproject2.member.dto.response.MemberDetailResponse;
import com.hairsalonproject2.member.dto.response.MemberSummaryResponse;
import com.hairsalonproject2.member.service.CustomUserDetails;
import com.hairsalonproject2.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.data.domain.Page;

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
    public String memberList(@RequestParam(required = false) String keyword,
                             @RequestParam(required = false) MemberRole role,
                             @RequestParam(defaultValue = "0") int page,
                             Model model) {

        Page<MemberSummaryResponse> memberPage =
                memberService.searchMembers(keyword, role, page);

        model.addAttribute("memberPage", memberPage);
        model.addAttribute("members", memberPage.getContent());

        model.addAttribute("keyword", keyword);
        model.addAttribute("selectedRole", role);
        model.addAttribute("roles", MemberRole.values());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", memberPage.getTotalPages());

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
     *
     * 주의
     * - 관리자가 자기 자신의 계정을 삭제하지 못하도록 서비스에서 방지
     */
    @PostMapping("/{memberId}/delete")
    public String deleteMember(@PathVariable String memberId,
                               @AuthenticationPrincipal CustomUserDetails userDetails,
                               RedirectAttributes redirectAttributes) {

        try {
            // 로그인한 관리자 아이디와 삭제 대상 아이디를 함께 전달
            memberService.deleteMemberByAdmin(userDetails.getUsername(), memberId);
            return "redirect:/admin/members?deleted=true";

        } catch (BusinessException e) {
            // 실패 시 상세 화면으로 되돌리고 에러 메시지 표시
            redirectAttributes.addFlashAttribute("deleteErrorMessage", e.getErrorCode().getMessage());
            return "redirect:/admin/members/" + memberId;
        }
    }

    /**
     * 회원 권한 변경
     *
     * 주의
     * - 관리자가 자기 자신의 권한을 변경하지 못하도록 서비스에서 방지
     */
    @PostMapping("/{memberId}/role")
    public String changeRole(@PathVariable String memberId,
                             @RequestParam MemberRole role,
                             @AuthenticationPrincipal CustomUserDetails userDetails,
                             RedirectAttributes redirectAttributes) {

        try {
            // 로그인한 관리자 아이디와 대상 회원 아이디를 함께 전달
            memberService.changeMemberRoleByAdmin(userDetails.getUsername(), memberId, role);
            return "redirect:/admin/members/" + memberId + "?roleChanged=true";

        } catch (BusinessException e) {
            // 실패 시 상세 화면으로 되돌리고 에러 메시지 표시
            redirectAttributes.addFlashAttribute("roleErrorMessage", e.getErrorCode().getMessage());
            return "redirect:/admin/members/" + memberId;
        }
    }
}