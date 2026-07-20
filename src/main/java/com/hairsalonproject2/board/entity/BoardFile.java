package com.hairsalonproject2.board.entity;

import com.hairsalonproject2.common.entity.BaseCreatedEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * BoardFile
 * 게시글 첨부파일 테이블
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "board_file")
public class BoardFile extends BaseCreatedEntity {

 /**
  * 파일 PK
  */
 @Id
 @GeneratedValue(strategy = GenerationType.IDENTITY)
 @Column(name = "file_id")
 private Long fileId;

 /**
  * 소속 게시글
  */
 @ManyToOne(fetch = FetchType.LAZY)
 @JoinColumn(name = "board_id", nullable = false)
 private Board board;

 /**
  * 원본 파일명
  */
 @Column(name = "original_name", nullable = false, length = 255)
 private String originalName;

 /**
  * 저장 파일명
  */
 @Column(name = "saved_name", nullable = false, length = 255)
 private String savedName;

 /**
  * 서버 저장 경로
  */
 @Column(name = "file_path", nullable = false, length = 255)
 private String filePath;

 /**
  * 파일 크기
  */
 @Column(name = "file_size", nullable = false)
 private Long fileSize;

 /**
  * 파일 확장자
  */
 @Column(name = "file_extension", length = 20)
 private String fileExtension;

 @Builder
 public BoardFile(Board board, String originalName, String savedName, String filePath, Long fileSize, String fileExtension) {
  this.board = board;
  this.originalName = originalName;
  this.savedName = savedName;
  this.filePath = filePath;
  this.fileSize = fileSize;
  this.fileExtension = fileExtension;
 }

 public void changeBoard(Board board) {
  this.board = board;
 }
}