package com.hairsalonproject2.board.entity;

import com.hairsalonproject2.common.entity.BaseCreatedEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * BoardImage
 *
 * 게시글 본문/첨부 이미지 테이블
 *
 * 변경 포인트
 * 1. created_at 을 직접 들고 있지 않고 BaseCreatedEntity 상속으로 통일
 * 2. 다른 created_at 전용 엔티티들과 스타일 통일
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "board_image")
public class BoardImage extends BaseCreatedEntity {

    /**
     * 이미지 PK
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "image_id")
    private Long imageId;

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
     * 저장된 파일명 (UUID 등)
     */
    @Column(name = "saved_name", nullable = false, length = 255)
    private String savedName;

    /**
     * 서버 저장 경로
     */
    @Column(name = "file_path", nullable = false, length = 255)
    private String filePath;

    /**
     * 화면 출력용 URL
     */
    @Column(name = "image_url", nullable = false, length = 255)
    private String imageUrl;

    @Builder
    public BoardImage(Board board,
                      String originalName,
                      String savedName,
                      String filePath,
                      String imageUrl) {
        this.board = board;
        this.originalName = originalName;
        this.savedName = savedName;
        this.filePath = filePath;
        this.imageUrl = imageUrl;
    }

    /**
     * 게시글 연관관계 변경
     */
    public void changeBoard(Board board) {
        this.board = board;
    }
}