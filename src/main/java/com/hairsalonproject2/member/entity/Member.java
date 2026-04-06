package com.hairsalonproject2.member.entity;

import com.hairsalonproject2.board.entity.Board;
import com.hairsalonproject2.common.constant.MemberRole;
import com.hairsalonproject2.common.constant.MemberStatus;
import com.hairsalonproject2.common.entity.BaseCreatedEntity;
import com.hairsalonproject2.designer.entity.Designer;
import com.hairsalonproject2.reservation.entity.Reservation;
import com.hairsalonproject2.review.entity.Review;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Member
 *
 * member 테이블과 매핑되는 엔티티
 *
 * 특징
 * - PK가 숫자가 아니라 member_id(String)
 * - USER / DESIGNER / ADMIN 역할 구분
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "member")
public class Member extends BaseCreatedEntity {

    /**
     * 회원 아이디 (PK)
     */
    @Id
    @Column(name = "member_id", length = 30)
    private String memberId;

    /**
     * 비밀번호 (암호화 저장 예정)
     */
    @Column(name = "password", nullable = false, length = 255)
    private String password;

    /**
     * 회원 이름
     */
    @Column(name = "name", nullable = false, length = 50)
    private String name;

    /**
     * 전화번호
     */
    @Column(name = "phone", length = 20)
    private String phone;

    /**
     * 이메일 (UNIQUE)
     */
    @Column(name = "email", unique = true, length = 100)
    private String email;

    /**
     * 회원 권한 (ENUM)
     *
     * EnumType.STRING 반드시 사용
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    private MemberRole role;

    /**
     * 회원 상태
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MemberStatus status;

    // ==============================
    // 연관관계
    // ==============================

    /**
     * 디자이너와 1:1 관계
     *
     * designer 테이블의 member_id 가 FK를 가지고 있으므로
     * mappedBy = "member" 로 읽기 전용 관계를 맺는다.
     *
     * 중요:
     * Designer 엔티티에도 반드시
     * private Member member;
     * 가 있어야 정상 동작한다.
     */
    @OneToOne(mappedBy = "member", fetch = FetchType.LAZY)
    private Designer designer;

    /**
     * 회원이 한 예약 목록
     */
    @OneToMany(mappedBy = "member")
    private List<Reservation> reservations = new ArrayList<>();

    /**
     * 회원이 작성한 리뷰 목록
     */
    @OneToMany(mappedBy = "member")
    private List<Review> reviews = new ArrayList<>();

    /**
     * 회원이 작성한 게시글 목록
     */
    @OneToMany(mappedBy = "member")
    private List<Board> boards = new ArrayList<>();

    // ==============================
    // 생성자 (Builder)
    // ==============================

    @Builder
    public Member(String memberId, String password, String name,
                  String phone, String email, MemberRole role, MemberStatus status) {
        this.memberId = memberId;
        this.password = password;
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.role = role;
        this.status = status;
    }

    // ==============================
    // 비즈니스 메서드
    // ==============================

    /**
     * 비밀번호 변경
     */
    public void changePassword(String password) {
        this.password = password;
    }

    /**
     * 회원 정보 수정
     */
    public void updateProfile(String name, String phone, String email) {
        this.name = name;
        this.phone = phone;
        this.email = email;
    }

    /**
     * 권한 변경
     */
    public void changeRole(MemberRole role) {
        this.role = role;
    }

    /**
     * 회원 상태 변경
     */
    public void changeStatus(MemberStatus status) {
        this.status = status;
    }
}