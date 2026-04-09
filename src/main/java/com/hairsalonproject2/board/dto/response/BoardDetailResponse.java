package com.hairsalonproject2.board.dto.response;

import com.hairsalonproject2.board.entity.Board;
import com.hairsalonproject2.board.entity.BoardFile;
import com.hairsalonproject2.board.entity.BoardImage;
import com.hairsalonproject2.common.constant.BoardType;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Getter
public class BoardDetailResponse {

    private Integer boardId;
    private String type;
    private String title;
    private String content;
    private String memberId;
    private Integer viewCount;
    private Integer parentBoardId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<ImageInfo> images;
    private List<FileInfo> files;
    private List<BoardReplyResponse> replies;
    private boolean hidden;
    private String hiddenReason;
    private int reportCount;
    private boolean answered;

    public BoardDetailResponse() {
        this.images = List.of();
        this.files = List.of();
        this.replies = List.of();
    }

    public BoardDetailResponse(Integer boardId,
                               BoardType type,
                               String title,
                               String content,
                               String memberId,
                               Integer viewCount,
                               Integer parentBoardId,
                               LocalDateTime createdAt,
                               LocalDateTime updatedAt,
                               boolean hidden,
                               String hiddenReason,
                               Integer reportCount) {
        this();
        this.boardId = boardId;
        this.type = type != null ? type.name() : null;
        this.title = title;
        this.content = content;
        this.memberId = memberId;
        this.viewCount = viewCount;
        this.parentBoardId = parentBoardId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.hidden = hidden;
        this.hiddenReason = hiddenReason;
        this.reportCount = reportCount == null ? 0 : reportCount;
    }

    public static BoardDetailResponse from(Board board) {
        return new BoardDetailResponse(
                board.getBoardId(),
                board.getType(),
                board.getTitle(),
                board.getContent(),
                board.getMember().getMemberId(),
                board.getViewCount(),
                board.getParent() != null ? board.getParent().getBoardId() : null,
                board.getCreatedAt(),
                board.getUpdatedAt(),
                board.isHidden(),
                board.getHiddenReason(),
                board.getReportCount()
        );
    }

    public void setReplies(List<BoardReplyResponse> replies) {
        this.replies = replies;
    }

    public void setAnswered(boolean answered) {
        this.answered = answered;
    }

    public void setImages(List<ImageInfo> images) {
        this.images = images;
    }

    public void setFiles(List<FileInfo> files) {
        this.files = files;
    }

    @Getter
    public static class ImageInfo {

        private Long imageId;
        private String imageUrl;
        private String savedName;
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

    @Getter
    public static class FileInfo {

        private static final Set<String> IMAGE_EXTENSIONS = Set.of(
                "jpg", "jpeg", "png", "gif", "webp", "bmp"
        );

        private Long fileId;
        private String originalName;
        private String originalFilename;
        private String storedFilename;
        private Long fileSize;
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

        public boolean isImage() {
            return fileExtension != null
                    && IMAGE_EXTENSIONS.contains(fileExtension.toLowerCase(Locale.ROOT));
        }
    }
}
