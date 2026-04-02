// admin.js — 관리자 페이지 전용 자바스크립트
// =====================================================
// 관리자는 게시글/회원/예약/살롱 등 다양한 목록을 볼 수 있습니다.
// 이 스크립트는 관리자 화면의 공통 동작을 정의합니다.

document.addEventListener('DOMContentLoaded', function () {
    /*
     * 검색 입력: Enter 키로 필터 폼 제출
     * --------------------------------------------
     * 관리자 페이지의 검색 입력에서 Enter 키를 누르면 폼을 즉시
     * 제출하여 검색을 수행합니다. 폼이 이미 submit 버튼을 가지고
     * 있어도 키보드 입력을 지원하기 위해 추가합니다.
     */
    var adminSearchInputs = document.querySelectorAll('.admin-search-input');
    adminSearchInputs.forEach(function (input) {
        input.addEventListener('keypress', function (e) {
            if (e.key === 'Enter') {
                e.preventDefault();
                if (input.form) {
                    input.form.submit();
                }
            }
        });
    });
});
