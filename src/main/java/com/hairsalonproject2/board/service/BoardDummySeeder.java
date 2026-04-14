package com.hairsalonproject2.board.service;

import com.hairsalonproject2.board.dto.request.BoardCreateRequest;
import com.hairsalonproject2.board.dto.request.BoardReplyRequest;
import com.hairsalonproject2.board.entity.Board;
import com.hairsalonproject2.board.repository.BoardRepository;
import com.hairsalonproject2.common.constant.BoardType;
import com.hairsalonproject2.common.constant.MemberRole;
import com.hairsalonproject2.common.constant.MemberStatus;
import com.hairsalonproject2.designer.entity.Designer;
import com.hairsalonproject2.designer.repository.DesignerRepository;
import com.hairsalonproject2.member.entity.Member;
import com.hairsalonproject2.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BoardDummySeeder {

    private static final int NOTICE_SEED_COUNT = 8;
    private static final int QNA_SEED_COUNT = 10;

    private final BoardService boardService;
    private final BoardRepository boardRepository;
    private final MemberRepository memberRepository;
    private final DesignerRepository designerRepository;

    @Transactional
    public int seedNoticeBoards() {
        Member adminWriter = memberRepository.findByRoleAndStatusOrderByCreatedAtAsc(MemberRole.ADMIN, MemberStatus.ACTIVE)
                .stream()
                .findFirst()
                .orElse(null);
        if (adminWriter == null) {
            return 0;
        }

        int baseIndex = (int) boardRepository.countByTypeAndParentIsNull(BoardType.NOTICE);
        int created = 0;
        for (int i = 0; i < NOTICE_SEED_COUNT; i++) {
            BoardCreateRequest request = new BoardCreateRequest();
            request.setTitle(buildNoticeTitle(baseIndex + i + 1));
            request.setContent(buildNoticeContent(baseIndex + i + 1));
            boardService.create(request, adminWriter.getMemberId(), List.of(), BoardType.NOTICE);
            created++;
        }
        return created;
    }

    @Transactional
    public int seedQnaBoards() {
        List<Member> questionWriters = memberRepository.findByRoleAndStatusOrderByCreatedAtAsc(MemberRole.USER, MemberStatus.ACTIVE);
        if (questionWriters.isEmpty()) {
            return 0;
        }

        int baseIndex = (int) boardRepository.countByTypeAndParentIsNull(BoardType.QNA);
        int created = 0;
        for (int i = 0; i < QNA_SEED_COUNT; i++) {
            Member writer = questionWriters.get(i % questionWriters.size());
            BoardCreateRequest request = new BoardCreateRequest();
            request.setTitle(buildQnaTitle(baseIndex + i + 1));
            request.setContent(buildQnaContent(baseIndex + i + 1));
            boardService.create(request, writer.getMemberId(), List.of(), BoardType.QNA);
            created++;
        }
        return created;
    }

    @Transactional
    public int seedQnaReplies() {
        List<Designer> designers = designerRepository.findAll().stream()
                .filter(designer -> designer.getMember() != null)
                .filter(designer -> designer.getMember().getStatus() == MemberStatus.ACTIVE)
                .filter(designer -> designer.getMember().getRole() == MemberRole.DESIGNER)
                .sorted(Comparator.comparing(Designer::getDesignerId))
                .toList();
        if (designers.isEmpty()) {
            return 0;
        }

        List<Board> unansweredQnaBoards = boardRepository.findByTypeAndParentIsNullOrderByBoardIdDesc(BoardType.QNA).stream()
                .filter(board -> !board.isHidden())
                .filter(board -> boardRepository.countByParent_BoardId(board.getBoardId()) == 0)
                .sorted(Comparator.comparing(Board::getBoardId))
                .toList();
        if (unansweredQnaBoards.isEmpty()) {
            return 0;
        }

        int created = 0;
        for (int i = 0; i < unansweredQnaBoards.size(); i++) {
            Board question = unansweredQnaBoards.get(i);
            Designer designer = designers.get(i % designers.size());

            BoardReplyRequest request = new BoardReplyRequest();
            request.setParentId(question.getBoardId().longValue());
            request.setTitle("문의하신 내용에 답변드립니다.");
            request.setContent(buildQnaReplyContent(question, designer, i));
            boardService.createReply(request, designer.getMember().getMemberId());
            created++;
        }
        return created;
    }

    private String buildNoticeTitle(int index) {
        return switch (index % 5) {
            case 0 -> "[안내] 예약 시스템 점검 일정 공지";
            case 1 -> "[이벤트] 신규 회원 첫 예약 할인 안내";
            case 2 -> "[공지] 리뷰 정책 및 운영 기준 안내";
            case 3 -> "[업데이트] 지역 미용실 정보 갱신 안내";
            default -> "[안내] 서비스 이용 관련 자주 묻는 사항";
        } + " #" + index;
    }

    private String buildNoticeContent(int index) {
        return switch (index % 4) {
            case 0 -> "보다 안정적인 예약 서비스를 위해 시스템 점검이 진행될 예정입니다. 점검 시간에는 일부 기능 이용이 제한될 수 있습니다.";
            case 1 -> "첫 예약 고객을 위한 할인 혜택이 적용됩니다. 이벤트 대상과 유의사항은 상세 내용을 확인해 주세요.";
            case 2 -> "건전한 커뮤니티 운영을 위해 리뷰 작성 기준과 노출 정책을 정리했습니다. 이용 전에 참고 부탁드립니다.";
            default -> "서비스 이용 중 자주 문의하시는 내용을 기준으로 운영 안내를 정리했습니다. 변경 사항은 본 공지를 통해 계속 업데이트됩니다.";
        };
    }

    private String buildQnaTitle(int index) {
        return switch (index % 6) {
            case 0 -> "예약 변경 가능한 시간 문의";
            case 1 -> "시술 후 관리 방법 문의";
            case 2 -> "디자이너 지정 예약 관련 문의";
            case 3 -> "염색 시술 예상 소요시간 문의";
            case 4 -> "주차 가능 여부 문의";
            default -> "첫 방문 전 상담 가능 여부 문의";
        } + " #" + index;
    }

    private String buildQnaContent(int index) {
        return switch (index % 5) {
            case 0 -> "예약 당일에도 시간 변경이 가능한지 궁금합니다. 변경 가능한 마감 시간도 함께 알고 싶습니다.";
            case 1 -> "시술 후 집에서 어떻게 관리하면 유지가 오래 가는지 안내받고 싶습니다.";
            case 2 -> "마음에 드는 디자이너가 있는데 다음 예약도 같은 분으로 지정 가능한지 궁금합니다.";
            case 3 -> "염색과 커트를 같이 받으면 총 소요 시간이 어느 정도인지 미리 알고 싶습니다.";
            default -> "첫 방문인데 시술 전에 간단한 스타일 상담을 별도로 받을 수 있는지 문의드립니다.";
        };
    }

    private String buildQnaReplyContent(Board question, Designer designer, int index) {
        String designerName = designer.getName();
        return switch (index % 4) {
            case 0 -> designerName + " 디자이너입니다. 문의 주신 내용은 매장 상황에 따라 조금 다를 수 있지만 방문 전 미리 연락 주시면 가능한 범위에서 일정 조정을 도와드리겠습니다.";
            case 1 -> designerName + " 디자이너입니다. 시술 후 관리 방법은 모발 상태에 따라 달라져 방문 시 자세하게 안내드리겠습니다.";
            case 2 -> designerName + " 디자이너입니다. 디자이너 지정 예약은 가능한 경우가 많으며 원하시는 일정으로 예약해 주시면 확인 후 자세히 안내드리겠습니다.";
            default -> designerName + " 디자이너입니다. \"" + question.getTitle() + "\" 문의는 매장에서 충분히 안내드릴 수 있으니 예약 메모나 전화로도 편하게 남겨 주세요.";
        };
    }
}
