-- =========================================================
-- [1] MySQL 사용자(계정) 생성 및 권한 부여
-- =========================================================
-- 주의:
-- 1. 이 부분은 보통 root 계정으로 접속해서 실행한다.
-- 2. 이미 계정이 있으면 CREATE USER에서 오류가 날 수 있으므로
--    IF NOT EXISTS를 사용한다.
-- 3. 프로젝트용 DB 이름은 salon_project_v2 로 설정한다.
-- =========================================================

-- 1) 프로젝트 전용 사용자 계정 생성
CREATE USER IF NOT EXISTS 'salon_user'@'localhost' IDENTIFIED BY '1234';

-- 2) 프로젝트 DB 전체 권한 부여
GRANT ALL PRIVILEGES ON salon_project_v2.* TO 'salon_user'@'localhost';

-- 3) 권한 즉시 반영
FLUSH PRIVILEGES;

-- 4) 권한 확인 (선택)
SHOW GRANTS FOR 'salon_user'@'localhost';


-- =========================================================
-- [2] DB 생성
-- =========================================================
-- 주의:
-- 1. 개발 환경에서 DB를 새로 맞출 때 사용하는 스크립트다.
-- 2. 기존 데이터를 유지해야 하면 DROP DATABASE는 실행하면 안 된다.
-- 3. 문자셋은 utf8mb4 로 지정해서 한글/이모지까지 대응한다.
-- =========================================================
DROP DATABASE IF EXISTS salon_project_v2;

CREATE DATABASE salon_project_v2
DEFAULT CHARACTER SET utf8mb4
DEFAULT COLLATE utf8mb4_general_ci;

USE salon_project_v2;


-- =========================================================
-- [3] MEMBER (회원)
-- =========================================================
-- 역할:
-- - 로그인 / 회원가입 / 권한 관리용 기본 테이블
-- - member_id 를 로그인 ID 및 PK 로 사용
-- =========================================================
CREATE TABLE member (
    member_id VARCHAR(30) PRIMARY KEY,              -- 회원 아이디 (로그인 ID)
    password VARCHAR(255) NOT NULL,                -- 암호화된 비밀번호
    name VARCHAR(50) NOT NULL,                     -- 회원 이름
    phone VARCHAR(20),                             -- 전화번호
    email VARCHAR(100) UNIQUE,                     -- 이메일 (중복 방지)

    role ENUM('USER','DESIGNER','ADMIN') DEFAULT 'USER', -- 권한
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',        -- 상태값 (예: ACTIVE)

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP       -- 가입일시
);


-- =========================================================
-- [4] SALON (미용실)
-- =========================================================
-- 역할:
-- - 미용실 기본 정보 저장
-- - 외부 API 데이터와 내부 데이터를 함께 관리할 수 있도록 설계
-- - average_rating / review_count / like_count 는 조회 성능을 위한 캐시 컬럼
-- =========================================================
CREATE TABLE salon (
    salon_id INT AUTO_INCREMENT PRIMARY KEY,       -- 미용실 PK

    external_id VARCHAR(100) UNIQUE,               -- 외부 API 연동용 식별자
    source_type VARCHAR(30),                       -- 외부 API 출처 (KAKAO / NAVER 등)

    name VARCHAR(100) NOT NULL,                    -- 미용실 이름
    address VARCHAR(255) NOT NULL,                 -- 기본 주소
    road_address VARCHAR(255),                     -- 도로명 주소
    phone VARCHAR(20),                             -- 전화번호

    description TEXT,                              -- 소개글

    latitude DECIMAL(10,7),                        -- 위도
    longitude DECIMAL(10,7),                       -- 경도

    image_url VARCHAR(255),                        -- 대표 이미지 경로
    place_url VARCHAR(255),                        -- 외부 원본 페이지 URL

    reservable TINYINT(1) DEFAULT 1,               -- 예약 가능 여부 (1: 가능, 0: 불가)

    average_rating DECIMAL(3,2) NOT NULL DEFAULT 0.00, -- 평균 평점 캐시
    review_count INT NOT NULL DEFAULT 0,                -- 리뷰 수 캐시
    like_count INT NOT NULL DEFAULT 0,                  -- 좋아요 수 캐시

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    -- 데이터 무결성 체크
    CHECK (average_rating BETWEEN 0 AND 5),
    CHECK (review_count >= 0),
    CHECK (like_count >= 0)
);


-- =========================================================
-- [5] DESIGNER (디자이너)
-- =========================================================
-- 역할:
-- - 미용실 소속 디자이너 정보 저장
-- - 필요 시 member 계정과 1:1 연결 가능
-- 주의:
-- - member_id 는 UNIQUE 이므로 한 회원 계정이 한 디자이너에만 연결됨
-- =========================================================
CREATE TABLE designer (
    designer_id INT AUTO_INCREMENT PRIMARY KEY,    -- 디자이너 PK

    salon_id INT NOT NULL,                         -- 소속 미용실 FK
    member_id VARCHAR(30) UNIQUE,                  -- 연결된 회원 계정 ID (선택)

    name VARCHAR(50) NOT NULL,                     -- 디자이너 이름
    profile_image VARCHAR(255),                    -- 프로필 이미지 경로
    introduction TEXT,                             -- 소개글

    career_years INT NOT NULL DEFAULT 0,           -- 경력 연차

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CHECK (career_years >= 0),

    FOREIGN KEY (salon_id) REFERENCES salon(salon_id) ON DELETE CASCADE,
    FOREIGN KEY (member_id) REFERENCES member(member_id) ON DELETE SET NULL
);


-- =========================================================
-- [6] SALON_SERVICE (시술)
-- =========================================================
-- 역할:
-- - 각 미용실에서 제공하는 시술 정보 저장
-- - 가격 / 소요 시간 검증 포함
-- =========================================================
CREATE TABLE salon_service (
    service_id INT AUTO_INCREMENT PRIMARY KEY,     -- 시술 PK

    salon_id INT NOT NULL,                         -- 소속 미용실 FK
    name VARCHAR(100) NOT NULL,                    -- 시술명
    price INT NOT NULL,                            -- 가격
    duration INT NOT NULL,                         -- 소요 시간(분)

    description TEXT,                              -- 시술 설명

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CHECK (price >= 0),
    CHECK (duration > 0),

    FOREIGN KEY (salon_id) REFERENCES salon(salon_id) ON DELETE CASCADE
);


-- =========================================================
-- [7] RESERVATION (예약)
-- =========================================================
-- 역할:
-- - 회원이 디자이너와 시술을 선택해서 예약한 내역 저장
-- 주의:
-- - 실제 중복 시간 방지는 reservation_slot 테이블에서 담당
-- - total_price 는 음수 불가
-- =========================================================
CREATE TABLE reservation (
    reservation_id INT AUTO_INCREMENT PRIMARY KEY, -- 예약 PK

    member_id VARCHAR(30) NOT NULL,                -- 예약한 회원
    designer_id INT NOT NULL,                      -- 예약 대상 디자이너
    service_id INT NOT NULL,                       -- 예약한 시술

    reservation_date DATE NOT NULL,                -- 예약 날짜
    reservation_time TIME NOT NULL,                -- 예약 시작 시간

    status ENUM('RESERVED','CANCELLED','COMPLETED')
        NOT NULL DEFAULT 'RESERVED',               -- 예약 상태

    total_price INT NOT NULL,                      -- 총 결제 금액

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CHECK (total_price >= 0),

    FOREIGN KEY (member_id) REFERENCES member(member_id) ON DELETE CASCADE,
    FOREIGN KEY (designer_id) REFERENCES designer(designer_id) ON DELETE CASCADE,
    FOREIGN KEY (service_id) REFERENCES salon_service(service_id) ON DELETE CASCADE
);


-- =========================================================
-- [8] RESERVATION_SLOT (예약 슬롯)
-- =========================================================
-- 역할:
-- - 디자이너의 특정 날짜/시간 점유 상태를 관리
-- - 같은 시간 중복 예약을 막는 핵심 테이블
-- 중요:
-- - 예약 취소 시 reservation status 만 바꾸지 말고
--   연결된 reservation_slot 도 함께 삭제하거나 정리해야 한다.
-- =========================================================
CREATE TABLE reservation_slot (
    slot_id INT AUTO_INCREMENT PRIMARY KEY,        -- 슬롯 PK

    reservation_id INT NOT NULL,                   -- 연결된 예약 ID
    designer_id INT NOT NULL,                      -- 디자이너 FK

    reservation_date DATE NOT NULL,                -- 예약 날짜
    slot_time TIME NOT NULL,                       -- 점유 시간

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    UNIQUE (designer_id, reservation_date, slot_time), -- 중복 예약 방지

    FOREIGN KEY (reservation_id) REFERENCES reservation(reservation_id) ON DELETE CASCADE,
    FOREIGN KEY (designer_id) REFERENCES designer(designer_id) ON DELETE CASCADE
);


-- =========================================================
-- [9] REVIEW (리뷰)
-- =========================================================
-- 역할:
-- - 예약 완료 후 남기는 리뷰 저장
-- - reservation_id UNIQUE 로 예약 1건당 리뷰 1개만 허용
-- - reply_content / reply_created_at 으로 디자이너 답글 지원
-- =========================================================
CREATE TABLE review (
    review_id INT AUTO_INCREMENT PRIMARY KEY,      -- 리뷰 PK

    reservation_id INT NOT NULL UNIQUE,            -- 예약 1건당 리뷰 1개
    member_id VARCHAR(30) NOT NULL,                -- 리뷰 작성 회원
    designer_id INT NOT NULL,                      -- 리뷰 대상 디자이너

    rating TINYINT NOT NULL,                       -- 평점
    content TEXT,                                  -- 리뷰 내용

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    reply_content TEXT,                            -- 디자이너 답글 내용
    reply_created_at TIMESTAMP,                    -- 답글 작성 시간

    CHECK (rating BETWEEN 1 AND 5),

    FOREIGN KEY (reservation_id) REFERENCES reservation(reservation_id) ON DELETE CASCADE,
    FOREIGN KEY (member_id) REFERENCES member(member_id) ON DELETE CASCADE,
    FOREIGN KEY (designer_id) REFERENCES designer(designer_id) ON DELETE CASCADE
);


-- =========================================================
-- [10] REVIEW_IMAGE (리뷰 이미지)
-- =========================================================
-- 역할:
-- - 리뷰 1개에 여러 이미지 첨부 가능
-- - sort_order 로 출력 순서 제어
-- =========================================================
CREATE TABLE review_image (
    image_id INT AUTO_INCREMENT PRIMARY KEY,       -- 리뷰 이미지 PK

    review_id INT NOT NULL,                        -- 소속 리뷰 FK
    image_url VARCHAR(255) NOT NULL,               -- 이미지 경로
    sort_order INT DEFAULT 0,                      -- 출력 순서

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (review_id) REFERENCES review(review_id) ON DELETE CASCADE
);


-- =========================================================
-- [11] BOARD (게시판)
-- =========================================================
-- 역할:
-- - 공지사항 / 문의게시판 통합 저장
-- - parent_id 로 답글 구조 구현
-- - hidden / hidden_reason / report_count 로 신고/숨김 관리 지원
-- =========================================================
CREATE TABLE board (
    board_id INT AUTO_INCREMENT PRIMARY KEY,       -- 게시글 PK

    member_id VARCHAR(30) NOT NULL,                -- 작성자 회원 ID
    type ENUM('NOTICE','QNA') NOT NULL DEFAULT 'NOTICE', -- 게시판 종류

    title VARCHAR(200) NOT NULL,                   -- 제목
    content TEXT,                                  -- 내용

    view_count INT NOT NULL DEFAULT 0,             -- 조회수

    parent_id INT,                                 -- 부모 글 ID (답글일 경우)

    hidden TINYINT(1) NOT NULL DEFAULT 0,          -- 숨김 여부
    hidden_reason VARCHAR(255),                    -- 숨김 사유

    report_count INT NOT NULL DEFAULT 0,           -- 신고 누적 수

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CHECK (view_count >= 0),
    CHECK (report_count >= 0),

    FOREIGN KEY (member_id) REFERENCES member(member_id) ON DELETE CASCADE,
    FOREIGN KEY (parent_id) REFERENCES board(board_id) ON DELETE CASCADE
);


-- =========================================================
-- [12] BOARD_IMAGE (게시글 이미지)
-- =========================================================
-- 역할:
-- - 게시글 첨부 이미지 저장
-- - 게시글 삭제 시 함께 삭제
-- =========================================================
CREATE TABLE board_image (
    image_id BIGINT AUTO_INCREMENT PRIMARY KEY,    -- 게시글 이미지 PK
    board_id INT NOT NULL,                         -- 연결된 게시글 ID

    original_name VARCHAR(255) NOT NULL,           -- 원본 파일명
    saved_name VARCHAR(255) NOT NULL,              -- 서버 저장 파일명
    file_path VARCHAR(255) NOT NULL,               -- 서버 저장 경로
    image_url VARCHAR(255) NOT NULL,               -- 브라우저 접근 URL

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (board_id) REFERENCES board(board_id) ON DELETE CASCADE
);


-- =========================================================
-- [13] BOARD_FILE (게시글 첨부파일)
-- =========================================================
-- 역할:
-- - 게시글 첨부 파일 저장
-- =========================================================
CREATE TABLE board_file (
    file_id BIGINT AUTO_INCREMENT PRIMARY KEY,     -- 첨부파일 PK
    board_id INT NOT NULL,                         -- 연결된 게시글 ID

    original_name VARCHAR(255) NOT NULL,           -- 원본 파일명
    saved_name VARCHAR(255) NOT NULL,              -- 서버 저장 파일명
    file_path VARCHAR(255) NOT NULL,               -- 서버 저장 경로
    file_size BIGINT NOT NULL,                     -- 파일 크기
    file_extension VARCHAR(20),                    -- 확장자

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (board_id) REFERENCES board(board_id) ON DELETE CASCADE
);


-- =========================================================
-- [14] SALON_LIKE (미용실 찜 / 좋아요)
-- =========================================================
-- 역할:
-- - 회원이 미용실에 좋아요(찜) 누른 정보 저장
-- - 같은 회원이 같은 미용실에 중복 좋아요 불가
-- =========================================================
CREATE TABLE salon_like (
    like_id INT AUTO_INCREMENT PRIMARY KEY,        -- 좋아요 PK

    member_id VARCHAR(30) NOT NULL,                -- 회원 ID
    salon_id INT NOT NULL,                         -- 미용실 ID

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    UNIQUE (member_id, salon_id),                  -- 중복 좋아요 방지

    FOREIGN KEY (member_id) REFERENCES member(member_id) ON DELETE CASCADE,
    FOREIGN KEY (salon_id) REFERENCES salon(salon_id) ON DELETE CASCADE
);


-- =========================================================
-- [15] BOARD_DELETE_LOG (게시글 삭제 로그)
-- =========================================================
-- 역할:
-- - 관리자가 게시글을 삭제한 기록 저장
-- - 게시글 원본은 삭제되더라도 로그는 남도록 별도 보관
-- 수정 포인트:
-- - created_at / updated_at 에 DEFAULT CURRENT_TIMESTAMP(6) 추가
--   → insert 시 시간값 누락 오류 방지
-- =========================================================
CREATE TABLE board_delete_log (
    delete_log_id BIGINT AUTO_INCREMENT PRIMARY KEY, -- 삭제 로그 PK

    board_id INT NOT NULL,                           -- 삭제된 게시글 ID
    board_type VARCHAR(20) NOT NULL,                -- 게시글 종류
    board_title VARCHAR(200) NOT NULL,              -- 게시글 제목

    writer_id VARCHAR(30) NOT NULL,                 -- 원래 작성자 ID
    deleted_by VARCHAR(30) NOT NULL,                -- 삭제한 관리자 ID

    delete_reason VARCHAR(255),                     -- 삭제 사유

    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6)
);


-- =========================================================
-- [16] BOARD_REPORT (게시글 신고)
-- =========================================================
-- 역할:
-- - 게시글 신고 기록 저장
-- - 같은 회원이 같은 게시글을 중복 신고하지 못하게 제한
-- =========================================================
CREATE TABLE board_report (
    report_id BIGINT AUTO_INCREMENT PRIMARY KEY,    -- 신고 PK
    board_id INT NOT NULL,                          -- 신고된 게시글
    member_id VARCHAR(30) NOT NULL,                 -- 신고한 회원

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    UNIQUE (board_id, member_id),                   -- 중복 신고 방지

    FOREIGN KEY (board_id) REFERENCES board(board_id) ON DELETE CASCADE,
    FOREIGN KEY (member_id) REFERENCES member(member_id) ON DELETE CASCADE
);


-- =========================================================
-- [17] 인덱스 추가
-- =========================================================
-- 역할:
-- - 자주 사용하는 조회 패턴에 맞춰 성능 보완
-- =========================================================

-- 예약: 회원별 날짜 조회 최적화
CREATE INDEX idx_reservation_member_date
    ON reservation(member_id, reservation_date);

-- 미용실 좋아요: 회원 기준 조회 최적화
CREATE INDEX idx_salon_like_member
    ON salon_like(member_id);

-- 게시글 이미지/파일: board_id 기준 조회 최적화
CREATE INDEX idx_board_image_board_id
    ON board_image(board_id);

CREATE INDEX idx_board_file_board_id
    ON board_file(board_id);

-- 미용실 검색: 이름 + 주소 검색 최적화
CREATE INDEX idx_salon_name_address
    ON salon(name, address);
    
-- 게시판 목록 조회 최적화 (공지/문의 분리 + 원글 필터 + 숨김 제외 + 최신순 정렬)
CREATE INDEX idx_board_type_parent_hidden_created
    ON board(type, parent_id, hidden, created_at);
