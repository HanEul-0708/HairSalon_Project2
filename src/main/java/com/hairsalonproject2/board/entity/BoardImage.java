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
 * 게시글 이미지 테이블
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
     * 브라우저 접근용 이미지 URL
     */
    @Column(name = "image_url", nullable = false, length = 255)
    private String imageUrl;

    @Builder
    public BoardImage(Board board, String originalName, String savedName,
                      String filePath, String imageUrl) {
        this.board = board;
        this.originalName = originalName;
        this.savedName = savedName;
        this.filePath = filePath;
        this.imageUrl = imageUrl;
    }

    public void changeBoard(Board board) {
        this.board = board;
    }
}