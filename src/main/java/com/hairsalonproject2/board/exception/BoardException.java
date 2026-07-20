package com.hairsalonproject2.board.exception;

/**
 * BoardException
 * 게시판 전용 예외
 */
public class BoardException extends RuntimeException {

 public BoardException(String message) {
  super(message);
 }
}