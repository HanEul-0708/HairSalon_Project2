package com.hairsalonproject2.admin.entity;

import com.hairsalonproject2.common.constant.BoardType;
import com.hairsalonproject2.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * BoardDeleteLog
 * 관리자 강제 삭제 기록 저장
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "board_delete_log")
public class BoardDeleteLog extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "delete_log_id")
    private Long deleteLogId;

    @Column(name = "board_id", nullable = false)
    private Integer boardId;

    @Enumerated(EnumType.STRING)
    @Column(name = "board_type", nullable = false, length = 20)
    private BoardType boardType;

    @Column(name = "board_title", nullable = false, length = 200)
    private String boardTitle;

    @Column(name = "writer_id", nullable = false, length = 30)
    private String writerId;

    @Column(name = "deleted_by", nullable = false, length = 30)
    private String deletedBy;

    @Column(name = "delete_reason", length = 255)
    private String deleteReason;

    @Builder
    public BoardDeleteLog(Integer boardId,
                          BoardType boardType,
                          String boardTitle,
                          String writerId,
                          String deletedBy,
                          String deleteReason) {
        this.boardId = boardId;
        this.boardType = boardType;
        this.boardTitle = boardTitle;
        this.writerId = writerId;
        this.deletedBy = deletedBy;
        this.deleteReason = deleteReason;
    }
}
