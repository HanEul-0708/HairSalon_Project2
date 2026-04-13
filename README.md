# 미용실 웹 애플리케이션2  
Spring Boot + JPA + Thymeleaf 기반 미용실 예약 플랫폼  

[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/projects/jdk/17/)  
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.0-green)](https://spring.io/projects/spring-boot)  
[![MySQL](https://img.shields.io/badge/MySQL-8.0-blue)](https://www.mysql.com/)  
[![License](https://img.shields.io/badge/License-MIT-lightgrey)](LICENSE)  

---  

## 📦 바로 실행하기 (Quick Start)

| # | 단계                | 명령어                                              | 비고                                                         |
|---|-------------------|--------------------------------------------------|------------------------------------------------------------|
| 1 | JDK 21 설치         | `java -version` (17 이상)                          | [TODO: JDK 설치 가이드 URL]                                     |
| 2 | MySQL 8+ 로컬 DB 생성 | ```sql CREATE DATABASE salon;```                 | `application.yml` 의 `spring.datasource.url` 에 맞게 DB명/계정 수정 |
| 3 | 환경 변수 파일 작성       | `cp .env.example .env` <br> `vi .env` (필요한 값 입력) | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` 등     |
| 4 | 프로젝트 빌드           | `./mvnw clean package -DskipTests`               | Maven Wrapper 포함                                           |
| 5 | 애플리케이션 실행         | `java -jar target/salon-web-0.0.1-SNAPSHOT.jar`  | 기본 포트 8081                                                 |
| 6 | 브라우저 접속           | `http://localhost:8081`                          | 로그인/회원가입 화면 확인                                             |

> **Tip**: `./mvnw spring-boot:run` 로도 바로 실행 가능 (개발 모드).

---  

## 📖 프로젝트 소개  

| 구분 | 내용 |
|------|------|
| 기간 / 인원 | 2026.03.26 ~ 2026.04.15 / 팀 4명 |
| 담당 역할 | 
**팀장 (이찬영)** – 회원·시큐리티·공통 기반 설계·구현·리팩터링·코드 통합 
**팀원 B (한태성)** – 살롱·디자이너 도메인 및 검색 기능 구현, 외부 API 연동 및 위치 기반 추천/필터 검색 기능 개발
**팀원 C (박중규)** – 예약·리뷰 도메인 구현, 예약 로직 및 리뷰/평점 집계·추천 데이터 처리 기능 개발
**팀원 D (김석호)** – 게시판·파일 업로드·에디터 기능 구현 및 관리자 UI 구성, 챗봇 기능 개발 |
| 핵심 목표 | - Spring Security 로 인증·인가 구현 <br> - 외부 API 연동으로 실제 미용실 데이터 제공 <br> - QueryDSL + Pageable 로 동적 검색·페이징 구현 |

---  

## ✨ 주요 기능  

| 기능 | 설명 |
|------|------|
| 인증·인가 | Spring Security + JWT 기반 로그인·로그아웃·세션 관리, Role(ADMIN, USER) 별 접근 제어 |
| 회원 관리 | 회원 가입, 마이페이지, 비밀번호 변경, 관리자용 회원 조회·삭제·권한 변경 |
| 외부 API 연동 | 실제 미용실 데이터 제공 API 호출 → 검색·리스트 화면에 표시 |
| 동적 검색·페이징 | QueryDSL + `Pageable` 으로 복합 조건 검색 및 무한 스크롤 구현 |
| UI | Thymeleaf + Bootstrap 로 반응형 화면, 로그인 상태에 따른 메뉴 제어 |
| 관리자 페이지 | 회원·콘텐츠(예약, 서비스) 관리 UI 제공 |

---  

## 🛠️ 기술 스택 & 선택 이유  

| 기술 | 사용 이유 |
|------|-----------|
| **Java 21** | LTS 버전, 최신 언어 기능(Records, Pattern Matching) 활용 가능 |
| **Spring Boot 3.2** | 자동 설정, 스타터 기반 의존성 관리, 최신 Spring Security/ JPA 지원 |
| **Spring Data JPA** | 선언형 DB 접근, 엔티티 매핑 간소화 |
| **Spring Security** | 검증된 인증·인가 프레임워크, Role 기반 접근 제어 구현 용이 |
| **QueryDSL** | 타입 안전한 동적 쿼리 작성, 복잡 검색 로직 가독성 향상 |
| **Thymeleaf** | 서버 사이드 템플릿, Spring MVC와 자연스러운 통합 |
| **Bootstrap 5** | 빠른 UI 구현, 반응형 레이아웃 제공 |
| **MySQL** | 관계형 데이터 모델에 최적, 팀 내 기존 인프라와 일치 |
| **Git / GitHub** | 협업 히스토리 관리, PR 기반 코드 리뷰 프로세스 |
| **IntelliJ IDEA** | Spring Boot 플러그인 지원, 디버깅 편의성 |

---  

## 🏗️ 아키텍처 및 요청 흐름  

```
[Client] --(HTML/JS)--> [Spring MVC Controller] --(Service)--> [Domain Service] 
   |                                            |
   |                                            v
   |                                      [Repository (JPA/QueryDSL)]
   |                                            |
   v                                            v
[Spring Security Filter]                [MySQL]
   |
   v
[AuthenticationManager] --> JWT Token (Cookie/Header)
```

- **공통 인증 필터**: 모든 요청에 JWT 검증 후 `SecurityContext` 설정.  
- **예외 처리**: `@ControllerAdvice` 로 전역 예외 변환, API/HTML 응답 일관성 유지.  

---  

## 📁 프로젝트 구조  

```
src
├─ main
│  ├─ java/com/example/salon
│  │  ├─ config          # Spring Security, WebMvc, QueryDSL 설정
│  │  ├─ controller      # MVC / RestController
│  │  ├─ dto             # Request/Response DTO
│  │  ├─ entity          # JPA Entity (≈16개)
│  │  ├─ repository      # Spring Data JPA + QueryDSL
│  │  ├─ service         # 비즈니스 로직
│  │  └─ util            # 공통 유틸 (JWT, PasswordEncoder 등)
│  └─ resources
│     ├─ static          # CSS/JS/이미지
│     ├─ templates       # Thymeleaf HTML
│     ├─ application.yml
│     └─ db               # 초기 DDL / data.sql
└─ test
   └─ java/com/example/salon   # 단위·통합 테스트
```

**시작점**  
- 인증 흐름: `config/SecurityConfig.java` → `filter/JwtAuthenticationFilter.java`  
- 회원 기능: `controller/MemberController.java` → `service/MemberService.java`  
- 관리자 화면: `controller/AdminMemberController.java`  

---  

## 🚀 실행 방법 (Detailed)

1. **Prerequisite**  
   - JDK 17 (`java -version`)  
   - Docker (선택) → `docker compose up -d mysql` 로 MySQL 컨테이너 실행 가능  

2. **환경 변수** (`.env.example`)

```properties
DB_URL=jdbc:mysql://localhost:3306/salon?useSSL=false&serverTimezone=UTC
DB_USERNAME=root
DB_PASSWORD=your_password
JWT_SECRET=your_very_secret_key
EXTERNAL_API_URL=https://api.example.com/salons
```

3. **빌드 & 실행**  

```bash
# Maven Wrapper 사용
./mvnw clean package -DskipTests
java -jar target/salon-web-0.0.1-SNAPSHOT.jar
```

4. **IDE 실행**  
   - IntelliJ → `SalonApplication` 메인 클래스 Run  
   - `application-dev.yml` 로 프로파일 지정 (`-Dspring.profiles.active=dev`)

5. **테스트 실행**  

```bash
./mvnw test
```

---  

## 🐞 트러블슈팅 & 배운 점  

| 문제 | 원인 | 해결 방법 |
|------|------|-----------|
| 로그인 후 페이지가 403 | `SecurityContext` 에 권한이 매핑되지 않음 | `JwtAuthenticationFilter` 에 `GrantedAuthority` 설정 로직 추가 |
| 외부 API 호출 시 `Read timed out` | 로컬 네트워크 제한 | `RestTemplate` 에 `setConnectTimeout/ReadTimeout` 5s 로 설정 |
| QueryDSL 빌드 오류 (`Q` 클래스 미생성) | `apt-maven-plugin` 설정 누락 | `pom.xml` 에 `annotationProcessorPaths` 추가 후 `mvn clean compile` |
| 페이지네이션 시 totalCount 가 0 | `Pageable` 전달 순서 오류 | `PageRequest.of(page, size, Sort.by("id").descending())` 로 수정 |
| Thymeleaf fragment 충돌 | 동일 이름 fragment 를 여러 템플릿에 정의 | `th:replace="fragments/header :: header"` 와 같이 네임스페이스 명시 |

**핵심 교훈**  
- 인증 필터와 예외 처리 로직을 **단일 책임** 으로 분리하면 디버깅이 쉬워진다.  
- QueryDSL 은 **빌드 단계**에서 `Q` 클래스가 생성되는지 항상 확인하고, IDE 캐시를 정리한다.  
- 외부 API 연동은 **타임아웃/재시도** 정책을 기본으로 구현해 서비스 가용성을 확보한다.  

---  

## 📈 향후 개선 계획  

- Docker Compose 기반 전체 스택(앱 + MySQL) 배포 자동화  
- Redis 기반 세션/Cache 도입으로 조회 성능 향상  
- Spring Batch 로 예약 데이터 정기 정리 작업 추가  
- API 문서 자동 생성을 위해 Springdoc OpenAPI 적용  

---  

## 📜 License  

MIT License. See `LICENSE` file.