package com.HairSalonProject2.board.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * BoardFile — 게시글 첨부파일 Entity
 * =====================================================
 * DB의 board_file 테이블과 1:1로 연결
 * 이미지 외 일반 파일 (PDF, 문서, 압축파일 등) 저장
 *
 * BoardImage와 구조는 거의 같지만
 * 파일 크기(fileSize) 컬럼이 추가됨
 * → 다운로드 전 파일 크기 미리 표시 가능
 *
 * Board와의 관계
 * Board (1) : BoardFile (N)
 */
@Entity
@Table(name = "board_file")
@Getter
@NoArgsConstructor
public class BoardFile {

    /**
     * 첨부파일 번호 (PK)
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long fileId;

    /**
     * 어느 게시글의 파일인지 — 연관관계 주인
     * @ManyToOne → 여러 파일이 하나의 게시글에 속함
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "board_id", nullable = false)
    @Setter  // Board.addFile() 에서 설정
    private Board board;

    /**
     * 원본 파일명
     * 예) 프로젝트_기획서.pdf
     */
    @Column(nullable = false)
    private String originalName;

    /**
     * 저장된 파일명 (UUID)
     * 예) a1b2c3d4-e5f6-7890-abcd-ef1234567890.pdf
     */
    @Column(nullable = false)
    private String savedName;

    /**
     * 저장 경로
     * 예) C:/upload/board/
     */
    @Column(nullable = false)
    private String filePath;

    /**
     * 파일 크기 (byte 단위)
     * 화면에서 KB / MB로 변환해서 표시
     * 예) 1048576 → 1.0 MB
     */
    @Column(nullable = false)
    private Long fileSize;

    /**
     * 파일 확장자
     * 아이콘 표시에 활용
     * 예) pdf, docx, zip
     */
    @Column(length = 20)
    private String fileExtension;

    /**
     * 업로드 일시
     */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;


    /* =============================================
       생성 메서드
       FileUploadService에서 호출
       BoardFile file = BoardFile.create(original, saved, path, size, ext);
       ============================================= */

    /**
     * 첨부파일 생성
     * @param originalName  원본 파일명
     * @param savedName     저장된 파일명 (UUID)
     * @param filePath      저장 경로
     * @param fileSize      파일 크기 (byte)
     * @param fileExtension 파일 확장자
     */
    public static BoardFile create(String originalName,
                                   String savedName,
                                   String filePath,
                                   Long fileSize,
                                   String fileExtension) {
        BoardFile file = new BoardFile();
        file.originalName  = originalName;
        file.savedName     = savedName;
        file.filePath      = filePath;
        file.fileSize      = fileSize;
        file.fileExtension = fileExtension;
        return file;
    }

    /**
     * 파일 크기를 사람이 읽기 쉬운 형태로 변환
     * Service / HTML에서 활용
     * 예) 1048576 → "1.0 MB"
     *     512000  → "500.0 KB"
     */
    public String getFileSizeFormatted() {
        if (fileSize == null) return "0 B";
        if (fileSize >= 1_048_576) {
            return String.format("%.1f MB", fileSize / 1_048_576.0);
        } else if (fileSize >= 1_024) {
            return String.format("%.1f KB", fileSize / 1_024.0);
        } else {
            return fileSize + " B";
        }
    }
}