package com.HairSalonProject2.board.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * BoardImage — 게시글 이미지 Entity
 * =====================================================
 * DB의 board_image 테이블과 1:1로 연결
 * Summernote 에디터에서 이미지 삽입 시 이 테이블에 저장
 *
 * Board와의 관계
 * Board (1) : BoardImage (N)
 * → 게시글 1개에 이미지 여러 장 가능
 *
 * 파일 저장 방식
 * 원본 파일명은 그대로 보존하고
 * 실제 저장 시엔 UUID로 새 이름을 만들어 저장
 * (같은 이름의 파일이 여러 개 올라와도 충돌 없음)
 */
@Entity
@Table(name = "board_image")
@Getter
@NoArgsConstructor
public class BoardImage {

    /**
     * 이미지 번호 (PK)
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long imageId;

    /**
     * 어느 게시글의 이미지인지 — 연관관계 주인
     * @ManyToOne → 여러 이미지가 하나의 게시글에 속함
     * @JoinColumn → board_id 컬럼으로 board 테이블과 연결
     * LAZY → 실제 필요할 때만 Board 조회 (성능 최적화)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "board_id", nullable = false)
    @Setter  // Board.addImage() 에서 설정
    private Board board;

    /**
     * 원본 파일명
     * 사용자가 올린 파일 이름 그대로 저장
     * 예) my_photo.jpg
     */
    @Column(nullable = false)
    private String originalName;

    /**
     * 저장된 파일명 (UUID)
     * 실제 디스크에 저장된 파일 이름
     * 예) a1b2c3d4-e5f6-7890-abcd-ef1234567890.jpg
     */
    @Column(nullable = false)
    private String savedName;

    /**
     * 저장 경로
     * application.properties 의 file.upload.path 기준
     * 예) C:/upload/board/
     */
    @Column(nullable = false)
    private String filePath;

    /**
     * 이미지 접근 URL
     * 브라우저에서 이미지를 불러올 때 사용하는 경로
     * 예) /upload/board/a1b2c3d4.jpg
     */
    @Column(nullable = false)
    private String imageUrl;

    /**
     * 업로드 일시
     */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;


    /* =============================================
       생성 메서드
       FileUploadService에서 호출
       BoardImage img = BoardImage.create(original, saved, path, url);
       ============================================= */

    /**
     * 이미지 생성
     * @param originalName 원본 파일명
     * @param savedName    저장된 파일명 (UUID)
     * @param filePath     저장 경로
     * @param imageUrl     접근 URL
     */
    public static BoardImage create(String originalName,
                                    String savedName,
                                    String filePath,
                                    String imageUrl) {
        BoardImage image = new BoardImage();
        image.originalName = originalName;
        image.savedName    = savedName;
        image.filePath     = filePath;
        image.imageUrl     = imageUrl;
        return image;
    }
}