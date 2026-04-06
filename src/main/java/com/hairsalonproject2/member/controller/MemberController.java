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

        // Bean Validation 실패 시 회원가입 폼으로 복귀
        if (bindingResult.hasErrors()) {
            return "member/signup";
        }

        try {
            memberService.signup(memberSignupRequest);
        } catch (BusinessException e) {
            // 중복 아이디, 중복 이메일, 비밀번호 확인 불일치 등은
            // 에러 페이지로 보내지 않고 회원가입 화면에서 바로 보여준다.
            model.addAttribute("signupErrorMessage", e.getErrorCode().getMessage());
            return "member/signup";
        }

        return "redirect:/members/login?signup=true";
    }

    @GetMapping("/login")
    public String loginForm(Model model,
                            @AuthenticationPrincipal CustomUserDetails userDetails) {

        // 이미 로그인한 사용자는 로그인 페이지에 다시 들어오지 못하게 한다.
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

        // Bean Validation 오류가 있으면 다시 마이페이지로 반환
        if (bindingResult.hasErrors()) {
            MemberDetailResponse member = memberService.getMyDetail(userDetails.getUsername());
            model.addAttribute("member", member);
            model.addAttribute("memberPasswordChangeRequest", new MemberPasswordChangeRequest());
            return "member/mypage";
        }

        try {
            // 로그인한 회원 기준으로 내 정보 수정
            memberService.updateMyProfile(userDetails.getUsername(), memberUpdateRequest);

        } catch (BusinessException e) {
            // 서비스 검증 예외가 발생하면 마이페이지에서 메시지를 보여주고 다시 렌더링한다.
            MemberDetailResponse member = memberService.getMyDetail(userDetails.getUsername());
            model.addAttribute("member", member);
            model.addAttribute("memberUpdateRequest", memberUpdateRequest);
            model.addAttribute("memberPasswordChangeRequest", new MemberPasswordChangeRequest());
            model.addAttribute("errorMessage", e.getErrorCode().getMessage());
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

        // Bean Validation 오류가 있으면 다시 마이페이지로 반환
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
            // 로그인한 회원 기준으로 비밀번호 변경
            memberService.changePassword(userDetails.getUsername(), memberPasswordChangeRequest);

        } catch (BusinessException e) {
            // 현재 비밀번호 불일치 등의 예외는 마이페이지에서 처리한다.
            MemberDetailResponse member = memberService.getMyDetail(userDetails.getUsername());

            MemberUpdateRequest memberUpdateRequest = new MemberUpdateRequest();
            memberUpdateRequest.setName(member.getName());
            memberUpdateRequest.setPhone(member.getPhone());
            memberUpdateRequest.setEmail(member.getEmail());

            model.addAttribute("member", member);
            model.addAttribute("memberUpdateRequest", memberUpdateRequest);
            model.addAttribute("memberPasswordChangeRequest", memberPasswordChangeRequest);
            model.addAttribute("errorMessage", e.getErrorCode().getMessage());
            return "member/mypage";
        }

        redirectAttributes.addFlashAttribute("successMessage", "비밀번호가 변경되었습니다.");
        return "redirect:/members/me";
    }

    @PostMapping("/me/delete")
    public String deleteMember(@AuthenticationPrincipal CustomUserDetails userDetails) {

        // 현재 로그인한 사용자 ID 조회
        String memberId = userDetails.getMember().getMemberId();

        memberService.delete(memberId);

        return "redirect:/members/logout";
    }
}
