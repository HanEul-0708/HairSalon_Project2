package com.hairsalonproject2.board.dto.response;

import com.hairsalonproject2.board.entity.Board;
import com.hairsalonproject2.board.entity.BoardFile;
import com.hairsalonproject2.board.entity.BoardImage;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * BoardDetailResponse
 * 게시글 상세 화면 전용 DTO
 */
@Getter
public class BoardDetailResponse {

    /** 게시글 번호 (board 테이블은 INT) */
    private Integer boardId;

    /** 게시판 타입 */
    private String type;

    /** 제목 */
    private String title;

    /** 본문 */
    private String content;

    /** 작성자 회원 ID */
    private String memberId;

    /** 조회수 */
    private Integer viewCount;

    /** 부모 글 번호 (board 테이블은 INT) */
    private Integer parentBoardId;

    /** 작성일시 */
    private LocalDateTime createdAt;

    /** 수정일시 */
    private LocalDateTime updatedAt;

    /** 본문 이미지 목록 */
    private List<ImageInfo> images;

    /** 첨부파일 목록 */
    private List<FileInfo> files;

    /** 답글 목록 */
    private List<BoardReplyResponse> replies;

    /** 숨김 여부 */
    private boolean hidden;

    /** 숨김 사유 */
    private String hiddenReason;

    /** 신고 수 */
    private int reportCount;

    /** 답변 완료 여부 */
    private boolean answered;

    public static BoardDetailResponse from(Board board) {
        BoardDetailResponse response = new BoardDetailResponse();

        response.boardId = board.getBoardId();
        response.type = board.getType().name();
        response.title = board.getTitle();
        response.content = board.getContent();
        response.memberId = board.getMember().getMemberId();
        response.viewCount = board.getViewCount();
        response.parentBoardId = (board.getParent() != null)
                ? board.getParent().getBoardId()
                : null;
        response.createdAt = board.getCreatedAt();
        response.updatedAt = board.getUpdatedAt();
        response.hidden = board.isHidden();
        response.hiddenReason = board.getHiddenReason();
        response.reportCount = board.getReportCount() == null ? 0 : board.getReportCount();
        response.answered = !board.getChildren().isEmpty();

        response.images = board.getBoardImages().stream()
                .map(ImageInfo::from)
                .collect(Collectors.toList());

        response.files = board.getBoardFiles().stream()
                .map(FileInfo::from)
                .collect(Collectors.toList());

        return response;
    }

    public void setReplies(List<BoardReplyResponse> replies) {
        this.replies = replies;
    }

    /* =============================================
       본문 이미지 정보
       ============================================= */
    @Getter
    public static class ImageInfo {

        /** board_image.image_id = BIGINT */
        private Long imageId;

        /** 화면 출력용 URL */
        private String imageUrl;

        /** 저장된 파일명 */
        private String savedName;

        /** 원본 파일명 */
        private String originalName;

        public static ImageInfo from(BoardImage image) {
            ImageInfo info = new ImageInfo();
            info.imageId = image.getImageId();
            info.imageUrl = image.getImageUrl();
            info.savedName = image.getSavedName();
            info.originalName = image.getOriginalName();
            return info;
        }
    }

    /* =============================================
       첨부파일 정보
       ============================================= */
    @Getter
    public static class FileInfo {

        /** board_file.file_id = BIGINT */
        private Long fileId;

        /** 원본 파일명 */
        private String originalName;

        /**
         * 기존 템플릿 호환용
         * qna-detail / notice-detail 에서 originalFilename 쓸 수 있으므로 유지
         */
        private String originalFilename;

        /** 저장된 파일명 */
        private String storedFilename;

        /** 파일 크기 */
        private Long fileSize;

        /** 파일 확장자 */
        private String fileExtension;

        public static FileInfo from(BoardFile file) {
            FileInfo info = new FileInfo();
            info.fileId = file.getFileId();
            info.originalName = file.getOriginalName();
            info.originalFilename = file.getOriginalName();
            info.storedFilename = file.getSavedName();
            info.fileSize = file.getFileSize();
            info.fileExtension = file.getFileExtension();
            return info;
        }
    }
}