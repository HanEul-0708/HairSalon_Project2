package com.hairsalonproject2.member.controller;

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
import org.springframework.web.bind.annotation.*;

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
                         BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            return "member/signup";
        }

        memberService.signup(memberSignupRequest);
        return "redirect:/members/login?signup=true";
    }

    @GetMapping("/login")
    public String loginForm(Model model) {
        model.addAttribute("memberLoginRequest", new MemberLoginRequest());
        return "member/login";
    }

    @GetMapping("/me")
    public String myPage(@AuthenticationPrincipal CustomUserDetails userDetails,
                         Model model) {

        MemberDetailResponse member = memberService.getMyDetail(userDetails.getUsername());

        MemberUpdateRequest memberUpdateRequest = new MemberUpdateRequest();
        memberUpdateRequest.setName(member.getName());
        memberUpdateRequest.setPhone(member.getPhone());
        memberUpdateRequest.setEmail(member.getEmail());

        model.addAttribute("member", member);
        model.addAttribute("memberUpdateRequest", memberUpdateRequest);
        model.addAttribute("memberPasswordChangeRequest", new MemberPasswordChangeRequest());

        return "member/mypage";
    }

    @GetMapping("/me/edit")
    public String myEditPage(@AuthenticationPrincipal CustomUserDetails userDetails,
                             Model model) {

        MemberDetailResponse member = memberService.getMyDetail(userDetails.getUsername());

        MemberUpdateRequest memberUpdateRequest = new MemberUpdateRequest();
        memberUpdateRequest.setName(member.getName());
        memberUpdateRequest.setPhone(member.getPhone());
        memberUpdateRequest.setEmail(member.getEmail());

        model.addAttribute("member", member);
        model.addAttribute("memberUpdateRequest", memberUpdateRequest);
        model.addAttribute("memberPasswordChangeRequest", new MemberPasswordChangeRequest());

        return "member/mypage";
    }

    @PostMapping("/me/edit")
    public String updateMyProfile(@AuthenticationPrincipal CustomUserDetails userDetails,
                                  @Valid @ModelAttribute MemberUpdateRequest memberUpdateRequest,
                                  BindingResult bindingResult,
                                  Model model) {

        if (bindingResult.hasErrors()) {
            MemberDetailResponse member = memberService.getMyDetail(userDetails.getUsername());
            model.addAttribute("member", member);
            model.addAttribute("memberPasswordChangeRequest", new MemberPasswordChangeRequest());
            return "member/mypage";
        }

        memberService.updateMyProfile(userDetails.getUsername(), memberUpdateRequest);
        return "redirect:/members/me?updated=true";
    }

    @PostMapping("/me/password")
    public String changePassword(@AuthenticationPrincipal CustomUserDetails userDetails,
                                 @Valid @ModelAttribute MemberPasswordChangeRequest memberPasswordChangeRequest,
                                 BindingResult bindingResult,
                                 Model model) {

        if (bindingResult.hasErrors()) {
            MemberDetailResponse member = memberService.getMyDetail(userDetails.getUsername());
            model.addAttribute("member", member);
            model.addAttribute("memberUpdateRequest", new MemberUpdateRequest());
            return "member/mypage";
        }

        memberService.changePassword(userDetails.getUsername(), memberPasswordChangeRequest);
        return "redirect:/members/me?passwordChanged=true";
    }
}