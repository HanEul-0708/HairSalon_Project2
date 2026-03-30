package com.HairSalonProject2.board.exception;

/**
 * BoardException — 게시판 전용 예외 클래스
 * =====================================================
 * 게시판 기능에서 발생하는 오류를 표현하는 클래스
 * RuntimeException 을 상속해서 unchecked 예외로 처리
 *
 * 사용 예시
 * throw new BoardException("존재하지 않는 게시글입니다.");
 * throw new BoardException("삭제 권한이 없습니다.");
 *
 * GlobalExceptionHandler 에서 잡아서 처리됨
 */
public class BoardException extends RuntimeException {

    /**
     * 메시지만 받는 생성자
     * 가장 많이 사용
     *
     * @param message 오류 메시지 (화면에 표시)
     */
    public BoardException(String message) {
        super(message);
    }

    /**
     * 메시지 + 원인 예외 받는 생성자
     * 다른 예외를 감싸서 던질 때 사용
     *
     * @param message 오류 메시지
     * @param cause   원인 예외
     */
    public BoardException(String message, Throwable cause) {
        super(message, cause);
    }
}