package com.hairsalonproject2.board.entity;

import com.hairsalonproject2.common.constant.BoardType;
import com.hairsalonproject2.common.entity.BaseTimeEntity;
import com.hairsalonproject2.member.entity.Member;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Board
 * 게시글 정보 저장
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "board")
public class Board extends BaseTimeEntity {

    /**
     * 게시글 번호
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
     * 게시판 종류
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
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
    @Column(name = "content", nullable = false)
    private String content;

    /**
     * 조회수
     * null 이 되지 않게 기본값 0 유지
     */
    @Column(name = "view_count", nullable = false)
    private Integer viewCount = 0;

    /**
     * 숨김 여부
     * 관리자 화면에서 임시 차단할 때 사용
     */
    @Column(name = "hidden", nullable = false)
    private boolean hidden = false;

    /**
     * 숨김 처리 사유
     */
    @Column(name = "hidden_reason", length = 255)
    private String hiddenReason;

    /**
     * 신고 수
     */
    @Column(name = "report_count", nullable = false)
    private Integer reportCount = 0;

    /**
     * 부모 글
     * 답글이면 부모 글이 있고, 원글이면 null
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Board parent;

    /**
     * 자식 글 목록
     */
    @OneToMany(mappedBy = "parent",
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    private List<Board> children = new ArrayList<>();

    /**
     * 본문 안 이미지 목록
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
    public Board(Member member,
                 BoardType type,
                 String title,
                 String content,
                 Integer viewCount,
                 Board parent) {
        this.member = member;
        this.type = type;
        this.title = title;
        this.content = content;
        this.viewCount = (viewCount == null) ? 0 : viewCount;

        /*
         * 부모 글 연결
         * 단순 대입이 아니라 연결 메서드를 통해
         * 부모-자식 목록도 함께 맞춘다.
         */
        if (parent != null) {
            changeParent(parent);
        }
    }

    /**
     * 게시글 내용 수정
     */
    public void updateBoard(String title, String content) {
        this.title = title;
        this.content = content;
    }

    /**
     * 조회수 1 증가
     */
    public void increaseViewCount() {
        this.viewCount++;
    }

    /**
     * 신고 수 1 증가
     */
    public void increaseReportCount() {
        this.reportCount++;
    }

    /**
     * 신고 수 초기화
     */
    public void resetReportCount() {
        this.reportCount = 0;
    }

    /**
     * 관리자 숨김 처리
     */
    public void hide(String hiddenReason) {
        this.hidden = true;
        this.hiddenReason = hiddenReason;
    }

    /**
     * 숨김 해제
     */
    public void unhide() {
        this.hidden = false;
        this.hiddenReason = null;
    }

    /**
     * 부모 글 변경
     * 부모의 자식 목록까지 함께 맞춘다.
     */
    public void changeParent(Board parent) {
        /*
         * 기존 부모가 있으면 기존 부모의 자식 목록에서 먼저 제거
         */
        if (this.parent != null) {
            this.parent.children.remove(this);
        }

        this.parent = parent;

        /*
         * 새 부모가 있고, 아직 자식 목록에 없으면 추가
         */
        if (parent != null && !parent.children.contains(this)) {
            parent.children.add(this);
        }
    }

    /**
     * 이미지 추가
     */
    public void addBoardImage(BoardImage image) {
        if (image == null) {
            return;
        }

        this.boardImages.add(image);
        image.changeBoard(this);
    }

    /**
     * 이미지 제거
     */
    public void removeBoardImage(BoardImage image) {
        if (image == null) {
            return;
        }

        this.boardImages.remove(image);
        image.changeBoard(null);
    }

    /**
     * 첨부파일 추가
     */
    public void addBoardFile(BoardFile file) {
        if (file == null) {
            return;
        }

        this.boardFiles.add(file);
        file.changeBoard(this);
    }

    /**
     * 첨부파일 제거
     */
    public void removeBoardFile(BoardFile file) {
        if (file == null) {
            return;
        }

        this.boardFiles.remove(file);
        file.changeBoard(null);
    }

    /**
     * 원글인지 여부
     */
    public boolean isOriginal() {
        return this.parent == null;
    }

    /**
     * 답글인지 여부
     */
    public boolean isReply() {
        return this.parent != null;
    }
}