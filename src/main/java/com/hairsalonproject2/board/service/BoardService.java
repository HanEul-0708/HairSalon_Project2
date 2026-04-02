package com.hairsalonproject2.board.service;

import com.hairsalonproject2.admin.dto.response.BoardDeleteLogResponse;
import com.hairsalonproject2.admin.entity.BoardDeleteLog;
import com.hairsalonproject2.admin.repository.BoardDeleteLogRepository;
import com.hairsalonproject2.board.dto.request.BoardCreateRequest;
import com.hairsalonproject2.board.dto.request.BoardReplyRequest;
import com.hairsalonproject2.board.dto.request.BoardSearchRequest;
import com.hairsalonproject2.board.dto.request.BoardUpdateRequest;
import com.hairsalonproject2.board.dto.response.BoardDetailResponse;
import com.hairsalonproject2.board.dto.response.BoardReplyResponse;
import com.hairsalonproject2.board.dto.response.BoardResponse;
import com.hairsalonproject2.board.dto.response.BoardSummaryResponse;
import com.hairsalonproject2.board.entity.Board;
import com.hairsalonproject2.board.entity.BoardFile;
import com.hairsalonproject2.board.entity.BoardImage;
import com.hairsalonproject2.board.exception.BoardException;
import com.hairsalonproject2.board.repository.BoardRepository;
import com.hairsalonproject2.common.constant.BoardType;
import com.hairsalonproject2.common.file.FileStore;
import com.hairsalonproject2.common.file.entity.UploadFile;
import com.hairsalonproject2.common.file.service.FileUploadService;
import com.hairsalonproject2.common.util.HtmlSanitizer;
import com.hairsalonproject2.member.entity.Member;
import com.hairsalonproject2.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * BoardService
 * 게시판 비즈니스 로직
 */
@Service
@RequiredArgsConstructor
@Transactional
public class BoardService {

    private final BoardRepository boardRepository;
    private final MemberRepository memberRepository;
    private final BoardDeleteLogRepository boardDeleteLogRepository;
    private final FileStore fileStore;
    private final FileUploadService fileUploadService;

    @Value("${file.upload.path}")
    private String uploadPath;

    @Transactional(readOnly = true)
    public Page<BoardResponse> getNoticePage(int page, int size, String keyword) {
        return getBoardPage(BoardType.NOTICE, keyword, page, size);
    }

    @Transactional(readOnly = true)
    public Page<BoardResponse> getQnaPage(int page, int size, String keyword) {
        return getBoardPage(BoardType.QNA, keyword, page, size);
    }

    @Transactional(readOnly = true)
    public Page<BoardResponse> getBoardPage(BoardType type, String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), normalizePageSize(size));
        return boardRepository.searchPageByTypeAndKeyword(type, normalizeKeyword(keyword), pageable);
    }

    @Transactional(readOnly = true)
    public Page<BoardResponse> search(BoardSearchRequest request, int page, int size) {
        return getBoardPage(request.getType(), request.getKeyword(), page, size);
    }

    @Transactional(readOnly = true)
    public List<BoardSummaryResponse> getRecentNotices() {
        return boardRepository.findByTypeAndParentIsNullOrderByBoardIdDesc(BoardType.NOTICE)
                .stream()
                .filter(board -> !board.isHidden())
                .limit(5)
                .map(BoardSummaryResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<BoardResponse> getAdminBoardPage(BoardType type, String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), normalizePageSize(size));
        return boardRepository.searchAdminPage(type, normalizeKeyword(keyword), pageable);
    }

    @Transactional(readOnly = true)
    public long countOriginalBoards(BoardType type) {
        return boardRepository.countByTypeAndParentIsNullAndHiddenFalse(type);
    }

    @Transactional(readOnly = true)
    public long countTodayBoards() {
        return boardRepository.countByParentIsNullAndCreatedAtGreaterThanEqual(LocalDateTime.now().toLocalDate().atStartOfDay());
    }

    @Transactional(readOnly = true)
    public long countHiddenBoards() {
        return boardRepository.countByParentIsNullAndHiddenTrue();
    }

    @Transactional(readOnly = true)
    public long countReportedBoards() {
        return boardRepository.countByParentIsNullAndReportCountGreaterThan(0);
    }

    @Transactional(readOnly = true)
    public List<BoardDeleteLogResponse> getRecentDeleteLogs() {
        return boardDeleteLogRepository.findTop10ByOrderByCreatedAtDesc()
                .stream()
                .map(BoardDeleteLogResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BoardDetailResponse getDetailOnly(Integer boardId) {
        return buildDetailResponse(findBoard(boardId));
    }

    @Transactional(readOnly = true)
    public BoardDetailResponse getPublicDetail(Integer boardId, BoardType type) {
        Board board = findBoard(boardId);
        validatePublicBoard(board, type);
        return buildDetailResponse(board);
    }

    public BoardDetailResponse getDetailAndIncreaseView(Integer boardId) {
        Board board = findBoard(boardId);
        board.increaseViewCount();
        return buildDetailResponse(board);
    }

    public BoardDetailResponse getPublicDetailAndIncreaseView(Integer boardId, BoardType type) {
        Board board = findBoard(boardId);
        validatePublicBoard(board, type);
        board.increaseViewCount();
        return buildDetailResponse(board);
    }

    public void increaseViewCount(Integer boardId) {
        boardRepository.increaseViewCount(boardId);
    }

    @Transactional(readOnly = true)
    public BoardType getBoardType(Integer boardId) {
        return findBoard(boardId).getType();
    }

    public Integer create(BoardCreateRequest request,
                          String memberId,
                          List<MultipartFile> files,
                          BoardType boardType) {
        Member member = findMember(memberId);
        String safeContent = HtmlSanitizer.sanitize(request.getContent());

        Board board = Board.builder()
                .member(member)
                .type(boardType)
                .title(request.getTitle())
                .content(safeContent)
                .viewCount(0)
                .build();

        Board savedBoard = boardRepository.save(board);
        if (files != null && !files.isEmpty()) {
            saveFiles(savedBoard, files);
        }
        syncBoardImages(savedBoard);
        return savedBoard.getBoardId();
    }

    public Integer createReply(BoardReplyRequest request, String memberId) {
        Member member = findMember(memberId);
        Board parent = findBoard(request.getParentId().intValue());
        validateReplyParent(parent);

        String title = request.getTitle();
        if (title == null || title.trim().isEmpty()) {
            title = "Re: " + parent.getTitle();
        }

        Board reply = Board.builder()
                .member(member)
                .type(BoardType.QNA)
                .title(title)
                .content(HtmlSanitizer.sanitize(request.getContent()))
                .viewCount(0)
                .parent(parent)
                .build();

        Board savedReply = boardRepository.save(reply);
        syncBoardImages(savedReply);
        return savedReply.getBoardId();
    }

    public void update(Integer boardId,
                       BoardUpdateRequest request,
                       String memberId,
                       List<MultipartFile> files) {
        Board board = findBoard(boardId);
        if (!board.getMember().getMemberId().equals(memberId)) {
            throw new BoardException("수정 권한이 없습니다.");
        }
        if (board.isHidden()) {
            throw new BoardException("숨김 처리된 글은 수정할 수 없습니다.");
        }

        board.updateBoard(request.getTitle(), HtmlSanitizer.sanitize(request.getContent()));
        deleteSelectedFiles(board, request.getDeleteFileIds());
        if (files != null && !files.isEmpty()) {
            saveFiles(board, files);
        }
        syncBoardImages(board);
    }

    public void reportBoard(Integer boardId, String reporterId) {
        Board board = findBoard(boardId);
        if (board.getMember().getMemberId().equals(reporterId)) {
            throw new BoardException("본인 글은 신고할 수 없습니다.");
        }
        board.increaseReportCount();
    }

    public void hideBoard(Integer boardId, String reason) {
        Board board = findBoard(boardId);
        board.hide(normalizeReason(reason, "관리자 숨김 처리"));
    }

    public void unhideBoard(Integer boardId) {
        Board board = findBoard(boardId);
        board.unhide();
    }

    public void resetReportCount(Integer boardId) {
        Board board = findBoard(boardId);
        board.resetReportCount();
    }

    public void delete(Integer boardId, String memberId, boolean isAdmin) {
        Board board = findBoard(boardId);
        boolean isWriter = board.getMember().getMemberId().equals(memberId);
        if (!isAdmin && !isWriter) {
            throw new BoardException("삭제 권한이 없습니다.");
        }
        deleteBoardRecursively(board);
    }

    public void adminDelete(Integer boardId, String adminId, String reason) {
        Board board = findBoard(boardId);
        saveDeleteLog(board, adminId, normalizeReason(reason, "관리자 강제 삭제"));
        deleteBoardRecursively(board);
    }

    @Transactional(readOnly = true)
    public List<BoardResponse> getMyBoards(String memberId) {
        return boardRepository.findByMemberMemberIdOrderByBoardIdDesc(memberId)
                .stream()
                .map(BoardResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public boolean isMyBoard(Integer boardId, String memberId) {
        return boardRepository.findById(boardId)
                .map(board -> board.getMember().getMemberId().equals(memberId))
                .orElse(false);
    }

    private Member findMember(String memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BoardException("존재하지 않는 회원입니다."));
    }

    private Board findBoard(Integer boardId) {
        return boardRepository.findById(boardId)
                .orElseThrow(() -> new BoardException("존재하지 않는 게시글입니다."));
    }

    private BoardDetailResponse buildDetailResponse(Board board) {
        BoardDetailResponse response = BoardDetailResponse.from(board);
        List<BoardReplyResponse> replies = boardRepository.findByParentBoardIdOrderByBoardIdAsc(board.getBoardId())
                .stream()
                .filter(reply -> !reply.isHidden())
                .map(BoardReplyResponse::from)
                .collect(Collectors.toList());
        response.setReplies(replies);
        return response;
    }

    private void validatePublicBoard(Board board, BoardType type) {
        if (board.getType() != type) {
            throw new BoardException("게시판 종류가 올바르지 않습니다.");
        }
        if (board.isHidden()) {
            throw new BoardException("숨김 처리된 게시글입니다.");
        }
    }

    private int normalizePageSize(int size) {
        if (size <= 0) {
            return 10;
        }
        return Math.min(size, 50);
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeReason(String reason, String defaultValue) {
        if (reason == null || reason.trim().isEmpty()) {
            return defaultValue;
        }
        return reason.trim();
    }

    private void validateReplyParent(Board parent) {
        if (parent.getType() != BoardType.QNA) {
            throw new BoardException("문의글에만 답글을 작성할 수 있습니다.");
        }
        if (parent.isReply()) {
            throw new BoardException("답글에는 다시 답글을 달 수 없습니다.");
        }
        if (parent.isHidden()) {
            throw new BoardException("숨김 처리된 글에는 답글을 달 수 없습니다.");
        }
    }

    private void deleteSelectedFiles(Board board, List<Long> deleteFileIds) {
        if (deleteFileIds == null || deleteFileIds.isEmpty()) {
            return;
        }

        List<BoardFile> deleteTargets = board.getBoardFiles().stream()
                .filter(file -> deleteFileIds.contains(file.getFileId()))
                .collect(Collectors.toList());

        for (BoardFile file : deleteTargets) {
            fileStore.deleteFile(file.getSavedName());
            board.removeBoardFile(file);
        }
    }

    private void saveFiles(Board board, List<MultipartFile> files) {
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            try {
                UploadFile uploadFile = fileUploadService.uploadFile(file);
                String originalFilename = file.getOriginalFilename();
                String fileExtension = "";
                if (originalFilename != null && originalFilename.contains(".")) {
                    fileExtension = originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
                }

                BoardFile boardFile = BoardFile.builder()
                        .board(board)
                        .originalName(uploadFile.getOriginalFilename())
                        .savedName(uploadFile.getStoredFilename())
                        .filePath(uploadPath)
                        .fileSize(file.getSize())
                        .fileExtension(fileExtension)
                        .build();
                board.addBoardFile(boardFile);
            } catch (IOException e) {
                throw new BoardException("파일 저장 중 오류가 발생했습니다: " + file.getOriginalFilename());
            } catch (IllegalArgumentException e) {
                throw new BoardException(e.getMessage());
            }
        }
    }

    private void syncBoardImages(Board board) {
        List<ImageMeta> contentImages = extractEditorImages(board.getContent());
        Map<String, BoardImage> currentImageMap = board.getBoardImages().stream()
                .collect(Collectors.toMap(BoardImage::getImageUrl, image -> image, (a, b) -> a, LinkedHashMap::new));

        for (ImageMeta imageMeta : contentImages) {
            if (!currentImageMap.containsKey(imageMeta.imageUrl())) {
                String savedName = extractSavedNameFromImageUrl(imageMeta.imageUrl());
                if (savedName != null && !savedName.isBlank()) {
                    BoardImage boardImage = BoardImage.builder()
                            .board(board)
                            .originalName(resolveOriginalImageName(imageMeta, savedName))
                            .savedName(savedName)
                            .filePath(uploadPath)
                            .imageUrl(imageMeta.imageUrl())
                            .build();
                    board.addBoardImage(boardImage);
                }
            }
        }

        List<BoardImage> deleteTargets = board.getBoardImages().stream()
                .filter(image -> contentImages.stream().noneMatch(meta -> meta.imageUrl().equals(image.getImageUrl())))
                .collect(Collectors.toList());

        for (BoardImage image : deleteTargets) {
            fileStore.deleteFile(image.getSavedName());
            board.removeBoardImage(image);
        }
    }

    private List<ImageMeta> extractEditorImages(String html) {
        if (html == null || html.isBlank()) {
            return Collections.emptyList();
        }
        Document document = Jsoup.parseBodyFragment(html);
        List<ImageMeta> result = new ArrayList<>();
        for (Element image : document.select("img[src]")) {
            String src = image.attr("src").trim();
            if (!src.contains("/files/images/")) {
                continue;
            }
            String originalName = image.hasAttr("data-original-name") ? image.attr("data-original-name").trim() : null;
            result.add(new ImageMeta(src, originalName));
        }
        return result;
    }

    private String resolveOriginalImageName(ImageMeta imageMeta, String savedName) {
        if (imageMeta.originalName() != null && !imageMeta.originalName().isBlank()) {
            return imageMeta.originalName();
        }
        return savedName;
    }

    private String extractSavedNameFromImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }
        String normalized = imageUrl;
        int queryIndex = normalized.indexOf('?');
        if (queryIndex >= 0) {
            normalized = normalized.substring(0, queryIndex);
        }
        int slashIndex = normalized.lastIndexOf('/');
        if (slashIndex >= 0 && slashIndex + 1 < normalized.length()) {
            normalized = normalized.substring(slashIndex + 1);
        }
        return URLDecoder.decode(normalized, StandardCharsets.UTF_8);
    }

    private void deleteBoardRecursively(Board board) {
        List<Board> children = new ArrayList<>(board.getChildren());
        for (Board child : children) {
            deleteBoardRecursively(child);
        }

        board.getBoardFiles().forEach(file -> fileStore.deleteFile(file.getSavedName()));
        board.getBoardImages().forEach(image -> fileStore.deleteFile(image.getSavedName()));

        if (board.getParent() != null) {
            board.changeParent(null);
        }
        boardRepository.delete(board);
    }

    private void saveDeleteLog(Board board, String adminId, String reason) {
        BoardDeleteLog log = BoardDeleteLog.builder()
                .boardId(board.getBoardId())
                .boardType(board.getType())
                .boardTitle(board.getTitle())
                .writerId(board.getMember().getMemberId())
                .deletedBy(adminId)
                .deleteReason(reason)
                .build();
        boardDeleteLogRepository.save(log);
    }

    private record ImageMeta(String imageUrl, String originalName) {
    }
}
