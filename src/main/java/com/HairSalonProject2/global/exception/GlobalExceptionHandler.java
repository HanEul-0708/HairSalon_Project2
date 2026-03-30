package com.HairSalonProject2.global.exception;

import com.HairSalonProject2.board.exception.BoardException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * GlobalExceptionHandler — 전역 예외 처리기
 * =====================================================
 * 프로젝트 어디서든 예외가 터지면 여기서 한 번에 잡아서 처리
 * 각 Controller마다 try-catch 안 써도 되게 해줌
 *
 * @ControllerAdvice → 모든 Controller를 감시하는 특수 클래스
 * @Slf4j            → log.error() 같은 로그 기록 기능 자동 추가
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 게시판 관련 예외 처리
     * BoardService에서 throw new BoardException("...") 하면 여기서 잡힘
     *
     * 예) 존재하지 않는 게시글 조회
     *     수정/삭제 권한 없음
     *
     * @param e     발생한 BoardException
     * @param model Thymeleaf에 에러 메시지 전달
     * @return 에러 페이지로 이동
     */
    @ExceptionHandler(BoardException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleBoardException(BoardException e, Model model) {
        log.error("게시판 오류 발생: {}", e.getMessage());
        model.addAttribute("errorMessage", e.getMessage());
        return "error/board-error"; // templates/error/board-error.html
    }

    /**
     * 잘못된 요청 예외 처리
     * 파일 업로드 시 이미지가 아닌 파일 올릴 때 등
     *
     * @param e     발생한 IllegalArgumentException
     * @param model Thymeleaf에 에러 메시지 전달
     * @return 에러 페이지로 이동
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleIllegalArgumentException(IllegalArgumentException e, Model model) {
        log.error("잘못된 요청: {}", e.getMessage());
        model.addAttribute("errorMessage", e.getMessage());
        return "error/board-error";
    }

    /**
     * 그 외 모든 예외 처리 (최후의 보루)
     * 위에서 못 잡은 예외가 있으면 여기서 잡힘
     *
     * @param e     발생한 Exception
     * @param model Thymeleaf에 에러 메시지 전달
     * @return 공통 에러 페이지로 이동
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleException(Exception e, Model model) {
        log.error("예상치 못한 오류 발생: {}", e.getMessage());
        model.addAttribute("errorMessage", "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
        return "error/common-error"; // templates/error/common-error.html
    }
}