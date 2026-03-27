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
    private Integer imageId;

    /**
     * 소속 게시글
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "board_id", nullable = false)
    private Board board;

    /**
     * 이미지 URL
     */
    @Column(name = "image_url", nullable = false, length = 255)
    private String imageUrl;

    /**
     * 정렬 순서
     */
    @Column(name = "sort_order")
    private Integer sortOrder;

    @Builder
    public BoardImage(Board board, String imageUrl, Integer sortOrder) {
        this.board = board;
        this.imageUrl = imageUrl;
        this.sortOrder = sortOrder;
    }

    public void changeBoard(Board board) {
        this.board = board;
    }
}