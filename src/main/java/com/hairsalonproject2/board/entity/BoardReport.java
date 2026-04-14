package com.hairsalonproject2.board.entity;

import com.hairsalonproject2.common.entity.BaseCreatedEntity;
import com.hairsalonproject2.member.entity.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * BoardReport
 * 게시글 신고 이력 저장 엔티티
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "board_report",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_board_report",
                        columnNames = {"board_id", "member_id"}
                )
        }
)
public class BoardReport extends BaseCreatedEntity {

    /**
     * 신고 기록 PK
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_id")
    private Long reportId;

    /**
     * 신고된 게시글
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "board_id", nullable = false)
    private Board board;

    /**
     * 신고한 회원
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Builder
    public BoardReport(Board board, Member member) {
        this.board = board;
        this.member = member;
    }
}