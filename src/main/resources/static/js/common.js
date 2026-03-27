/*
 * common.js — 전체 공통 자바스크립트
 * =====================================================
 * 포함 기능
 * 1. 모바일 서랍 메뉴 열기 / 닫기
 * 2. 스크롤 시 헤더 그림자 강화 효과
 * 3. 알림 메시지 자동 닫기
 * 4. 삭제 확인 팝업 공통 함수
 *
 * 의존성 없음 — 순수 자바스크립트 (jQuery 불필요)
 */

/*
    DOMContentLoaded
    HTML 파싱이 완료된 후 실행
    이미지 로딩은 기다리지 않아서 빠름
*/
document.addEventListener("DOMContentLoaded", function () {

    /* =============================================
       1. 모바일 서랍 메뉴
       ============================================= */

    /* 관련 요소 찾기 */
    var mobileMenuButton     = document.getElementById("mobileMenuButton");
    var mobileMenuCloseButton= document.getElementById("mobileMenuCloseButton");
    var mobileDrawer         = document.getElementById("mobileDrawer");
    var mobileBackdrop       = document.getElementById("mobileBackdrop");

    /*
        메뉴 열기
        - mobileDrawer 에 is-open 클래스 추가 → CSS에서 오른쪽으로 슬라이드
        - mobileBackdrop 에 is-open 클래스 추가 → 배경 어둡게
        - 배경 스크롤 방지 (메뉴 열린 상태에서 뒤가 스크롤되면 어색함)
    */
    function openMobileMenu() {
        if (mobileDrawer)   mobileDrawer.classList.add("is-open");
        if (mobileBackdrop) mobileBackdrop.classList.add("is-open");
        document.body.style.overflow = "hidden";
    }

    /*
        메뉴 닫기
        - is-open 클래스 제거 → 원래 위치로 슬라이드
        - 배경 스크롤 복원
    */
    function closeMobileMenu() {
        if (mobileDrawer)   mobileDrawer.classList.remove("is-open");
        if (mobileBackdrop) mobileBackdrop.classList.remove("is-open");
        document.body.style.overflow = "";
    }

    /* 열기 버튼 클릭 */
    if (mobileMenuButton) {
        mobileMenuButton.addEventListener("click", openMobileMenu);
    }

    /* 닫기 버튼 클릭 */
    if (mobileMenuCloseButton) {
        mobileMenuCloseButton.addEventListener("click", closeMobileMenu);
    }

    /* 배경(어두운 오버레이) 클릭 시 닫기 */
    if (mobileBackdrop) {
        mobileBackdrop.addEventListener("click", closeMobileMenu);
    }

    /* Esc 키 누르면 닫기 */
    document.addEventListener("keydown", function (e) {
        if (e.key === "Escape") closeMobileMenu();
    });


    /* =============================================
       2. 스크롤 시 헤더 그림자 강화
       ============================================= */

    /*
        스크롤을 조금이라도 내리면 헤더에 더 진한 그림자 추가
        포트폴리오에서 디테일을 살려주는 작은 효과
    */
    var headerMain = document.querySelector(".header-main");

    if (headerMain) {
        window.addEventListener("scroll", function () {
            if (window.scrollY > 10) {
                /* 스크롤 내린 상태 — 그림자 강화 */
                headerMain.style.boxShadow =
                    "0 4px 0 0 rgba(201,168,76,0.25), 0 4px 20px rgba(0,0,0,0.10)";
            } else {
                /* 최상단 — 기본 그림자 */
                headerMain.style.boxShadow =
                    "0 2px 0 0 rgba(201,168,76,0.18), 0 1px 4px rgba(0,0,0,0.06)";
            }
        });
    }


    /* =============================================
       3. 알림 메시지 자동 닫기
       ============================================= */

    /*
        .alert-auto-close 클래스가 붙은 알림은 3초 후 자동으로 사라짐
        스프링에서 session.setAttribute("msg", "저장되었습니다") 같은 거
        화면에 잠깐 보여주고 사라지게 할 때 사용
    */
    var autoCloseAlerts = document.querySelectorAll(".alert-auto-close");

    autoCloseAlerts.forEach(function (alert) {
        setTimeout(function () {
            /* 부드럽게 사라지게 */
            alert.style.transition = "opacity 0.5s ease";
            alert.style.opacity = "0";
            /* 투명해진 후 공간도 제거 */
            setTimeout(function () {
                alert.style.display = "none";
            }, 500);
        }, 3000); /* 3초 후 */
    });


    /* =============================================
       4. 삭제 확인 팝업 공통 함수
       ============================================= */

    /*
        사용법 (HTML에서):
        <button onclick="confirmDelete(event, '정말 삭제하시겠습니까?')"
                data-form-id="deleteForm">삭제</button>
        <form id="deleteForm" th:action="@{/boards/{id}/delete(id=${board.boardId})}"
              method="post">
            <input type="hidden" th:name="${_csrf.parameterName}"
                   th:value="${_csrf.token}">
        </form>
    */
    window.confirmDelete = function (event, message) {
        event.preventDefault();

        var msg = message || "정말 삭제하시겠습니까? 이 작업은 되돌릴 수 없습니다.";

        if (confirm(msg)) {
            /* 버튼의 data-form-id 속성에서 폼 id 가져옴 */
            var formId = event.target.getAttribute("data-form-id");
            if (formId) {
                var form = document.getElementById(formId);
                if (form) form.submit();
            }
        }
    };


    /* =============================================
       5. 현재 페이지 GNB 메뉴 active 처리
       ============================================= */

    /*
        현재 URL을 읽어서 해당하는 GNB 링크에 active 클래스 추가
        Thymeleaf 서버사이드 처리로 대체 가능하지만
        클라이언트 JS로 해두면 SSR 없이도 동작함
    */
    var currentPath = window.location.pathname;
    var gnbLinks    = document.querySelectorAll(".gnb__link");

    gnbLinks.forEach(function (link) {
        var href = link.getAttribute("href");
        if (!href || href === "#") return;

        /* 정확히 일치하거나 하위 경로면 active */
        if (currentPath === href ||
            (href !== "/" && currentPath.startsWith(href))) {
            link.classList.add("active");
        }
    });

}); /* DOMContentLoaded 끝 */