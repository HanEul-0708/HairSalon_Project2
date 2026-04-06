package com.hairsalonproject2.member.controller;

import com.hairsalonproject2.common.exception.BusinessException;
import com.hairsalonproject2.member.dto.request.MemberLoginRequest;
import com.hairsalonproject2.member.dto.request.MemberPasswordChangeRequest;
import com.hairsalonproject2.member.dto.request.MemberSignupRequest;
import com.hairsalonproject2.member.dto.request.MemberUpdateRequest;
import com.hairsalonproject2.member.dto.response.MemberDetailResponse;
import com.hairsalonproject2.member.service.CustomUserDetails;
import com.hairsalonproject2.member.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;
import java.util.function.Consumer;

/**
 * 회원 컨트롤러
 *
 * URL 규칙:
 * /members/signup
 * /members/login
 * /members/me
 * /members/me/edit
 * /members/me/password
 */
@Controller
@RequestMapping("/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @GetMapping("/signup")
    public String signupForm(Model model) {
        model.addAttribute("memberSignupRequest", new MemberSignupRequest());
        return "member/signup";
    }

    @PostMapping("/signup")
    public String signup(@Valid @ModelAttribute MemberSignupRequest memberSignupRequest,
                         BindingResult bindingResult,
                         Model model) {
        if (bindingResult.hasErrors()) {
            return "member/signup";
        }

        try {
            memberService.signup(memberSignupRequest);
        } catch (BusinessException e) {
            model.addAttribute("signupErrorMessage", e.getErrorCode().getMessage());
            return "member/signup";
        }

        return "redirect:/members/login?signup=true";
    }

    @GetMapping("/login")
    public String loginForm(Model model,
                            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails != null) {
            return "redirect:/members/me";
        }

        if (!model.containsAttribute("memberLoginRequest")) {
            model.addAttribute("memberLoginRequest", new MemberLoginRequest());
        }

        return "member/login";
    }

    @GetMapping("/check-id")
    @ResponseBody
    public Map<String, Object> checkMemberId(@RequestParam String memberId) {
        return Map.of("available", memberService.isMemberIdAvailable(memberId));
    }

    @GetMapping("/check-email")
    @ResponseBody
    public Map<String, Object> checkEmail(@RequestParam String email) {
        return Map.of("available", memberService.isEmailAvailable(email));
    }

    @GetMapping("/check-phone")
    @ResponseBody
    public Map<String, Object> checkPhone(@RequestParam String phone) {
        return Map.of("available", memberService.isPhoneAvailable(phone));
    }

    @GetMapping("/me/check-email")
    @ResponseBody
    public Map<String, Object> checkMyEmail(@AuthenticationPrincipal CustomUserDetails userDetails,
                                            @RequestParam String email) {
        return Map.of("available", memberService.isEmailAvailableForUpdate(userDetails.getUsername(), email));
    }

    @GetMapping("/me/check-phone")
    @ResponseBody
    public Map<String, Object> checkMyPhone(@AuthenticationPrincipal CustomUserDetails userDetails,
                                            @RequestParam String phone) {
        return Map.of("available", memberService.isPhoneAvailableForUpdate(userDetails.getUsername(), phone));
    }

    @GetMapping("/me")
    public String myPage(@AuthenticationPrincipal CustomUserDetails userDetails,
                         Model model) {
        populateMyPageModel(model, userDetails.getUsername(), currentModel -> { });
        return "member/mypage";
    }

    @GetMapping("/me/edit")
    public String myEditPage(@AuthenticationPrincipal CustomUserDetails userDetails,
                             Model model) {
        populateMyPageModel(model, userDetails.getUsername(), currentModel -> { });
        return "member/mypage";
    }

    @PostMapping("/me/edit")
    public String updateMyProfile(@AuthenticationPrincipal CustomUserDetails userDetails,
                                  @Valid @ModelAttribute MemberUpdateRequest memberUpdateRequest,
                                  BindingResult bindingResult,
                                  Model model,
                                  RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            populateMyPageModel(model, userDetails.getUsername(),
                    currentModel -> currentModel.addAttribute("memberUpdateRequest", memberUpdateRequest));
            return "member/mypage";
        }

        try {
            memberService.updateMyProfile(userDetails.getUsername(), memberUpdateRequest);
        } catch (BusinessException e) {
            populateMyPageModel(model, userDetails.getUsername(), currentModel -> {
                currentModel.addAttribute("memberUpdateRequest", memberUpdateRequest);
                currentModel.addAttribute("errorMessage", e.getErrorCode().getMessage());
            });
            return "member/mypage";
        }

        redirectAttributes.addFlashAttribute("successMessage", "회원 정보가 수정되었습니다.");
        return "redirect:/members/me";
    }

    @PostMapping("/me/password")
    public String changePassword(@AuthenticationPrincipal CustomUserDetails userDetails,
                                 @Valid @ModelAttribute MemberPasswordChangeRequest memberPasswordChangeRequest,
                                 BindingResult bindingResult,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            populateMyPageModel(model, userDetails.getUsername(),
                    currentModel -> currentModel.addAttribute("memberPasswordChangeRequest", memberPasswordChangeRequest));
            return "member/mypage";
        }

        try {
            memberService.changePassword(userDetails.getUsername(), memberPasswordChangeRequest);
        } catch (BusinessException e) {
            populateMyPageModel(model, userDetails.getUsername(), currentModel -> {
                currentModel.addAttribute("memberPasswordChangeRequest", memberPasswordChangeRequest);
                currentModel.addAttribute("errorMessage", e.getErrorCode().getMessage());
            });
            return "member/mypage";
        }

        redirectAttributes.addFlashAttribute("successMessage", "비밀번호가 변경되었습니다.");
        return "redirect:/members/me";
    }

    @PostMapping("/me/delete")
    public String deleteMember(@AuthenticationPrincipal CustomUserDetails userDetails) {
        String memberId = userDetails.getMember().getMemberId();
        memberService.delete(memberId);
        return "redirect:/members/logout";
    }

    private void populateMyPageModel(Model model,
                                     String memberId,
                                     Consumer<Model> customizer) {
        MemberDetailResponse member = memberService.getMyDetail(memberId);
        model.addAttribute("member", member);
        model.addAttribute("memberUpdateRequest", createMemberUpdateRequest(member));
        model.addAttribute("memberPasswordChangeRequest", new MemberPasswordChangeRequest());
        customizer.accept(model);
    }

    private MemberUpdateRequest createMemberUpdateRequest(MemberDetailResponse member) {
        MemberUpdateRequest request = new MemberUpdateRequest();
        request.setName(member.getName());
        request.setPhone(member.getPhone());
        request.setEmail(member.getEmail());
        return request;
    }
}
