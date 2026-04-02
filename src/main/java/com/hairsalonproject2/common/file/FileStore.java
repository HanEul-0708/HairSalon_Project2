package com.hairsalonproject2.common.file;

import com.hairsalonproject2.common.file.entity.UploadFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * FileStore — 파일을 실제 디스크에 저장/삭제하는 클래스
 * =====================================================
 * Service는 "뭘 저장할지" 결정하고
 * FileStore는 "어떻게 저장할지" 실행하는 역할 분담
 *
 * @Component → 스프링이 자동으로 관리해주는 부품으로 등록
 * @Value     → application.properties 값을 자동으로 읽어옴
 */
@Component
public class FileStore {

    /**
     * 파일 저장 경로
     * application.properties 에 file.upload.path=C:/upload/ 설정 필요
     */
    @Value("${file.upload.path}")
    private String uploadPath;

    /**
     * 파일 1개 저장
     *
     * @param multipartFile 브라우저에서 올라온 파일
     * @return UploadFile (원래이름 + 저장된이름) — 파일이 비어있으면 null
     * @throws IOException 저장 실패 시
     */
    public UploadFile storeFile(MultipartFile multipartFile) throws IOException {

        // 빈 파일이면 null 반환 (파일 첨부 안 한 경우)
        if (multipartFile == null || multipartFile.isEmpty()) {
            return null;
        }

        // 원래 파일 이름 (예: 내사진.jpg)
        String originalFilename = multipartFile.getOriginalFilename();

        // 서버 저장용 이름 생성 (UUID + 확장자)
        String storedFilename = createStoredFilename(originalFilename);

        // 실제 저장 — transferTo가 파일을 디스크에 씀
        multipartFile.transferTo(new File(getFullPath(storedFilename)));

        return new UploadFile(originalFilename, storedFilename);
    }

    /**
     * 파일 삭제
     * 게시글 삭제 시 실제 파일도 함께 지워야 함
     *
     * @param storedFilename 삭제할 저장 파일명
     */
    public void deleteFile(String storedFilename) {
        if (storedFilename == null || storedFilename.isBlank()) return;

        File file = new File(getFullPath(storedFilename));
        if (file.exists()) {
            file.delete();
        }
    }

    /**
     * 저장 경로 + 파일명 합치기
     * 예) C:/upload/ + a3f9b2c1.jpg = C:/upload/a3f9b2c1.jpg
     */
    public String getFullPath(String storedFilename) {
        return uploadPath + storedFilename;
    }

    /**
     * UUID 기반 저장용 파일명 생성
     * 원래이름의 확장자(jpg, png 등)만 뽑아서 UUID 뒤에 붙임
     *
     * @param originalFilename 원래 파일명
     * @return UUID.확장자 (예: a3f9b2c1-1234-xxxx.jpg)
     */
    private String createStoredFilename(String originalFilename) {
        String ext = extractExt(originalFilename);  // 확장자 추출
        return UUID.randomUUID() + "." + ext;       // UUID + 확장자
    }

    /**
     * 확장자 추출
     * "내사진.jpg" → "jpg"
     */
    private String extractExt(String originalFilename) {
        int dotPos = originalFilename.lastIndexOf(".");
        return originalFilename.substring(dotPos + 1);
    }
}