package com.hairsalonproject2.board.service;

import com.hairsalonproject2.board.entity.Board;
import com.hairsalonproject2.board.entity.BoardFile;
import com.hairsalonproject2.board.entity.BoardImage;
import com.hairsalonproject2.board.exception.BoardException;
import com.hairsalonproject2.common.file.FileStore;
import com.hairsalonproject2.common.file.entity.UploadFile;
import com.hairsalonproject2.common.file.service.FileUploadService;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * BoardAttachmentService
 *
 * 게시글 첨부파일 / 본문 이미지 처리 전담 서비스
 *
 * 담당 역할
 * 1. 첨부파일 저장
 * 2. 선택된 첨부파일 삭제
 * 3. 본문 이미지 추출
 * 4. board_image 동기화
 */
@Service
@RequiredArgsConstructor
public class BoardAttachmentService {

    private final FileStore fileStore;
    private final FileUploadService fileUploadService;

    @Value("${file.upload.path}")
    private String uploadPath;

    /**
     * 선택된 첨부파일 삭제
     */
    public void deleteSelectedFiles(Board board, List<Long> deleteFileIds) {
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

    /**
     * 첨부파일 저장
     */
    public void saveFiles(Board board, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return;
        }

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }

            try {
                UploadFile uploadFile = fileUploadService.uploadFile(file);

                String originalFilename = file.getOriginalFilename();
                String fileExtension = "";
                if (originalFilename != null && originalFilename.contains(".")) {
                    fileExtension = originalFilename.substring(originalFilename.lastIndexOf('.') + 1)
                            .toLowerCase(Locale.ROOT);
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

    /**
     * 본문 안 이미지와 board_image 테이블 동기화
     */
    public void syncBoardImages(Board board) {
        List<ImageMeta> contentImages = extractEditorImages(board.getContent());

        Map<String, BoardImage> currentImageMap = board.getBoardImages().stream()
                .collect(Collectors.toMap(
                        BoardImage::getImageUrl,
                        image -> image,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        // 본문에는 있는데 board_image 에 없는 이미지 추가
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

        // board_image 에는 있는데 본문에는 없는 이미지 제거
        List<BoardImage> deleteTargets = board.getBoardImages().stream()
                .filter(image -> contentImages.stream().noneMatch(meta -> meta.imageUrl().equals(image.getImageUrl())))
                .collect(Collectors.toList());

        for (BoardImage image : deleteTargets) {
            fileStore.deleteFile(image.getSavedName());
            board.removeBoardImage(image);
        }
    }

    /**
     * 게시글 삭제 시 첨부파일/이미지 실제 파일 삭제
     */
    public void deletePhysicalFiles(Board board) {
        board.getBoardFiles().forEach(file -> fileStore.deleteFile(file.getSavedName()));
        board.getBoardImages().forEach(image -> fileStore.deleteFile(image.getSavedName()));
    }

    /**
     * 에디터 본문에서 이미지 정보 추출
     */
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

            String originalName = image.hasAttr("data-original-name")
                    ? image.attr("data-original-name").trim()
                    : null;

            if ((originalName == null || originalName.isBlank()) && image.hasAttr("alt")) {
                originalName = image.attr("alt").trim();
            }

            result.add(new ImageMeta(src, originalName));
        }

        return result;
    }

    /**
     * 원본 이미지명 결정
     */
    private String resolveOriginalImageName(ImageMeta imageMeta, String savedName) {
        if (imageMeta.originalName() != null && !imageMeta.originalName().isBlank()) {
            return imageMeta.originalName();
        }
        return savedName;
    }

    /**
     * 이미지 URL에서 저장 파일명 추출
     */
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

    /**
     * 에디터 이미지 메타 정보
     */
    private record ImageMeta(String imageUrl, String originalName) {
    }
}
