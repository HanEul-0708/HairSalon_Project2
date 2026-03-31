package com.hairsalonproject2.board.entity;

import com.hairsalonproject2.common.constant.BoardType;
import com.hairsalonproject2.common.entity.BaseTimeEntity;
import com.hairsalonproject2.member.entity.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Board
 *
 * board 테이블과 매핑
 *
 * 특징
 * - 공지사항 / 문의게시판 구분
 * - parent_id 로 답글 구조 구현
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "board")
public class Board extends BaseTimeEntity {

    /**
     * 게시글 PK
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "board_id")
    private Integer boardId;

    /**
     * 작성자
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    /**
     * 게시판 타입 (NOTICE / QNA)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private BoardType type;

    /**
     * 제목
     */
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /**
     * 내용
     */
    @Lob
    @Column(name = "content")
    private String content;

    /**
     * 조회수
     */
    @Column(name = "view_count")
    private Integer viewCount;

    /**
     * 부모 글 (답글 구조)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Board parent;

    /**
     * 자식 글 목록 (답글)
     */
    @OneToMany(mappedBy = "parent")
    private List<Board> children = new ArrayList<>();

    /**
     * 게시글 이미지 목록
     */
    @OneToMany(mappedBy = "board",
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    private List<BoardImage> boardImages = new ArrayList<>();

    /**
     * 첨부파일 목록
     */
    @OneToMany(mappedBy = "board",
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    private List<BoardFile> boardFiles = new ArrayList<>();

    @Builder
    public Board(Member member, BoardType type,
                 String title, String content,
                 Integer viewCount, Board parent) {
        this.member = member;
        this.type = type;
        this.title = title;
        this.content = content;
        this.viewCount = viewCount;
        this.parent = parent;
    }

    /**
     * 게시글 수정
     */
    public void updateBoard(String title, String content) {
        this.title = title;
        this.content = content;
    }

    /**
     * 조회수 증가
     */
    public void increaseViewCount() {
        if (this.viewCount == null) {
            this.viewCount = 0;
        }
        this.viewCount++;
    }

    /**
     * 이미지 추가
     */
    public void addBoardImage(BoardImage image) {
        this.boardImages.add(image);
        image.changeBoard(this);
    }

    /**
     * 파일 추가
     */
    public void addBoardFile(BoardFile file) {
        this.boardFiles.add(file);
        file.changeBoard(this);
    }
}