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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
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
                         BindingResult bindingResult,
                         Model model) {

        // Bean Validation 실패 시 회원가입 화면으로 복귀
        if (bindingResult.hasErrors()) {
            return "member/signup";
        }

        try {
            memberService.signup(memberSignupRequest);
        } catch (BusinessException e) {
            // 중복 아이디 / 중복 이메일 / 비밀번호 확인 불일치 등을
            // 에러 페이지가 아니라 회원가입 화면에서 바로 보여주기 위한 처리
            model.addAttribute("signupErrorMessage", e.getErrorCode().getMessage());
            return "member/signup";
        }

        return "redirect:/members/login?signup=true";
    }

    @GetMapping("/login")
    public String loginForm(Model model,
                            @AuthenticationPrincipal CustomUserDetails userDetails) {

        // 이미 로그인된 상태면 로그인 페이지 못 들어오게 막기
        if (userDetails != null) {
            return "redirect:/members/me"; // 또는 "/" 가능
        }

        if (!model.containsAttribute("memberLoginRequest")) {
            model.addAttribute("memberLoginRequest", new MemberLoginRequest());
        }

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
                                  Model model,
                                  RedirectAttributes redirectAttributes) {

        // 1. Bean Validation 오류가 있으면 다시 마이페이지로
        if (bindingResult.hasErrors()) {
            MemberDetailResponse member = memberService.getMyDetail(userDetails.getUsername());
            model.addAttribute("member", member);
            model.addAttribute("memberPasswordChangeRequest", new MemberPasswordChangeRequest());
            return "member/mypage";
        }

        try {
            // 2. 로그인한 회원 기준으로 내 정보 수정
            memberService.updateMyProfile(userDetails.getUsername(), memberUpdateRequest);

        } catch (BusinessException e) {
            // 3. 서비스 검증 예외(예: 이메일 중복)가 발생하면
            //    마이페이지에서 메시지를 보여주고 다시 렌더링
            MemberDetailResponse member = memberService.getMyDetail(userDetails.getUsername());
            model.addAttribute("member", member);
            model.addAttribute("memberUpdateRequest", memberUpdateRequest);
            model.addAttribute("memberPasswordChangeRequest", new MemberPasswordChangeRequest());

            // 공통 메시지 fragment 와 이름 통일
            model.addAttribute("errorMessage", e.getErrorCode().getMessage());
            return "member/mypage";
        }

        redirectAttributes.addFlashAttribute("successMessage", "회원정보가 수정되었습니다.");
        return "redirect:/members/me";
    }

    @PostMapping("/me/password")
    public String changePassword(@AuthenticationPrincipal CustomUserDetails userDetails,
                                 @Valid @ModelAttribute MemberPasswordChangeRequest memberPasswordChangeRequest,
                                 BindingResult bindingResult,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {

        // 1. Bean Validation 오류가 있으면 다시 마이페이지로
        if (bindingResult.hasErrors()) {
            MemberDetailResponse member = memberService.getMyDetail(userDetails.getUsername());

            MemberUpdateRequest memberUpdateRequest = new MemberUpdateRequest();
            memberUpdateRequest.setName(member.getName());
            memberUpdateRequest.setPhone(member.getPhone());
            memberUpdateRequest.setEmail(member.getEmail());

            model.addAttribute("member", member);
            model.addAttribute("memberUpdateRequest", memberUpdateRequest);
            return "member/mypage";
        }

        try {
            // 2. 로그인한 회원 기준으로 비밀번호 변경
            memberService.changePassword(userDetails.getUsername(), memberPasswordChangeRequest);

        } catch (BusinessException e) {
            // 3. 현재 비밀번호 불일치 등의 예외를 마이페이지에서 처리
            MemberDetailResponse member = memberService.getMyDetail(userDetails.getUsername());

            MemberUpdateRequest memberUpdateRequest = new MemberUpdateRequest();
            memberUpdateRequest.setName(member.getName());
            memberUpdateRequest.setPhone(member.getPhone());
            memberUpdateRequest.setEmail(member.getEmail());

            model.addAttribute("member", member);
            model.addAttribute("memberUpdateRequest", memberUpdateRequest);
            model.addAttribute("memberPasswordChangeRequest", memberPasswordChangeRequest);

            // 공통 메시지 fragment 와 이름 통일
            model.addAttribute("errorMessage", e.getErrorCode().getMessage());
            return "member/mypage";
        }

        redirectAttributes.addFlashAttribute("successMessage", "비밀번호가 변경되었습니다.");
        return "redirect:/members/me";
    }

    @PostMapping("/me/delete")
    public String deleteMember(@AuthenticationPrincipal CustomUserDetails userDetails) {

        // 현재 로그인한 사용자 ID 가져오기
        String memberId = userDetails.getMember().getMemberId();

        memberService.delete(memberId);

        return "redirect:/members/logout";
    }
}