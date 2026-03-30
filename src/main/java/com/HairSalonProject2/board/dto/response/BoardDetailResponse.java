package com.HairSalonProject2.board.dto.response;

import com.HairSalonProject2.board.entity.Board;
import com.HairSalonProject2.board.entity.BoardFile;
import com.HairSalonProject2.board.entity.BoardImage;
import lombok.Getter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * BoardDetailResponse — 게시글 상세 페이지용 응답 DTO
 * =====================================================
 * 게시글 상세 화면에서 필요한 모든 정보를 담는 그릇
 * BoardResponse 보다 더 많은 정보 포함
 *
 * BoardResponse 와 차이점
 * - content (본문 HTML) 포함
 * - imageUrls (첨부 이미지 URL 목록) 포함
 * - files (첨부파일 목록) 포함
 * - updatedAt (수정일) 포함
 * - replies (답글 목록) 포함
 *
 * 사용 위치
 * GET /boards/notices/{boardId} → 공지사항 상세
 * GET /boards/qna/{boardId}     → QnA 상세
 */
@Getter
public class BoardDetailResponse {

    /** 게시글 번호 */
    private Long boardId;

    /** 게시글 유형 */
    private String type;

    /** 제목 */
    private String title;

    /**
     * 본문 HTML
     * Summernote 에디터로 작성된 HTML 그대로 저장됨
     * Thymeleaf 에서 th:utext 로 렌더링 (HTML 태그 살려서 출력)
     */
    private String content;

    /** 작성자 회원 ID */
    private String memberId;

    /** 조회수 */
    private int viewCount;

    /** 부모 게시글 ID (답글이면 값 있음, 원글이면 null) */
    private Long parentId;

    /** 작성일시 */
    private LocalDateTime createdAt;

    /** 수정일시 (수정한 적 없으면 null) */
    private LocalDateTime updatedAt;

    /**
     * 첨부 이미지 URL 목록
     * Summernote 에디터에서 올린 이미지들
     * 예) ["/upload/board/abc123.jpg", "/upload/board/def456.png"]
     */
    private List<String> imageUrls;

    /**
     * 첨부파일 정보 목록
     * 파일명과 다운로드 URL 포함
     */
    private List<FileInfo> files;

    /**
     * 답글 목록
     * 이 게시글에 달린 답글들
     * Service 에서 별도로 조회해서 세팅
     */
    private List<BoardResponse> replies;

    /**
     * Board Entity → BoardDetailResponse 변환
     *
     * @param board Board Entity (images, files Lazy 로딩 주의)
     * @return BoardDetailResponse
     */
    public static BoardDetailResponse from(Board board) {
        BoardDetailResponse res = new BoardDetailResponse();
        res.boardId   = board.getBoardId();
        res.type      = board.getType();
        res.title     = board.getTitle();
        res.content   = board.getContent();
        res.memberId  = board.getMemberId();
        res.viewCount = board.getViewCount();
        res.parentId  = board.getParentId();
        res.createdAt = board.getCreatedAt();
        res.updatedAt = board.getUpdatedAt();

        /* 이미지 URL 목록 변환 */
        res.imageUrls = board.getImages().stream()
                .map(BoardImage::getImageUrl)
                .collect(Collectors.toList());

        /* 첨부파일 목록 변환 */
        res.files = board.getFiles().stream()
                .map(FileInfo::from)
                .collect(Collectors.toList());

        return res;
    }

    /**
     * 답글 목록 세팅
     * Service 에서 답글 조회 후 별도로 호출
     *
     * @param replies 답글 목록
     */
    public void setReplies(List<BoardResponse> replies) {
        this.replies = replies;
    }

    /**
     * 작성일 포맷
     * 예) "2026-03-27 14:30"
     */
    public String getFormattedDate() {
        if (createdAt == null) return "";
        return createdAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    /**
     * 수정일 포맷
     * 수정한 적 없으면 빈 문자열 반환
     */
    public String getFormattedUpdatedDate() {
        if (updatedAt == null) return "";
        return updatedAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    /**
     * 원글 여부
     * Thymeleaf 에서 th:if="${board.original}" 로 사용
     */
    public boolean isOriginal() {
        return parentId == null;
    }

    /* =============================================
       내부 클래스 — 첨부파일 정보
       ============================================= */

    /**
     * FileInfo — 첨부파일 정보 (BoardDetailResponse 전용)
     * 파일 다운로드 링크와 표시 정보 담당
     */
    @Getter
    public static class FileInfo {

        /** 첨부파일 번호 */
        private Long fileId;

        /**
         * 원본 파일명 (화면에 표시)
         * th:text="${file.originalName}" 으로 사용
         */
        private String originalName;

        /**
         * HTML에서 file.originalFilename 으로 접근하는 용도
         * notice-detail.html / qna-detail.html 에서 사용
         */
        private String originalFilename;

        /**
         * 저장된 파일명 (UUID)
         * 다운로드 URL 생성에 사용
         * th:href 에서 file.storedFilename 으로 접근
         */
        private String storedFilename;

        /** 파일 크기 (예: "1.2 MB") */
        private String fileSize;

        /** 파일 확장자 (예: "pdf") */
        private String fileExtension;

        /**
         * BoardFile Entity → FileInfo 변환
         *
         * @param file BoardFile Entity
         * @return FileInfo
         */
        public static FileInfo from(BoardFile file) {
            FileInfo info = new FileInfo();
            info.fileId           = file.getFileId();
            info.originalName     = file.getOriginalName();
            info.originalFilename = file.getOriginalName();  // HTML 접근용
            info.storedFilename   = file.getSavedName();     // 다운로드 URL용
            info.fileSize         = file.getFileSizeFormatted();
            info.fileExtension    = file.getFileExtension();
            return info;
        }
    }
}