package com.hairsalonproject2.board.service;

import com.hairsalonproject2.board.dto.request.BoardCreateRequest;
import com.hairsalonproject2.board.dto.request.BoardReplyRequest;
import com.hairsalonproject2.board.dto.request.BoardUpdateRequest;
import com.hairsalonproject2.board.entity.Board;
import com.hairsalonproject2.board.exception.BoardException;
import com.hairsalonproject2.board.repository.BoardRepository;
import com.hairsalonproject2.common.constant.BoardType;
import com.hairsalonproject2.common.util.HtmlSanitizer;
import com.hairsalonproject2.member.entity.Member;
import com.hairsalonproject2.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class BoardCommandService {

    private final BoardRepository boardRepository;
    private final MemberRepository memberRepository;
    private final BoardAttachmentService boardAttachmentService;

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
            boardAttachmentService.saveFiles(savedBoard, files);
        }

        boardAttachmentService.syncBoardImages(savedBoard);
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
        boardAttachmentService.syncBoardImages(savedReply);
        return savedReply.getBoardId();
    }

    public void update(Integer boardId,
                       BoardUpdateRequest request,
                       String memberId,
                       List<MultipartFile> files,
                       boolean isAdmin) {

        Board board = findBoard(boardId);
        boolean canEditNoticeAsAdmin = isAdmin && board.getType() == BoardType.NOTICE;

        if (!canEditNoticeAsAdmin && !board.getMember().getMemberId().equals(memberId)) {
            throw new BoardException("수정 권한이 없습니다.");
        }

        if (board.isHidden()) {
            throw new BoardException("숨김 처리된 글은 수정할 수 없습니다.");
        }

        board.updateBoard(request.getTitle(), HtmlSanitizer.sanitize(request.getContent()));

        boardAttachmentService.deleteSelectedFiles(board, request.getDeleteFileIds());

        if (files != null && !files.isEmpty()) {
            boardAttachmentService.saveFiles(board, files);
        }

        boardAttachmentService.syncBoardImages(board);
    }

    public void delete(Integer boardId, String memberId, boolean isAdmin) {
        Board board = findBoard(boardId);

        boolean isWriter = board.getMember().getMemberId().equals(memberId);
        if (!isAdmin && !isWriter) {
            throw new BoardException("삭제 권한이 없습니다.");
        }

        deleteBoardRecursively(board);
    }

    private Member findMember(String memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BoardException("존재하지 않는 회원입니다."));
    }

    private Board findBoard(Integer boardId) {
        return boardRepository.findById(boardId)
                .orElseThrow(() -> new BoardException("존재하지 않는 게시글입니다."));
    }

    private void validateReplyParent(Board parent) {
        if (parent.getType() != BoardType.QNA) {
            throw new BoardException("문의글만 답글을 작성할 수 있습니다.");
        }

        if (parent.isReply()) {
            throw new BoardException("답글에는 다시 답글을 작성할 수 없습니다.");
        }

        if (parent.isHidden()) {
            throw new BoardException("숨김 처리된 글에는 답글을 작성할 수 없습니다.");
        }
    }

    private void deleteBoardRecursively(Board board) {
        List<Board> children = new ArrayList<>(board.getChildren());

        for (Board child : children) {
            deleteBoardRecursively(child);
        }

        boardAttachmentService.deletePhysicalFiles(board);

        if (board.getParent() != null) {
            board.changeParent(null);
        }

        boardRepository.delete(board);
    }
}
