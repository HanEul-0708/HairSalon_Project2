package com.hairsalonproject2.admin.controller;

import com.hairsalonproject2.common.constant.MemberRole;
import com.hairsalonproject2.common.exception.BusinessException;
import com.hairsalonproject2.member.dto.response.MemberDetailResponse;
import com.hairsalonproject2.member.dto.response.MemberSummaryResponse;
import com.hairsalonproject2.member.service.CustomUserDetails;
import com.hairsalonproject2.member.service.MemberService;
import com.hairsalonproject2.common.constant.MemberStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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
        model.addAttribute("statuses", MemberStatus.values());
        return "admin/member-detail";
    }

    /**
     * 회원 삭제
     */
    @PostMapping("/{memberId}/delete")
    public String deleteMember(@PathVariable String memberId,
                               @AuthenticationPrincipal CustomUserDetails userDetails,
                               RedirectAttributes redirectAttributes) {

        try {
            memberService.deleteMemberByAdmin(userDetails.getUsername(), memberId);

            // 삭제 성공 메시지는 목록 화면에서 보여준다.
            redirectAttributes.addFlashAttribute("successMessage", "회원이 삭제되었습니다.");
            return "redirect:/admin/members";

        } catch (BusinessException e) {
            // 삭제 실패 메시지는 상세 화면에서 보여준다.
            redirectAttributes.addFlashAttribute("errorMessage", e.getErrorCode().getMessage());
            return "redirect:/admin/members/" + memberId;
        }
    }

    /**
     * 회원 권한 변경
     */
    @PostMapping("/{memberId}/role")
    public String changeRole(@PathVariable String memberId,
                             @RequestParam MemberRole role,
                             @AuthenticationPrincipal CustomUserDetails userDetails,
                             RedirectAttributes redirectAttributes) {

        try {
            memberService.changeMemberRoleByAdmin(userDetails.getUsername(), memberId, role);

            // 권한 변경 성공 메시지는 상세 화면에서 보여준다.
            redirectAttributes.addFlashAttribute("successMessage", "권한이 변경되었습니다.");
            return "redirect:/admin/members/" + memberId;

        } catch (BusinessException e) {
            // 권한 변경 실패 메시지도 상세 화면에서 보여준다.
            redirectAttributes.addFlashAttribute("errorMessage", e.getErrorCode().getMessage());
            return "redirect:/admin/members/" + memberId;
        }
    }

    /**
     * 회원 상태 변경
     */
    @PostMapping("/{memberId}/status")
    public String changeStatus(@PathVariable String memberId,
                               @RequestParam MemberStatus status,
                               @AuthenticationPrincipal CustomUserDetails userDetails,
                               RedirectAttributes redirectAttributes) {

        try {
            memberService.changeMemberStatusByAdmin(userDetails.getUsername(), memberId, status);
            redirectAttributes.addFlashAttribute("successMessage", "회원 상태가 변경되었습니다.");
            return "redirect:/admin/members/" + memberId;

        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getErrorCode().getMessage());
            return "redirect:/admin/members/" + memberId;
        }
    }
}