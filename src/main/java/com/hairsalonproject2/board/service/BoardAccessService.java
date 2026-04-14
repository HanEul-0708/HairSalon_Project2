package com.hairsalonproject2.board.service;

import com.hairsalonproject2.board.dto.response.BoardDetailResponse;
import com.hairsalonproject2.board.dto.response.BoardResponse;
import com.hairsalonproject2.common.constant.BoardType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoardAccessService {

    private final BoardQueryService boardQueryService;
    private final BoardAdminService boardAdminService;

    public Page<BoardResponse> getQnaPageForViewer(int page,
                                                   int size,
                                                   String keyword,
                                                   String viewerMemberId,
                                                   boolean canViewAll) {
        return boardQueryService.getQnaPage(page, size, keyword)
                .map(board -> maskQnaListBoard(board, viewerMemberId, canViewAll));
    }

    public BoardDetailResponse getAccessibleQnaDetail(Integer boardId,
                                                      String viewerMemberId,
                                                      boolean canViewAll) {
        return requireQnaAccess(
                boardQueryService.getPublicDetail(boardId, BoardType.QNA),
                viewerMemberId,
                canViewAll
        );
    }

    public boolean canEditBoard(Integer boardId, String viewerMemberId, boolean isAdmin) {
        BoardType boardType = boardQueryService.getBoardType(boardId);
        if (boardType == BoardType.NOTICE && isAdmin) {
            return true;
        }

        return viewerMemberId != null && boardQueryService.isMyBoard(boardId, viewerMemberId);
    }

    @Transactional
    public BoardDetailResponse getAccessibleQnaDetailAndIncreaseView(Integer boardId,
                                                                     String viewerMemberId,
                                                                     boolean canViewAll) {
        getAccessibleQnaDetail(boardId, viewerMemberId, canViewAll);
        return requireQnaAccess(
                boardQueryService.getPublicDetailAndIncreaseView(boardId, BoardType.QNA),
                viewerMemberId,
                canViewAll
        );
    }

    @Transactional
    public boolean reportAccessibleQna(Integer boardId, String reporterId, boolean canViewAll) {
        getAccessibleQnaDetail(boardId, reporterId, canViewAll);
        return boardAdminService.reportBoard(boardId, reporterId);
    }

    private BoardResponse maskQnaListBoard(BoardResponse board, String viewerMemberId, boolean canViewAll) {
        if (canViewQna(board.getMemberId(), viewerMemberId, canViewAll)) {
            return board;
        }
        board.maskAsSecret();
        return board;
    }

    private BoardDetailResponse requireQnaAccess(BoardDetailResponse board, String viewerMemberId, boolean canViewAll) {
        if (canViewQna(board.getMemberId(), viewerMemberId, canViewAll)) {
            return board;
        }
        throw new AccessDeniedException("문의글을 조회할 권한이 없습니다.");
    }

    private boolean canViewQna(String writerMemberId, String viewerMemberId, boolean canViewAll) {
        return canViewAll || (viewerMemberId != null && viewerMemberId.equals(writerMemberId));
    }
}
