package com.hairsalonproject2.review.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * FileStorageService
 * <p>
 * 파일 저장소 추상화 인터페이스
 * <p>
 * 역할
 * 1. 업로드 파일을 저장
 * 2. 저장된 파일의 접근 경로(URL 또는 상대경로) 반환
 * <p>
 * 장점
 * - 현재는 로컬 저장
 * - 나중에 AWS S3 등으로 쉽게 교체 가능
 */
public interface FileStorageService {

 /**
  * 파일 저장
  *
  * @param file 업로드 파일
  * @return 저장된 파일 접근 경로
  */
 String storeFile(MultipartFile file);
}