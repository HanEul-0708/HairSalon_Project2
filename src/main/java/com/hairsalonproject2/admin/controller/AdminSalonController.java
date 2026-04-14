package com.hairsalonproject2.admin.controller;

import com.hairsalonproject2.admin.support.AdminPaginationUtils;
import com.hairsalonproject2.designer.dto.request.DesignerSearchRequest;
import com.hairsalonproject2.designer.dto.response.DesignerSummaryResponse;
import com.hairsalonproject2.designer.service.DesignerQueryService;
import com.hairsalonproject2.salon.dto.request.SalonSearchRequest;
import com.hairsalonproject2.salon.dto.response.SalonSummaryResponse;
import com.hairsalonproject2.designer.repository.DesignerRepository;
import com.hairsalonproject2.salon.repository.SalonRepository;
import com.hairsalonproject2.salon.service.SalonQueryService;
import com.hairsalonproject2.salonservice.dto.request.SalonServiceSearchRequest;
import com.hairsalonproject2.salonservice.repository.SalonServiceRepository;
import com.hairsalonproject2.salonservice.service.SalonServiceQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin")
public class AdminSalonController {
    private static final int PAGE_SIZE = 10;

    private final SalonQueryService salonQueryService;
    private final DesignerQueryService designerQueryService;
    private final SalonServiceQueryService salonServiceQueryService;
    private final SalonRepository salonRepository;
    private final DesignerRepository designerRepository;
    private final SalonServiceRepository salonServiceRepository;

    @GetMapping("/salons")
    public String salons(@ModelAttribute("search") SalonSearchRequest request,
                         @org.springframework.web.bind.annotation.RequestParam(defaultValue = "false") boolean todayOnly,
                         @org.springframework.web.bind.annotation.RequestParam(defaultValue = "1") int page,
                         Model model) {
        request.setSearched(true);
        List<SalonSummaryResponse> filteredSalons = salonQueryService.search(request).stream()
                .filter(salon -> !todayOnly || isToday(salon.getCreatedAt()))
                .toList();
        Page<SalonSummaryResponse> salonPage = AdminPaginationUtils.slice(filteredSalons, page, PAGE_SIZE);
        model.addAttribute("salonPage", salonPage);
        model.addAttribute("salons", salonPage.getContent());
        model.addAttribute("pageNumbers", AdminPaginationUtils.getPageNumbers(salonPage));
        model.addAttribute("currentPage", salonPage.getNumber() + 1);
        model.addAttribute("previousGroupPage", AdminPaginationUtils.getPreviousGroupPage(salonPage));
        model.addAttribute("nextGroupPage", AdminPaginationUtils.getNextGroupPage(salonPage));
        model.addAttribute("totalSalonCount", salonRepository.count());
        model.addAttribute("todaySalonCount", salonRepository.countByCreatedAtBetween(todayStart(), tomorrowStart()));
        model.addAttribute("todayOnly", todayOnly);
        model.addAttribute("currentMenu", "salons");
        return "admin/salons";
    }

    @GetMapping("/designers")
    public String designers(@ModelAttribute("search") DesignerSearchRequest request,
                            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "false") boolean todayOnly,
                            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "1") int page,
                            Model model) {
        request.setSearched(true);
        List<DesignerSummaryResponse> filteredDesigners = designerQueryService.search(request).stream()
                .filter(designer -> !todayOnly || isToday(designer.getCreatedAt()))
                .toList();
        Page<DesignerSummaryResponse> designerPage = AdminPaginationUtils.slice(filteredDesigners, page, PAGE_SIZE);
        model.addAttribute("designerPage", designerPage);
        model.addAttribute("designers", designerPage.getContent());
        model.addAttribute("pageNumbers", AdminPaginationUtils.getPageNumbers(designerPage));
        model.addAttribute("currentPage", designerPage.getNumber() + 1);
        model.addAttribute("previousGroupPage", AdminPaginationUtils.getPreviousGroupPage(designerPage));
        model.addAttribute("nextGroupPage", AdminPaginationUtils.getNextGroupPage(designerPage));
        model.addAttribute("totalDesignerCount", designerRepository.count());
        model.addAttribute("todayDesignerCount", designerRepository.countByCreatedAtBetween(todayStart(), tomorrowStart()));
        model.addAttribute("todayOnly", todayOnly);
        model.addAttribute("currentMenu", "designers");
        return "admin/designers";
    }

    @GetMapping("/salon-services")
    public String salonServices(@ModelAttribute("search") SalonServiceSearchRequest request,
                                @org.springframework.web.bind.annotation.RequestParam(defaultValue = "1") int page,
                                Model model) {
        request.setSearched(true);
        Page<?> servicePage = salonServiceQueryService.list(request, AdminPaginationUtils.normalizePageNumber(page) - 1, PAGE_SIZE);
        model.addAttribute("servicePage", servicePage);
        model.addAttribute("services", servicePage.getContent());
        model.addAttribute("pageNumbers", AdminPaginationUtils.getPageNumbers(servicePage));
        model.addAttribute("currentPage", servicePage.getNumber() + 1);
        model.addAttribute("previousGroupPage", AdminPaginationUtils.getPreviousGroupPage(servicePage));
        model.addAttribute("nextGroupPage", AdminPaginationUtils.getNextGroupPage(servicePage));
        model.addAttribute("totalServiceCount", salonServiceRepository.count());
        model.addAttribute("currentMenu", "services");
        return "admin/salon-services";
    }

    private LocalDateTime todayStart() {
        return LocalDate.now().atStartOfDay();
    }

    private LocalDateTime tomorrowStart() {
        return LocalDate.now().plusDays(1).atStartOfDay();
    }

    private boolean isToday(LocalDateTime value) {
        return value != null && !value.isBefore(todayStart()) && value.isBefore(tomorrowStart());
    }
}
