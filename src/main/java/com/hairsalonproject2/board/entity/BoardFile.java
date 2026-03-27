package com.hairsalonproject2.board.entity;

import com.hairsalonproject2.common.entity.BaseCreatedEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * BoardFile
 *
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
    private Integer fileId;

    /**
     * 소속 게시글
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "board_id", nullable = false)
    private Board board;

    /**
     * 파일 URL
     */
    @Column(name = "file_url", nullable = false, length = 255)
    private String fileUrl;

    /**
     * 원본 파일명
     */
    @Column(name = "original_name", length = 255)
    private String originalName;

    /**
     * 저장 파일명
     */
    @Column(name = "saved_name", length = 255)
    private String savedName;

    /**
     * 파일 크기 (BIGINT)
     */
    @Column(name = "file_size")
    private Long fileSize;

    /**
     * MIME 타입
     */
    @Column(name = "content_type", length = 100)
    private String contentType;

    @Builder
    public BoardFile(Board board, String fileUrl,
                     String originalName, String savedName,
                     Long fileSize, String contentType) {
        this.board = board;
        this.fileUrl = fileUrl;
        this.originalName = originalName;
        this.savedName = savedName;
        this.fileSize = fileSize;
        this.contentType = contentType;
    }

    public void changeBoard(Board board) {
        this.board = board;
    }
}