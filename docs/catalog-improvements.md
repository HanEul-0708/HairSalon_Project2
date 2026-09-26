# 미용실·디자이너·시술 개선 및 검증

## 동작

- 공개 미용실 목록은 15개씩 DB에서 조회하며 지도에는 현재 페이지 결과를 표시한다.
- 미용실과 시술 정렬 메뉴는 공통 아이보리·갈색·골드 테마를 사용한다. 선택 상자와 메뉴의 간격은 0이며 좌우 폭이 일치한다. 미용실 메뉴는 항목 높이와 내부 여백을 줄여 지도 위의 빈 공간에 표시하며, 열고 닫아도 지도 위치가 바뀌지 않는다. 화살표는 배경과 분리된 SVG로 유지하며, 키보드 선택·취소와 미용실 AJAX 후 포커스를 지원한다. JavaScript를 사용할 수 없으면 기존 select를 표시한다.
- 시술의 가격·소요 시간·평점·이름 정렬과 가격 비교는 DB 페이징을 사용한다. 트렌드는 기존 필터 결과의 시술명 빈도 기준을 유지한다.
- 디자이너 목록은 한 번 조회한 후보와 일괄 조회한 평점을 재사용한다. 상세·좋아요 조회는 필요한 ID의 평점만 읽는다.
- 숫자 변환 오류·범위 초과를 한국어로 표시하고 거부된 입력을 보존한다. 검증에 실패하면 검색/추천용 DB·외부 호출을 하지 않는다. `messages.properties`는 이 변환 오류 문구를 제공하므로 유지한다.
- 미용실에 디자이너나 시술이 있으면 삭제할 수 없다. 취소·완료를 포함한 예약 이력이나 디자이너 리뷰가 있으면 대상 삭제·소속 이동을 차단한다. 이름·가격 등 일반 수정은 가능하다.
- 회원 연결은 존재·DESIGNER 권한·중복을 검증한다. 회원 잠금 후 디자이너 잠금을 얻으며, 역할 변경도 같은 규칙을 사용한다.
- 예약 생성·변경은 디자이너 → 시술 순서로 읽기 잠금을 얻는다. 삭제·이동은 부모 쓰기 잠금 후 최신 이력을 확인해 대기 중 완료된 예약도 보호한다.
- Kakao와 AI HTTP 호출은 DB 트랜잭션 밖에서 수행한다. 연결 2초·읽기 5초가 기본값이며 설정으로 변경할 수 있다.
- 외부 미용실은 출처와 외부 ID로 식별한다. 신규 데이터는 예약 불가이고 기존 관리자 설정은 유지한다. 저장 충돌은 새 트랜잭션에서 최대 3회 시도하며 HTTP 요청은 반복하지 않는다.
- AI는 전체 후보를 평가한 뒤 상위 8명을 모델에 전달하고 최대 6명을 추천한다. 모델 실패·잘못된 후보는 로컬 추천으로 처리한다.

## 검증 명령

Java 21, MySQL 8 서버/클라이언트, Node 24가 필요하다. Windows의 기본 `java`가 Java 11이면 JAR를 실행할 수 없다. IntelliJ SDK와 `JAVA_HOME`을 Java 21로 지정한다.

```powershell
# 단위·MVC·실제 Thymeleaf 렌더링 (개발 DB 사용 안 함)
.\gradlew.bat test --offline --console=plain

# 별도 MySQL 인스턴스 + 전체 테스트 + JAR + 8080 실제 기능 검증/재기동
.\scripts\test-catalog.ps1 -Smoke

# MySQL 설치 위치 또는 JDK 경로가 다르면 명시
.\scripts\test-catalog.ps1 -Smoke -MySqlBin 'C:\MySQL\bin' -JavaHome 'C:\Java\jdk-21'

# 위 단위 테스트가 생성한 실제 Thymeleaf HTML을 세 브라우저에서 검증
node src/test/browser/salon-rating-slider.mjs
node src/test/browser/salon-rating-slider-firefox.mjs
node src/test/browser/salon-sort-dropdown.mjs

# 8080에서 실행 중이며 미용실·디자이너·시술이 각각 30개 이상인 개발 데이터로 GET 검증
node src/test/browser/catalog-live-browser.mjs
```

`test`는 MySQL 태그를 제외한다. DB 검증까지 완료하려면 반드시 `test-catalog.ps1`을 실행한다. `mysqlTest`는 환경변수 `CATALOG_TEST_DB_URL`이 없으면 실패하며 매번 다시 실행한다. 스크립트는 `build/catalog-mysql` 아래에 별도 데이터 디렉터리를 만들고 임의 DB 포트를 사용한다. 기존 3306 개발 DB를 초기화하지 않는다.

`-Smoke`는 8080이 비어 있을 때만 자체 애플리케이션을 실행한다. 관리자 로그인, 폼 검증, 등록·수정·삭제, 예약 생성·변경·취소, 이력 보호를 확인하고 종료·재기동 후 같은 검증을 반복한다. 기본적으로 성공·실패 모두 자체 서버를 종료한다. `-KeepServer`는 문제 분석용이며 해당 실행의 DB만 유지한다.

브라우저 테스트는 렌더링된 HTML과 실제 CSS/JS를 사용한다. Chrome·Edge는 설치된 브라우저를 사용하며 Firefox 준비 명령은 해당 테스트 파일 상단에 있다. 실제 Kakao 지도와 외부 AI 계정의 가용성은 이 자동 검증 범위에 포함되지 않는다. 외부 HTTP의 오류·시간 초과·응답 처리는 로컬 HTTP 서버와 대체 클라이언트로 검증한다.

## 데이터베이스 변경

기존 DB에는 `sql/migrations/20260924_catalog_integrity.sql`만 적용한다. `sql/HairSalon_Project2_table.sql`은 DB를 삭제하는 신규 설치용 스크립트이므로 기존 데이터에 실행하지 않는다.

애플리케이션을 중지하고 백업한 뒤 대상 DB를 명시하여 마이그레이션을 실행한다. 관련 외래키를 RESTRICT로 바꾸고 좋아요만 CASCADE로 정리하며 외부 ID의 복합 유일성을 맞춘다. MySQL DDL은 자동 커밋되므로 실패 시 현재 제약 조건을 확인한 후 재실행한다. 스크립트는 재실행할 수 있다. 통합 테스트는 초기 스키마와 기존 CASCADE 스키마에서 각각 두 번 적용해 이력 보존을 검사한다.

## 결과 파일

- 단위·MVC: `build/reports/tests/test/index.html`
- MySQL 통합: `build/reports/tests/mysqlTest/index.html`
- 8080 실제 기능: `build/reports/catalog-smoke/report.json`
- Chrome·Edge: `build/reports/rating-slider/report.json`
- Firefox: `build/rating-slider/firefox-tools/result.json`
- 미용실·시술 정렬 메뉴(세 브라우저·모바일·JavaScript 비활성): `build/reports/salon-sort/report.json`
- 실행 중인 서버의 Chrome·Edge·Firefox 검증: `build/reports/catalog-live-browser.json`
- 실행별 DB·애플리케이션 로그: `build/catalog-mysql/<실행 ID>/`

## 2026-09-25 검증 결과

- 단위·MVC·Thymeleaf·로컬 HTTP 테스트: 369건 통과, 실패/건너뜀 0건. AI 추천 수의 오류 값 보존과 정상 선택, 시술 정렬 5개 옵션의 서버 렌더링을 포함한다.
- 별도 MySQL 8.0.43 통합 테스트: 34건 통과, 실패/건너뜀 0건. 초기/기존 스키마 마이그레이션 반복, 데이터 보존, 두 트랜잭션 경쟁, SQL 조회 횟수를 포함한다. 새 DB에서 재실행해 테스트가 생략되지 않는 것도 확인했다.
- 8080 실제 기능 점검: 116개 검증을 기동·재기동 각각 통과했다. 두 실행의 애플리케이션 ERROR 로그는 0건이었다.
- Chrome·Edge 평점 슬라이더: 308개 조합, 3,048개 검증 통과. Firefox: 454개 검증, AJAX 22회, JavaScript 오류 0건.
- 공통 정렬 메뉴와 배치 변경 후 Chrome·Edge·Firefox 메뉴 검증 1,107건, Chrome·Edge 기존 평점·검색 상호작용 252건을 통과했다. 데스크톱·390px·320px에서 간격 0, 좌우 폭 일치, 지도 비겹침과 메뉴를 닫은 뒤 지도 위치 복원을 확인했다. 시술의 5개 정렬값, 검색 버튼 제출, 두 도메인의 동일한 색상·글꼴·모서리, 키보드 조작, 필터 유지, AJAX 후 포커스와 JavaScript 비활성 동작을 검증했다. 메뉴 스크린샷 39장은 `build/reports/salon-sort/`에 저장했다.
- 최신 JAR의 실제 개발 화면을 Chrome·Edge·Firefox에서 각각 26개, 총 78개 시나리오로 검증했다. 세 도메인의 1→2페이지 이동, 미용실 AJAX, AI 추천 수 오류 값 8종의 보존·교정, 정상값 1~6·기본값 3이 모두 통과했으며 JavaScript 오류는 0건이었다.
- 기존 개발 DB 마이그레이션 적용 후 전체 16개 테이블 체크섬이 적용 전 백업과 일치했다. 미용실 46개·디자이너 131명·시술 221개를 보존했다.
- 실제 개발 설정으로 Java 21/8080 기동 후 홈, 미용실 목록·2페이지, 시술 평점순·가격 비교, 디자이너 목록·잘못된 숫자 입력 화면이 모두 HTTP 200으로 응답했다. 기동과 조회 후에도 16개 테이블 체크섬이 일치했다.

로컬 백업은 `build/catalog-backups/20260925-091739/backup.sql`, 마이그레이션 적용·체크섬 결과는 같은 디렉터리의 `migration-result.json`에 저장했다. 해당 백업과 실행 로그는 Git에 포함되지 않는다.

최종 브라우저 검증 후에도 개발 DB 16개 테이블의 체크섬이 백업과 일치했다(`build/catalog-backups/development-data-verification.json`). 검증용 애플리케이션과 별도 MySQL 프로세스를 종료하고 8080이 비어 있음을 확인했다. IntelliJ에서 실행할 때는 애플리케이션 인스턴스를 하나만 실행한다.

## 2026-09-26 미용실 정렬 메뉴 높이 보정

- 지도를 밀어내던 배치를 제거하고 메뉴를 선택 상자 바로 아래에 겹쳐 표시한다. 미용실에만 항목 최소 높이 30px, 메뉴 여백 4px를 적용해 세 항목이 총 99px 안에 보이도록 줄였다.
- 관련 렌더링·폼 테스트 40건과 실행 JAR 빌드를 통과했다.
- 실행 중인 8080 화면의 1440px 너비에서 메뉴 높이 99px, 선택 상자와의 간격·좌우 오차 0px, 메뉴와 지도 사이 간격 9.875px를 확인했다. 메뉴를 열고 닫아도 지도 이동은 0px였다(`build/reports/salon-sort/live-compact.json`).
- Chrome·Edge·Firefox 검증 1,371건을 통과했다. 각 브라우저의 1280·1440·1920·390·320px에서 메뉴 높이 99px, 지도 이동 0px, 세 항목의 완전한 표시와 지도 비겹침을 확인했다. JavaScript 오류와 실패한 HTTP 응답은 0건이었다.
