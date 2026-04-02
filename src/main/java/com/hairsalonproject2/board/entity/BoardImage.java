package com.hairsalonproject2.board.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "board_image")
public class BoardImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "image_id")
    private Long imageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "board_id", nullable = false)
    private Board board;

    /** 원본 파일명 */
    @Column(name = "original_name", nullable = false)
    private String originalName;

    /** 저장된 파일명 (UUID) */
    @Column(name = "saved_name", nullable = false)
    private String savedName;

    /** 저장 경로 */
    @Column(name = "file_path", nullable = false)
    private String filePath;

    /** 화면 출력 URL */
    @Column(name = "image_url", nullable = false)
    private String imageUrl;

    /** 생성 시간 */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

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

    public void changeBoard(Board board) {
        this.board = board;
    }
}