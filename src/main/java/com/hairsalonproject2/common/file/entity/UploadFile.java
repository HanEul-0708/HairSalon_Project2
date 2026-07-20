package com.hairsalonproject2.common.file.entity;

import lombok.Getter;

/**
 * UploadFile — 업로드된 파일 정보 묶음
 * =====================================================
 * DB 테이블이 아니라 순수 Java 객체 (Entity 아님!)
 * 파일 저장 후 "원래 이름 + 저장된 이름"을 한 세트로 들고 다니는 박스
 * <p>
 * 비유: 짐 보관소에 맡긴 짐 영수증
 * originalFilename  → 짐에 붙어있던 원래 이름표
 * storedFilename    → 보관소가 붙여준 번호표 (UUID)
 */
@Getter
public class UploadFile {

 /**
  * 사용자가 업로드한 원래 파일 이름 (예: 내사진.jpg)
  */
 private final String originalFilename;

 /**
  * 서버에 실제 저장된 파일 이름 (예: a3f9b2c1-xxxx.jpg)
  */
 private final String storedFilename;

 public UploadFile(String originalFilename, String storedFilename) {
  this.originalFilename = originalFilename;
  this.storedFilename = storedFilename;
 }
}