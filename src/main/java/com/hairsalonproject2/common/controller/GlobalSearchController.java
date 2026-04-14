package com.hairsalonproject2.common.controller;

import com.hairsalonproject2.board.dto.response.BoardResponse;
import com.hairsalonproject2.board.service.BoardService;
import com.hairsalonproject2.designer.dto.request.DesignerSearchRequest;
import com.hairsalonproject2.member.service.CustomUserDetails;
import com.hairsalonproject2.salon.dto.request.SalonSearchRequest;
import com.hairsalonproject2.salon.service.SalonQueryService;
import com.hairsalonproject2.salonservice.dto.request.SalonServiceSearchRequest;
import com.hairsalonproject2.salonservice.service.SalonServiceQueryService;
import com.hairsalonproject2.designer.service.DesignerQueryService;
import com.hairsalonproject2.common.support.GlobalSearchTerms;
import com.hairsalonproject2.common.support.PageUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class GlobalSearchController {

    private static final int PREVIEW_SIZE = 4;

    private final SalonQueryService salonQueryService;
    private final DesignerQueryService designerQueryService;
    private final SalonServiceQueryService salonServiceQueryService;
    private final BoardService boardService;

    @GetMapping("/search")
    public String results(@RequestParam(required = false) String keyword,
                          @AuthenticationPrincipal CustomUserDetails userDetails,
                          Model model) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        if (normalizedKeyword.isBlank()) {
            return "redirect:/";
        }

        GlobalSearchTerms searchTerms = GlobalSearchTerms.parse(normalizedKeyword);
        var salonPage = salonQueryService.search(buildSalonSearchRequest(searchTerms), 0, PREVIEW_SIZE);
        var designerPage = designerQueryService.search(buildDesignerSearchRequest(searchTerms), 0, PREVIEW_SIZE);
        var servicePage = salonServiceQueryService.list(buildServiceSearchRequest(searchTerms), 0, PREVIEW_SIZE);
        String boardKeyword = searchTerms.boardKeyword(normalizedKeyword);
        var noticePage = boardService.getNoticePage(0, PREVIEW_SIZE, boardKeyword);
        var qnaPage = searchQna(boardKeyword, userDetails);

        model.addAttribute("keyword", normalizedKeyword);
        model.addAttribute("searchKeyword", searchTerms.keyword());
        model.addAttribute("searchCity", searchTerms.city());
        model.addAttribute("searchDistrict", searchTerms.district());
        model.addAttribute("searchNeighborhood", searchTerms.neighborhood());
        model.addAttribute("salons", salonPage.getContent());
        model.addAttribute("designers", designerPage.getContent());
        model.addAttribute("services", servicePage.getContent());
        model.addAttribute("notices", noticePage.getContent());
        model.addAttribute("qnas", qnaPage.getContent());
        model.addAttribute("salonCount", salonPage.getTotalElements());
        model.addAttribute("designerCount", designerPage.getTotalElements());
        model.addAttribute("serviceCount", servicePage.getTotalElements());
        model.addAttribute("noticeCount", noticePage.getTotalElements());
        model.addAttribute("qnaCount", qnaPage.getTotalElements());
        model.addAttribute("hasAnyResult",
                salonPage.getTotalElements() > 0
                        || designerPage.getTotalElements() > 0
                        || servicePage.getTotalElements() > 0
                        || noticePage.getTotalElements() > 0
                        || qnaPage.getTotalElements() > 0);
        return "search/results";
    }

    private SalonSearchRequest buildSalonSearchRequest(GlobalSearchTerms searchTerms) {
        SalonSearchRequest request = new SalonSearchRequest();
        request.setKeyword(searchTerms.keyword());
        request.setCity(searchTerms.city());
        request.setDistrict(searchTerms.district());
        request.setNeighborhood(searchTerms.neighborhood());
        request.setSearched(true);
        return request;
    }

    private DesignerSearchRequest buildDesignerSearchRequest(GlobalSearchTerms searchTerms) {
        DesignerSearchRequest request = new DesignerSearchRequest();
        request.setKeyword(searchTerms.keyword());
        request.setCity(searchTerms.city());
        request.setDistrict(searchTerms.district());
        request.setNeighborhood(searchTerms.neighborhood());
        request.setSearched(true);
        return request;
    }

    private SalonServiceSearchRequest buildServiceSearchRequest(GlobalSearchTerms searchTerms) {
        SalonServiceSearchRequest request = new SalonServiceSearchRequest();
        request.setKeyword(searchTerms.keyword());
        request.setCity(searchTerms.city());
        request.setDistrict(searchTerms.district());
        request.setNeighborhood(searchTerms.neighborhood());
        request.setSortBy("rating");
        request.setSearched(true);
        return request;
    }

    private Page<BoardResponse> searchQna(String keyword, CustomUserDetails userDetails) {
        if (userDetails == null) {
            return PageUtils.empty(PREVIEW_SIZE);
        }

        return boardService.getQnaPage(
                0,
                PREVIEW_SIZE,
                keyword,
                userDetails.getUsername(),
                canViewAllQna(userDetails)
        );
    }

    private boolean canViewAllQna(CustomUserDetails userDetails) {
        return userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                        || a.getAuthority().equals("ROLE_DESIGNER"));
    }

}
