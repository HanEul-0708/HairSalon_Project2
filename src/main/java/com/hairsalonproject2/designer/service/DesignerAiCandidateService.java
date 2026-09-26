package com.hairsalonproject2.designer.service;

import com.hairsalonproject2.designer.entity.Designer;
import com.hairsalonproject2.designer.llm.DesignerAiCandidatePrompt;
import com.hairsalonproject2.designer.projection.DesignerRatingRow;
import com.hairsalonproject2.designer.repository.DesignerRepository;
import com.hairsalonproject2.review.repository.ReviewRepository;
import com.hairsalonproject2.salonservice.entity.SalonService;
import com.hairsalonproject2.salonservice.repository.SalonServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Materializes every candidate inside a short transaction; no entities leave this service. */
@Service
@RequiredArgsConstructor
public class DesignerAiCandidateService {
 private final DesignerRepository designerRepository;
 private final SalonServiceRepository salonServiceRepository;
 private final ReviewRepository reviewRepository;

 @Transactional(readOnly = true)
 public List<DesignerAiCandidatePrompt> loadCandidates() {
  List<Designer> designers = designerRepository.findAll().stream()
   .filter(d -> d.getSalon() != null && d.getDesignerId() != null).toList();
  if (designers.isEmpty()) return List.of();
  List<Integer> designerIds = designers.stream().map(Designer::getDesignerId).toList();
  List<Integer> salonIds = designers.stream().map(d -> d.getSalon().getSalonId()).distinct().toList();
  Map<Integer, DesignerRatingRow> ratings = designerRepository.findDesignerRatingRowsByIds(designerIds).stream()
   .collect(Collectors.toMap(DesignerRatingRow::getDesignerId, Function.identity()));
  Map<Integer, LinkedHashSet<String>> services = new HashMap<>();
  for (SalonService service : salonServiceRepository.findBySalonSalonIdInOrderByServiceIdAsc(salonIds)) {
   String text = text(service.getName()) + (StringUtils.hasText(service.getDescription()) ? " - " + service.getDescription() : "");
   if (!StringUtils.hasText(text)) continue;
   LinkedHashSet<String> texts = services.computeIfAbsent(service.getSalon().getSalonId(), ignored -> new LinkedHashSet<>());
   if (texts.size() < 12) texts.add(text);
  }
  Map<Integer, List<String>> snippets = new HashMap<>();
  for (var review : reviewRepository.findFirstThreeSnippetsByDesignerIds(designerIds)) {
   String content = text(review.getContent()).trim().replaceAll("\\s+", " ");
   if (!StringUtils.hasText(content)) continue;
   snippets.computeIfAbsent(review.getDesignerId(), ignored -> new ArrayList<>())
    .add(content.substring(0, Math.min(90, content.length())).trim());
  }
  return designers.stream().map(d -> {
   DesignerRatingRow rating = ratings.get(d.getDesignerId());
   return new DesignerAiCandidatePrompt(d.getDesignerId(), d.getSalon().getSalonId(), text(d.getName()),
    text(d.getSalon().getName()), d.getSpecialty() == null ? "" : d.getSpecialty().name(), number(d.getCareerYears()),
    BigDecimal.valueOf(rating == null || rating.getAverageRating() == null ? 0 : rating.getAverageRating()).setScale(2, RoundingMode.HALF_UP),
    rating == null || rating.getReviewCount() == null ? 0L : rating.getReviewCount(), number(d.getLikeCount()), text(d.getIntroduction()),
    List.copyOf(services.getOrDefault(d.getSalon().getSalonId(), new LinkedHashSet<>())),
    List.copyOf(snippets.getOrDefault(d.getDesignerId(), List.of())));
  }).toList();
 }

 private static String text(String value) { return value == null ? "" : value; }
 private static int number(Integer value) { return value == null ? 0 : value; }
}
