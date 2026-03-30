package com.HairSalonProject2.common.file.service;

import com.HairSalonProject2.common.file.FileStore;
import com.HairSalonProject2.common.file.dto.FileResponse;
import com.HairSalonProject2.common.file.entity.UploadFile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * FileUploadService — 파일 업로드 비즈니스 로직
 * =====================================================
 * FileStore(실제 저장 담당)를 사용해서
 * "업로드 → 결과 반환" 흐름을 처리하는 서비스
 *
 * 비유: FileStore가 실제 짐을 옮기는 직원이라면
 *       FileUploadService는 어떤 짐을 어디로 보낼지 지시하는 팀장
 *
 * 사용처
 * 1. 썸머노트 에디터 이미지 업로드 (이미지 1개)
 * 2. 게시글 첨부파일 업로드 (파일 여러 개)
 */
@Service
@RequiredArgsConstructor
public class FileUploadService {

    private final FileStore fileStore;

    /* =============================================
       이미지 업로드 (썸머노트 에디터 전용)
       ============================================= */

    /**
     * 에디터 이미지 1개 업로드
     * 썸머노트가 이미지 붙여넣기/첨부 시 자동 호출
     *
     * @param imageFile 에디터에서 올라온 이미지 파일
     * @return FileResponse (url 포함) — 썸머노트가 이 url로 이미지 표시
     * @throws IOException 저장 실패 시
     */
    public FileResponse uploadEditorImage(MultipartFile imageFile) throws IOException {

        // 빈 파일 방어
        if (imageFile == null || imageFile.isEmpty()) {
            throw new IllegalArgumentException("이미지 파일이 비어있습니다.");
        }

        // 이미지 파일인지 확인 (jpg, png, gif, webp만 허용)
        validateImageFile(imageFile);

        // 실제 저장
        UploadFile uploadFile = fileStore.storeFile(imageFile);

        // 브라우저에서 접근 가능한 URL 생성
        // WebConfig에서 /files/images/** → C:/upload/ 로 매핑 예정
        String accessUrl = "/files/images/" + uploadFile.getStoredFilename();

        return new FileResponse(
                accessUrl,
                uploadFile.getOriginalFilename(),
                uploadFile.getStoredFilename()
        );
    }

    /* =============================================
       첨부파일 업로드 (게시글 파일 첨부)
       ============================================= */

    /**
     * 첨부파일 1개 업로드
     *
     * @param file 첨부할 파일
     * @return FileResponse
     * @throws IOException 저장 실패 시
     */
    public FileResponse uploadFile(MultipartFile file) throws IOException {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("파일이 비어있습니다.");
        }

        UploadFile uploadFile = fileStore.storeFile(file);

        // 첨부파일 다운로드 URL
        String accessUrl = "/files/download/" + uploadFile.getStoredFilename();

        return new FileResponse(
                accessUrl,
                uploadFile.getOriginalFilename(),
                uploadFile.getStoredFilename()
        );
    }

    /**
     * 첨부파일 여러 개 업로드
     * 게시글 작성 시 파일 여러 개 한 번에 올릴 때 사용
     *
     * @param files 첨부파일 목록
     * @return FileResponse 목록
     * @throws IOException 저장 실패 시
     */
    public List<FileResponse> uploadFiles(List<MultipartFile> files) throws IOException {

        List<FileResponse> result = new ArrayList<>();

        for (MultipartFile file : files) {
            // 빈 파일은 건너뜀 (파일 첨부 안 한 칸이 있을 수 있음)
            if (file == null || file.isEmpty()) continue;
            result.add(uploadFile(file));
        }

        return result;
    }

    /* =============================================
       파일 삭제
       ============================================= */

    /**
     * 파일 삭제 (게시글 삭제 시 연동)
     * Board 삭제 → BoardFile/BoardImage 삭제 → 실제 파일도 삭제
     *
     * @param storedFilename 삭제할 저장 파일명
     */
    public void deleteFile(String storedFilename) {
        fileStore.deleteFile(storedFilename);
    }

    /* =============================================
       내부 유효성 검사
       ============================================= */

    /**
     * 이미지 파일 여부 검사
     * jpg, jpeg, png, gif, webp 만 허용
     *
     * @param file 검사할 파일
     * @throws IllegalArgumentException 이미지가 아닌 파일일 때
     */
    private void validateImageFile(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new IllegalArgumentException("파일 이름이 없습니다.");
        }

        // 확장자 추출 후 소문자로 변환
        String ext = originalFilename
                .substring(originalFilename.lastIndexOf(".") + 1)
                .toLowerCase();

        List<String> allowedExt = List.of("jpg", "jpeg", "png", "gif", "webp");

        if (!allowedExt.contains(ext)) {
            throw new IllegalArgumentException(
                    "이미지 파일만 업로드 가능합니다. (jpg, png, gif, webp)");
        }
    }
}