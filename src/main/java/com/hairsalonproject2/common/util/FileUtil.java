package com.hairsalonproject2.common.util;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * FileUtil — 파일 관련 공통 유틸리티 모음
 * =====================================================
 * 여러 곳에서 반복 사용되는 파일 관련 기능을 한 곳에 모아둠
 * 인스턴스 생성 없이 FileUtil.확장자추출() 형태로 바로 사용
 * <p>
 * static 메서드만 모아두는 유틸 클래스는 관습적으로
 * 생성자를 막아서 new FileUtil() 을 못 하게 함
 */
public class FileUtil {

 /**
  * 외부에서 new FileUtil() 생성 막기
  * 유틸 클래스는 인스턴스 필요 없음
  */
 private FileUtil() {
 }


    /* =============================================
       확장자 관련
       ============================================= */

 /**
  * 파일명에서 확장자 추출
  * "홍길동사진.jpg" → "jpg"
  *
  * @param filename 파일명
  * @return 확장자 소문자 (예: jpg, png, pdf)
  * @throws IllegalArgumentException 확장자 없는 파일명일 때
  */
 public static String extractExt(String filename) {
  if (filename == null || !filename.contains(".")) {
   throw new IllegalArgumentException("확장자가 없는 파일입니다: " + filename);
  }
  return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
 }

 /**
  * 이미지 파일 여부 확인
  * jpg, jpeg, png, gif, webp 만 이미지로 허용
  *
  * @param filename 파일명
  * @return true: 이미지 / false: 이미지 아님
  */
 public static boolean isImageFile(String filename) {
  List<String> imageExtList = List.of("jpg", "jpeg", "png", "gif", "webp");
  return imageExtList.contains(extractExt(filename));
 }

 /**
  * 허용된 첨부파일 여부 확인
  * 이미지 + 문서 + 압축파일까지 허용
  *
  * @param filename 파일명
  * @return true: 허용된 파일 / false: 허용되지 않은 파일
  */
 public static boolean isAllowedFile(String filename) {
  List<String> allowedExtList = List.of("jpg", "jpeg", "png", "gif", "webp",  // 이미지
		  "pdf", "doc", "docx", "xls", "xlsx",  // 문서
		  "zip", "rar"                           // 압축
  );
  return allowedExtList.contains(extractExt(filename));
 }


    /* =============================================
       파일 크기 관련
       ============================================= */

 /**
  * 파일 크기를 읽기 쉬운 문자열로 변환
  * 1024 → "1.0 KB"
  * 1048576 → "1.0 MB"
  *
  * @param bytes 파일 크기 (바이트)
  * @return 읽기 쉬운 크기 문자열
  */
 public static String formatFileSize(long bytes) {
  if (bytes < 1024) {
   return bytes + " B";
  } else if (bytes < 1024 * 1024) {
   return String.format("%.1f KB", bytes / 1024.0);
  } else {
   return String.format("%.1f MB", bytes / (1024.0 * 1024));
  }
 }

 /**
  * 파일 크기 제한 초과 여부 확인
  * 기본 제한: 10MB
  *
  * @param file      업로드된 파일
  * @param maxSizeMB 최대 허용 크기 (MB 단위)
  * @return true: 제한 초과 / false: 허용 범위 내
  */
 public static boolean isExceedMaxSize(MultipartFile file, int maxSizeMB) {
  long maxBytes = (long) maxSizeMB * 1024 * 1024;
  return file.getSize() > maxBytes;
 }


    /* =============================================
       파일명 관련
       ============================================= */

 /**
  * 파일명에서 확장자 제외한 이름 추출
  * "홍길동사진.jpg" → "홍길동사진"
  *
  * @param filename 파일명
  * @return 확장자 제외한 파일명
  */
 public static String extractBaseName(String filename) {
  if (filename == null || !filename.contains(".")) {
   return filename;
  }
  return filename.substring(0, filename.lastIndexOf("."));
 }
}