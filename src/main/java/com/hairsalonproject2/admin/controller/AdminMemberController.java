package com.hairsalonproject2.admin.controller;

import com.hairsalonproject2.common.constant.MemberRole;
import com.hairsalonproject2.common.constant.MemberStatus;
import com.hairsalonproject2.common.exception.BusinessException;
import com.hairsalonproject2.member.dto.response.MemberDetailResponse;
import com.hairsalonproject2.member.dto.response.MemberSummaryResponse;
import com.hairsalonproject2.member.repository.MemberRepository;
import com.hairsalonproject2.member.service.CustomUserDetails;
import com.hairsalonproject2.member.service.DummyMemberSeeder;
import com.hairsalonproject2.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/members")
@RequiredArgsConstructor
public class AdminMemberController {

    private final MemberService memberService;
    private final DummyMemberSeeder dummyMemberSeeder;
    private final MemberRepository memberRepository;

    @Value("${app.seed.members.target-designer-count:20}")
    private int targetDesignerCount;

    @Value("${app.seed.members.target-user-count:40}")
    private int targetUserCount;

    @Value("${app.seed.members.default-password:12341234}")
    private String seedPassword;

    @GetMapping
    public String memberList(@RequestParam(required = false) String keyword,
                             @RequestParam(required = false) MemberRole role,
                             @RequestParam(required = false) MemberStatus status,
                             @RequestParam(defaultValue = "latest") String sort,
                             @RequestParam(defaultValue = "0") int page,
                             Model model) {

        Page<MemberSummaryResponse> memberPage =
                memberService.searchMembers(keyword, role, status, sort, page);

        model.addAttribute("memberPage", memberPage);
        model.addAttribute("members", memberPage.getContent());
        model.addAttribute("keyword", keyword);
        model.addAttribute("selectedRole", role);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedSort", sort);
        model.addAttribute("roles", MemberRole.values());
        model.addAttribute("statuses", MemberStatus.values());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", memberPage.getTotalPages());
        model.addAttribute("currentMenu", "members");
        model.addAttribute("memberSeedingEnabled", true);
        model.addAttribute("adminMemberCount", memberRepository.countByRole(MemberRole.ADMIN));
        model.addAttribute("designerMemberCount", memberRepository.countByRole(MemberRole.DESIGNER));
        model.addAttribute("userMemberCount", memberRepository.countByRole(MemberRole.USER));
        model.addAttribute("seedGuide", "디자이너 " + targetDesignerCount
                + "명, 회원 " + targetUserCount
                + "명까지 생성합니다. 공통 비밀번호는 " + seedPassword + " 입니다.");

        return "admin/member-list";
    }

    @GetMapping("/{memberId}")
    public String memberDetail(@PathVariable String memberId, Model model) {
        MemberDetailResponse member = memberService.getMemberDetailByAdmin(memberId);
        model.addAttribute("member", member);
        model.addAttribute("roles", MemberRole.values());
        model.addAttribute("statuses", MemberStatus.values());
        model.addAttribute("currentMenu", "members");
        return "admin/member-detail";
    }

    @PostMapping("/seed")
    public String seedDummyMembers(RedirectAttributes redirectAttributes) {
        int created = dummyMemberSeeder.seedMembers();
        if (created > 0) {
            redirectAttributes.addFlashAttribute("successMessage", created + "개의 더미 회원을 생성했습니다.");
        } else {
            redirectAttributes.addFlashAttribute("successMessage", "추가로 생성할 더미 회원이 없습니다.");
        }
        return "redirect:/admin/members";
    }

    @PostMapping("/{memberId}/delete")
    public String deleteMember(@PathVariable String memberId,
                               @AuthenticationPrincipal CustomUserDetails userDetails,
                               RedirectAttributes redirectAttributes) {
        try {
            memberService.softDeleteByAdmin(userDetails.getUsername(), memberId);
            redirectAttributes.addFlashAttribute("successMessage", "회원을 삭제했습니다.");
            return "redirect:/admin/members";
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getErrorCode().getMessage());
            return "redirect:/admin/members/" + memberId;
        }
    }

    @PostMapping("/{memberId}/role")
    public String changeRole(@PathVariable String memberId,
                             @RequestParam MemberRole role,
                             @AuthenticationPrincipal CustomUserDetails userDetails,
                             RedirectAttributes redirectAttributes) {
        try {
            memberService.changeMemberRoleByAdmin(userDetails.getUsername(), memberId, role);
            redirectAttributes.addFlashAttribute("successMessage", "권한이 변경되었습니다.");
            return "redirect:/admin/members/" + memberId;
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getErrorCode().getMessage());
            return "redirect:/admin/members/" + memberId;
        }
    }

    @PostMapping("/{memberId}/status")
    public String changeStatus(@PathVariable String memberId,
                               @RequestParam MemberStatus status,
                               @AuthenticationPrincipal CustomUserDetails userDetails,
                               RedirectAttributes redirectAttributes) {
        try {
            memberService.changeStatusByAdmin(userDetails.getUsername(), memberId, status);
            redirectAttributes.addFlashAttribute("successMessage", "회원 상태가 변경되었습니다.");
            return "redirect:/admin/members/" + memberId;
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getErrorCode().getMessage());
            return "redirect:/admin/members/" + memberId;
        }
    }
}
