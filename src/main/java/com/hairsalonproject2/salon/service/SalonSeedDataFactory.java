package com.hairsalonproject2.salon.service;

import com.hairsalonproject2.designer.constant.DesignerSpecialty;
import com.hairsalonproject2.designer.entity.Designer;
import com.hairsalonproject2.salon.entity.Salon;
import com.hairsalonproject2.salonservice.entity.SalonService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class SalonSeedDataFactory {
 private static final String[] FAMILY_NAMES = {"김", "이", "박", "최", "정", "강", "윤", "장"};
 private static final String[] GIVEN_NAME_FIRST = {"하", "서", "도", "시", "민", "유", "지", "태", "연", "주"};
 private static final String[] GIVEN_NAME_SECOND = {"늘", "준", "린", "안", "율", "빈", "원", "담", "솔", "온"};
 private static final String[] TITLES = {"점장", "부점장", "디자이너"};

 public List<Designer> createDesigners(Salon salon) {
  int seed = seedFor(salon);
  DesignerProfile first = buildProfile(salon, seed, 0);
  DesignerProfile second = buildProfile(salon, seed, 1);
  DesignerProfile third = buildProfile(salon, seed, 2);
  return List.of(Designer.builder().salon(salon).name(first.name()).introduction(first.introduction()).careerYears(first.careerYears()).specialty(first.specialty()).build(), Designer.builder().salon(salon).name(second.name()).introduction(second.introduction()).careerYears(second.careerYears()).specialty(second.specialty()).build(), Designer.builder().salon(salon).name(third.name()).introduction(third.introduction()).careerYears(third.careerYears()).specialty(third.specialty()).build());
 }

 public List<SalonService> createServices(Salon salon) {
  int seed = seedFor(salon);
  DesignerProfile cutProfile = buildProfile(salon, seed, 0);
  DesignerProfile permProfile = buildProfile(salon, seed, 1);
  DesignerProfile colorProfile = buildProfile(salon, seed, 2);
  DesignerProfile stylingProfile = buildProfile(salon, seed, 3);
  return List.of(buildService(salon, "여성 커트", cutPrice(cutProfile.careerYears(), cutProfile.specialty(), true), 60, cutProfile), buildService(salon, "남성 커트", cutPrice(cutProfile.careerYears(), cutProfile.specialty(), false), 45, cutProfile), buildService(salon, "염색", colorPrice(colorProfile.careerYears(), colorProfile.specialty()), 120, colorProfile), buildService(salon, "펌", permPrice(permProfile.careerYears(), permProfile.specialty()), 150, permProfile), buildService(salon, "스타일링", stylingPrice(stylingProfile.careerYears(), stylingProfile.specialty()), 50, stylingProfile));
 }

 private SalonService buildService(Salon salon, String serviceName, int price, int duration, DesignerProfile profile) {
  return SalonService.builder().salon(salon).name(serviceName).price(price).duration(duration).description(buildServiceDescription(serviceName, profile)).build();
 }

 private DesignerProfile buildProfile(Salon salon, int seed, int index) {
  int careerYears = 1 + Math.floorMod(seed + (index * 7), 10);
  DesignerSpecialty specialty = specialtyByIndex(seed + index);
  String name = buildDesignerName(seed, index);
  String introduction = buildIntroduction(careerYears, specialty);
  return new DesignerProfile(name, careerYears, specialty, introduction);
 }

 private String buildDesignerName(int seed, int index) {
  String familyName = FAMILY_NAMES[Math.floorMod(seed + index, FAMILY_NAMES.length)];
  String first = GIVEN_NAME_FIRST[Math.floorMod(seed + (index * 3), GIVEN_NAME_FIRST.length)];
  String second = GIVEN_NAME_SECOND[Math.floorMod(seed + (index * 5), GIVEN_NAME_SECOND.length)];
  return familyName + first + second + " " + titleByIndex(index);
 }

 private String titleByIndex(int index) {
  return TITLES[Math.floorMod(index, TITLES.length)];
 }

 private DesignerSpecialty specialtyByIndex(int index) {
  DesignerSpecialty[] values = DesignerSpecialty.values();
  return values[Math.floorMod(index, values.length)];
 }

 private String buildIntroduction(int careerYears, DesignerSpecialty specialty) {
  return "경력 " + careerYears + "년의 " + specialty.getLabel() + " 전문 디자이너입니다.";
 }

 private String buildServiceDescription(String serviceName, DesignerProfile profile) {
  return profile.careerYears() + "년 경력의 " + profile.specialty().getLabel() + " 전문 디자이너가 진행하는 " + serviceName + " 시술입니다.";
 }

 private int cutPrice(int careerYears, DesignerSpecialty specialty, boolean womens) {
  int base = womens ? 24000 : 18000;
  int specialtyBonus = specialty == DesignerSpecialty.CUT ? 12000 : specialty == DesignerSpecialty.PERM ? 6000 : specialty == DesignerSpecialty.STYLING ? 7000 : 4000;
  return base + (careerYears * 2000) + specialtyBonus;
 }

 private int colorPrice(int careerYears, DesignerSpecialty specialty) {
  int specialtyBonus = specialty == DesignerSpecialty.COLOR ? 18000 : specialty == DesignerSpecialty.PERM ? 10000 : specialty == DesignerSpecialty.STYLING ? 9000 : 8000;
  return 65000 + (careerYears * 3500) + specialtyBonus;
 }

 private int permPrice(int careerYears, DesignerSpecialty specialty) {
  int specialtyBonus = specialty == DesignerSpecialty.PERM ? 20000 : specialty == DesignerSpecialty.CUT ? 10000 : specialty == DesignerSpecialty.STYLING ? 12000 : 8000;
  return 80000 + (careerYears * 4000) + specialtyBonus;
 }

 private int stylingPrice(int careerYears, DesignerSpecialty specialty) {
  int specialtyBonus = specialty == DesignerSpecialty.STYLING ? 15000 : specialty == DesignerSpecialty.CUT ? 9000 : specialty == DesignerSpecialty.PERM ? 7000 : 6000;
  return 30000 + (careerYears * 2500) + specialtyBonus;
 }

 private int seedFor(Salon salon) {
  return Objects.hash(salon.getExternalId(), salon.getName(), salon.getAddress());
 }

 private record DesignerProfile(String name, int careerYears, DesignerSpecialty specialty, String introduction) {
 }
}
