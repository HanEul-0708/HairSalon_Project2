/*
 * service.js — 시술 페이지 전용
 */

document.addEventListener("DOMContentLoaded", function () {

    // 검색 폼 Enter 제출
    var form = document.querySelector(".service-search-form");
    if (form) {
        form.querySelectorAll("input").forEach(function (input) {
            input.addEventListener("keypress", function (e) {
                if (e.key === "Enter") {
                    e.preventDefault();
                    form.submit();
                }
            });
        });
    }

    // 삭제 확인
    var deleteForms = document.querySelectorAll('form[data-service-delete]');
    deleteForms.forEach(function (form) {
        form.addEventListener("submit", function (e) {
            if (!confirm("정말 삭제하시겠습니까?")) {
                e.preventDefault();
            }
        });
    });

});