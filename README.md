# 💇 HairSalon Project2

> 미용실 탐색 · 디자이너 선택 · 예약 · 리뷰 · 커뮤니티까지 한 곳에서  
> **헤어살롱 예약 & 비교 플랫폼**

<br>

## 📌 프로젝트 소개

HairSalon Project2는 사용자가 주변 미용실을 탐색하고, 원하는 디자이너를 선택하여 예약할 수 있는 웹 플랫폼입니다.  
Kakao Local Search API를 통해 실제 미용실 데이터를 연동하며, 예약·리뷰·커뮤니티 기능을 통합적으로 제공합니다.

<br>

## ⚙️ 기술 스택

| 구분 | 기술 |
|---|---|
| **Backend** | Spring Boot 3.5 · JDK 21 · Spring Security 6 · Spring Data JPA / Hibernate |
| **Frontend** | Thymeleaf · Bootstrap 5 · Vanilla JS / jQuery · Summernote 0.8.20 |
| **Database** | MySQL 8.0 · JPA Auditing |
| **빌드 도구** | Gradle (Groovy) |
| **외부 API** | Kakao Local Search API |
| **파일 저장** | 로컬 디스크 (`C:/upload/`) · UUID 기반 저장명 |
| **개발 도구** | IntelliJ IDEA · Git / GitHub · Claude Code |

<br>

## 🏗️ 아키텍처

```
Browser (Thymeleaf + Bootstrap 5)
        ↕
Controller Layer     MemberController / SalonController / BoardController ...
        ↕
Service Layer        비즈니스 로직 · *QueryService + *CommandService 패턴
        ↕
Repository Layer     Spring Data JPA · JPQL 커스텀 @Query · Specifications
        ↕
Entity / DB          MySQL 8 · BaseTimeEntity / BaseCreatedEntity 상속
```

**9개 기능 모듈:** `member` · `salon` · `designer` · `salonservice` · `reservation` · `review` · `board` · `admin` · `common`

<br>

## 🚀 핵심 기능

### 🔍 살롱 탐색
- 이름·지역 키워드 검색 (JPA Specifications 동적 쿼리)
- **Kakao Local Search API** 연동 → 실제 살롱 데이터 자동 동기화
- 지도 기반 탐색 (GPS 좌표 마커 표시)
- 좋아요 / 찜 목록 관리

### 💇 디자이너
- 프로필 · 전문 분야 (CUT / PERM / DYE / CARE) · 경력 · 소개글
- 별점 기반 랭킹 (Projection 경량 조회)
- 좋아요 토글 · 북마크 · 마이페이지 목록

### 📅 예약
- 날짜 · 시간 · 디자이너 · 서비스 선택 후 예약 확정
- **타임슬롯 중복 방지** (`reservation_slot` 테이블로 동시 예약 차단)
- 결제 방법 선택 (CARD / CASH / KAKAO_PAY)
- 상태 전이: `PENDING → CONFIRMED → COMPLETED / CANCELLED`

### ⭐ 리뷰
- 예약 완료(COMPLETED) 건에 한해 작성 가능 (1예약 1리뷰)
- 별점(1-5) · 텍스트 · 이미지 다중 첨부
- 좋아요 토글 · 디자이너 답글
- 월별 통계 API · 디자이너 / 살롱 랭킹

### 📋 게시판
- 공지사항(NOTICE) · Q&A 문의게시판 분리
- **자기참조(parent) 답글 구조** (CascadeType.ALL)
- 쿠키 기반 조회수 중복 방지 (24시간 / max 50개)
- 신고 시스템 (누적 5회 자동 숨김)
- **Summernote WYSIWYG 에디터** · 파일 / 이미지 첨부

### 🤖 챗봇
- 규칙 기반 키워드 매칭 (외부 API 없이 서버 내부 처리)
- 전 페이지 우측 하단 플로팅 UI
- 예약 · 살롱 안내 · 이용약관 · Q&A 게시판 연결

### 🔐 보안
- **Spring Security 6** Form Login · BCrypt 단방향 암호화
- 역할 3단계: USER · DESIGNER · ADMIN
- XSS(th:text 이스케이프) · CSRF 토큰 · 파일 업로드 다중 검증

### 🛠 관리자
- 통합 대시보드 (회원 · 예약 · 살롱 · 게시판 · 리뷰 전체 관리)
- `BoardDeleteLog` 삭제 감사 로그
- 관리자 권한 5중 안전 장치 (마지막 ADMIN 계정 보호 포함)

<br>

## 🗂️ 프로젝트 구조

```
src/main/java/com/hairsalonproject2/
├── member/          # 회원 · Spring Security · 공통 기반      (A담당)
├── salon/           # 살롱 · Kakao API · 지도                 (B담당)
├── designer/        # 디자이너 · 전문분야 · 랭킹               (B담당)
├── salonservice/    # 살롱 서비스 항목 · 가격 비교             (B담당)
├── reservation/     # 예약 · 타임슬롯 · 상태 관리             (C담당)
├── review/          # 리뷰 · 이미지 · 통계 · 좋아요            (C담당)
├── board/           # 게시판 · 파일 · WYSIWYG 에디터          (D담당)
├── admin/           # 관리자 통합 대시보드                     (D담당)
├── common/          # 보안설정 · 예외처리 · 파일저장 · 공통기반
└── home/            # 메인 홈 페이지
```

<br>

## 🗄️ DB 설계 요약

| 테이블 | 핵심 필드 |
|---|---|
| `member` | memberId(PK·String) · role(ENUM) · status(ENUM) |
| `salon` | salon_id · address · latitude · longitude · avg_rating |
| `designer` | designer_id · salon_id(FK) · member_id(FK·1:1) · specialty |
| `salon_service` | service_id · salon_id(FK) · price · duration |
| `reservation` | reservation_id · member_id · designer_id · service_id · status |
| `reservation_slot` | slot_id · designer_id · date · time (중복 방지) |
| `review` | review_id · reservation_id(FK) · rating · like_count |
| `board` | board_id · type(NOTICE/QNA) · parent_id (자기참조) |
| `board_report` | board_id + member_id **UNIQUE** (중복 신고 차단) |
| `board_delete_log` | logId · boardId · deletedBy · deletedAt (감사 로그) |

<br>

## 🛠️ 설치 및 실행

### 사전 준비
- JDK 21+
- MySQL 8.0+
- Gradle

### 1. DB 초기화

```sql
mysql -u root -p
source /sql/HairSalon_Project2_table.sql
```

### 2. 비밀 설정 파일 생성

`src/main/resources/application-secret.properties` 파일을 생성하고 아래 내용을 작성합니다.

```properties
# DB 연결
spring.datasource.url=jdbc:mysql://localhost:3306/salon_project_v2?characterEncoding=UTF-8&serverTimezone=Asia/Seoul
spring.datasource.username=salon_user
spring.datasource.password=1234

# Kakao Local Search API
kakao.rest-api-key=YOUR_KAKAO_REST_API_KEY

# 파일 업로드 경로
file.upload.path=C:/upload/
```

> ⚠️ `application-secret.properties`는 `.gitignore`에 반드시 포함되어야 합니다.

### 3. 빌드 및 실행

```bash
# 빌드
./gradlew clean build

# 실행
./gradlew bootRun
```

### 4. 접속

```
http://localhost:8081
```

### 기본 관리자 계정 (앱 시작 시 자동 생성)

| 항목 | 값 |
|---|---|
| 아이디 | `admin1` |
| 비밀번호 | `12341234` |

<br>

## 🔒 보안 구조

```
[인증]  Spring Security Form Login → BCrypt 단방향 암호화
[인가]  URL 레벨: SecurityConfig authorizeHttpRequests
        메서드 레벨: @PreAuthorize (isAuthenticated / hasRole)
[XSS]  Thymeleaf th:text 자동 이스케이프 / th:utext 최소화
[CSRF] Spring Security 기본 활성화 / AJAX X-CSRF-TOKEN 헤더
[파일] 확장자 화이트리스트 + 블랙리스트 + MIME 이중 검증
[SQL]  JPA 파라미터 바인딩 (문자열 직접 연결 금지)
```

<br>

## 📐 설계 패턴

| 패턴 | 적용 |
|---|---|
| **레이어드 아키텍처** | Controller → Service → Repository → Entity |
| **CQRS 서비스 분리** | `*QueryService`(읽기) + `*CommandService`(쓰기) |
| **DTO 분리** | `dto/request` 입력 / `dto/response` 출력 / Entity 직접 노출 금지 |
| **Projection** | 경량 데이터 조회 (`DesignerRatingRow`, `ServicePriceCompareRow`) |
| **Builder 패턴** | Entity 생성 시 `@Builder` 활용 |
| **소프트 딜리트** | `status = DELETED` (DB 레코드 유지) |
| **감사 로그** | `BoardDeleteLog` (관리자 삭제 이력) |

<br>

## 🧩 팀 역할 분담

| 담당 | 이름 | 담당 기능 |
|---|---|---|
| **A담당** | 이찬영 | 회원 · Spring Security · 공통 기반 · 관리자 회원 관리 |
| **B담당** | 한태성 | 살롱 · 디자이너 · 서비스 관리 · Kakao API 연동 |
| **C담당** | 박중규 | 예약 · 타임슬롯 · 리뷰 · 통계 · 랭킹 |
| **D담당** | 김석호 | 게시판 · 파일 · WYSIWYG 에디터 · 챗봇 · 관리자 UI |

<br>

## 📁 멀티 프로파일 구성

| 파일 | 역할 |
|---|---|
| `application.properties` | 공통 설정 (포트 8081 · JPA · 멀티파트 제한) |
| `application-local.properties` | 로컬 DB 연결 |
| `application-secret.properties` | 민감 정보 (Kakao API 키 · DB 비번 · 파일 경로) |

<br>

## 🔍 주요 이슈 해결

| 이슈 | 해결 방법 |
|---|---|
| `@EnableJpaAuditing` 누락 | Application 클래스 어노테이션 추가 |
| N+1 쿼리 | JPQL `JOIN FETCH` + `@Query` 직접 작성 |
| 쿠키 사이즈 초과 | ViewGuard 최대 50개 이력 + URLEncoder 처리 |
| Git 패키지명 충돌 | 패키지명 소문자 통일 (`com.hairsalonproject2`) |
| 에디터 로드 오류 | jQuery → Bootstrap → Summernote 순서 고정 + DOMContentLoaded 재시도 패턴 |

<br>

## 🔮 향후 개선 방향

- [ ] **JWT 인증 전환** — 세션 → JWT + Refresh Token (모바일 앱 대응)
- [ ] **Redis 캐시** — 살롱 랭킹·통계 캐싱 · 세션 클러스터링
- [ ] **AWS S3** — 로컬 디스크 → 클라우드 파일 저장
- [ ] **알림 기능** — 예약 확정·취소 Email / 카카오 알림톡
- [ ] **AI 챗봇 고도화** — 규칙 기반 → LLM API 연동
- [ ] **결제 연동** — 카카오페이 · 토스 PG 실제 연동

<br>

---

> Spring Boot 3.5 · JDK 21 · 팀 4인 · 9개 모듈
