/*
 * member.js — 회원 관련
 */

document.addEventListener("DOMContentLoaded", function () {

    // 회원 탈퇴 확인
    var deleteForms = document.querySelectorAll('form[data-member-delete]');
    deleteForms.forEach(function (form) {
        form.addEventListener("submit", function (e) {
            if (!confirm("정말 탈퇴하시겠습니까?")) {
                e.preventDefault();
            }
        });
    });

    // 로그인 입력 체크
    var loginForm = document.querySelector(".login-box form");
    if (loginForm) {
        loginForm.addEventListener("submit", function (e) {
            var id = loginForm.querySelector("input[name='memberId']");
            var pw = loginForm.querySelector("input[name='password']");

            if (!id.value || !pw.value) {
                e.preventDefault();
                alert("아이디와 비밀번호를 입력해주세요.");
            }
        });
    }

});