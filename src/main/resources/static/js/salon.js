/*
 * salon.js — 살롱 페이지 전용 스크립트
 * =====================================================
 * 포함 기능
 * 1. 검색 폼 Enter 제출 보강
 * 2. 카드 포커스 접근성 보강
 */

document.addEventListener("DOMContentLoaded", function () {
    bindSalonSearchForm();
    bindSalonCardFocusEffect();
});

function bindSalonSearchForm() {
    var form = document.querySelector(".salon-search-form");
    if (!form) return;

    var inputs = form.querySelectorAll("input, select");
    inputs.forEach(function (input) {
        input.addEventListener("keypress", function (event) {
            if (event.key === "Enter") {
                event.preventDefault();
                form.submit();
            }
        });
    });
}

function bindSalonCardFocusEffect() {
    var links = document.querySelectorAll(".salon-card a");
    links.forEach(function (link) {
        link.addEventListener("focus", function () {
            var card = link.closest(".salon-card");
            if (card) {
                card.style.transform = "translateY(-4px)";
                card.style.boxShadow = "var(--shadow-md)";
            }
        });

        link.addEventListener("blur", function () {
            var card = link.closest(".salon-card");
            if (card) {
                card.style.transform = "";
                card.style.boxShadow = "";
            }
        });
    });
}