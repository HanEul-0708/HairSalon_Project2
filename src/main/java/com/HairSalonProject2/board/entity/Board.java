package com.HairSalonProject2.board.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Board — 게시글 Entity
 * =====================================================
 * DB의 board 테이블과 1:1로 연결되는 클래스
 * JPA가 이 클래스를 보고 SQL을 자동으로 만들어줌
 *
 * 연관관계
 * Board (1) : BoardImage (N) — 게시글 1개에 이미지 여러 개
 * Board (1) : BoardFile  (N) — 게시글 1개에 파일 여러 개
 *
 * cascade = CascadeType.ALL
 * → Board 저장/삭제 시 연결된 이미지/파일도 함께 처리
 *
 * orphanRemoval = true
 * → Board에서 이미지/파일 연결이 끊기면 DB에서도 자동 삭제
 *   (이게 없으면 삭제된 게시글의 이미지 정보가 DB에 둥둥 떠다님)
 */
@Entity                          // "이 클래스는 DB 테이블이야" 선언
@Table(name = "board")           // 연결할 테이블 이름
@Getter                          // Lombok: getXxx() 메서드 자동 생성
@NoArgsConstructor               // Lombok: 기본 생성자 자동 생성 (JPA 필수)
public class Board {

    /**
     * 게시글 번호 (PK)
     * AUTO_INCREMENT — DB가 자동으로 번호 부여
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long boardId;

    /**
     * 작성자 회원 ID
     * member 테이블의 member_id를 문자열로 저장
     * (Member Entity와 JOIN하지 않고 단순 문자열로 관리)
     */
    @Column(nullable = false)
    private String memberId;

    /**
     * 게시글 유형
     * NOTICE : 공지사항 (관리자만 작성 가능)
     * QNA    : 문의게시판 (회원 누구나 작성 가능)
     */
    @Column(nullable = false, length = 10)
    private String type;

    /**
     * 게시글 제목
     */
    @Column(nullable = false, length = 200)
    private String title;

    /**
     * 게시글 본문
     * @Lob → 긴 텍스트 저장용 (Summernote 에디터 HTML 내용)
     * TEXT 타입으로 저장됨
     */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String content;

    /**
     * 조회수
     * 기본값 0, 게시글 열람 시마다 +1
     */
    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private int viewCount = 0;

    /**
     * 부모 게시글 ID (답글 구조)
     * 원글이면 null
     * 답글이면 원글의 boardId 저장
     * 예) 공지사항 답글, QnA 답변
     */
    @Column
    private Long parentId;

    /**
     * 작성일시
     * @CreationTimestamp → INSERT 시 현재 시간 자동 저장
     * updatable = false → 이후 수정해도 작성일은 바뀌지 않음
     */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 수정일시
     * @UpdateTimestamp → UPDATE 시 현재 시간 자동 갱신
     * 수정한 적 없으면 null
     */
    @UpdateTimestamp
    @Column
    private LocalDateTime updatedAt;

    /**
     * 연결된 이미지 목록
     * mappedBy = "board" → BoardImage.board 필드가 연관관계 주인
     * cascade = ALL      → Board 저장/삭제 시 이미지도 함께 처리
     * orphanRemoval      → 연결 끊긴 이미지 자동 삭제
     * fetch = LAZY       → 실제 필요할 때만 DB 조회 (성능 최적화)
     */
    @OneToMany(
            mappedBy = "board",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    private List<BoardImage> images = new ArrayList<>();

    /**
     * 연결된 첨부파일 목록
     * 이미지와 동일한 cascade / orphanRemoval 설정
     */
    @OneToMany(
            mappedBy = "board",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    private List<BoardFile> files = new ArrayList<>();


    /* =============================================
       생성 메서드
       new Board() 대신 이 메서드로 생성 권장
       Service에서: Board board = Board.create(memberId, type, title, content);
       ============================================= */

    /**
     * 원글 생성
     * @param memberId 작성자 ID
     * @param type     게시글 유형 (NOTICE / QNA)
     * @param title    제목
     * @param content  본문
     */
    public static Board create(String memberId, String type,
                               String title, String content) {
        Board board = new Board();
        board.memberId = memberId;
        board.type     = type;
        board.title    = title;
        board.content  = content;
        // parentId는 null → 원글
        return board;
    }

    /**
     * 답글 생성
     * @param memberId 작성자 ID
     * @param type     게시글 유형
     * @param title    제목
     * @param content  본문
     * @param parentId 원글 boardId
     */
    public static Board createReply(String memberId, String type,
                                    String title, String content,
                                    Long parentId) {
        Board board = create(memberId, type, title, content);
        board.parentId = parentId;
        return board;
    }


    /* =============================================
       비즈니스 메서드
       Service에서 호출해서 상태 변경
       ============================================= */

    /**
     * 게시글 수정
     * @param title   새 제목
     * @param content 새 본문
     */
    public void update(String title, String content) {
        this.title   = title;
        this.content = content;
        // updatedAt은 @UpdateTimestamp가 자동 갱신
    }

    /**
     * 조회수 1 증가
     * 게시글 상세 페이지 열람 시 호출
     */
    public void increaseViewCount() {
        this.viewCount++;
    }

    /**
     * 이미지 추가
     * @param image 추가할 BoardImage
     */
    public void addImage(BoardImage image) {
        this.images.add(image);
        // BoardImage의 board 필드도 이 Board로 설정
        image.setBoard(this);
    }

    /**
     * 파일 추가
     * @param file 추가할 BoardFile
     */
    public void addFile(BoardFile file) {
        this.files.add(file);
        file.setBoard(this);
    }

    /**
     * 원글 여부 확인
     * @return true: 원글 / false: 답글
     */
    public boolean isOriginal() {
        return this.parentId == null;
    }

    /**
     * 작성자 본인 여부 확인
     * @param memberId 확인할 회원 ID
     * @return true: 본인 / false: 타인
     */
    public boolean isWriter(String memberId) {
        return this.memberId.equals(memberId);
    }
}