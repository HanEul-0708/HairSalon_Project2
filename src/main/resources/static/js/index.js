/*
 * index.js — 메인 홈 화면 전용 스크립트
 * =====================================================
 * 현재 CSS 기준으로 안전하게 동작하는 버전
 *
 * 포함 기능
 * 1. 히어로 숫자 카운트업
 * 2. 스크롤 시 카드/섹션에 가벼운 등장 효과 클래스 추가
 * 3. 키보드 포커스 접근성 보강
 * 4. 앵커 링크 부드러운 이동
 */

document.addEventListener("DOMContentLoaded", function () {
    initHeroStatsCountUp();
    initScrollRevealEffect();
    initCardKeyboardInteraction();
    initSmoothAnchorLinks();
});

/* =============================================
   1. 히어로 숫자 카운트업
   ============================================= */
function initHeroStatsCountUp() {
    var stats = document.querySelectorAll(".hero__stat strong");

    if (!stats.length) {
        return;
    }

    stats.forEach(function (stat) {
        var originalText = stat.textContent.trim();
        var parsed = parseStatText(originalText);

        if (!parsed.isCountable) {
            return;
        }

        animateCounter(stat, parsed);
    });
}

function parseStatText(text) {
    var numberText = text.replace(/[^0-9.]/g, "");
    var suffix = text.replace(/[0-9.]/g, "");
    var hasDecimal = numberText.indexOf(".") !== -1;
    var value = hasDecimal ? parseFloat(numberText) : parseInt(numberText, 10);

    if (isNaN(value)) {
        return {
            isCountable: false
        };
    }

    return {
        isCountable: true,
        value: value,
        suffix: suffix,
        hasDecimal: hasDecimal
    };
}

function animateCounter(element, parsed) {
    var duration = 1200;
    var startTime = null;

    function step(timestamp) {
        if (!startTime) {
            startTime = timestamp;
        }

        var progress = Math.min((timestamp - startTime) / duration, 1);
        var eased = easeOutCubic(progress);
        var currentValue = parsed.value * eased;

        if (parsed.hasDecimal) {
            element.textContent = currentValue.toFixed(1) + parsed.suffix;
        } else {
            element.textContent = Math.floor(currentValue) + parsed.suffix;
        }

        if (progress < 1) {
            window.requestAnimationFrame(step);
        } else {
            element.textContent = parsed.hasDecimal
                ? parsed.value.toFixed(1) + parsed.suffix
                : parsed.value + parsed.suffix;
        }
    }

    window.requestAnimationFrame(step);
}

function easeOutCubic(x) {
    return 1 - Math.pow(1 - x, 3);
}

/* =============================================
   2. 스크롤 가벼운 등장 효과
   ============================================= */
function initScrollRevealEffect() {
    var targets = document.querySelectorAll(
        ".feature-card, .salon-rank-card, .designer-card, .review-card, .cta-banner__inner"
    );

    if (!targets.length) {
        return;
    }

    /* 초기 숨김은 절대 하지 않음 */
    targets.forEach(function (target) {
        target.style.transition =
            "transform 0.45s ease, box-shadow 0.25s ease, border-color 0.25s ease, opacity 0.45s ease";
    });

    if (!("IntersectionObserver" in window)) {
        return;
    }

    var observer = new IntersectionObserver(function (entries, obs) {
        entries.forEach(function (entry) {
            if (!entry.isIntersecting) {
                return;
            }

            /* 아주 살짝만 효과 */
            entry.target.style.opacity = "1";
            entry.target.style.transform = "translateY(0)";
            obs.unobserve(entry.target);
        });
    }, {
        threshold: 0.08,
        rootMargin: "0px 0px -40px 0px"
    });

    targets.forEach(function (target, index) {
        /* 화면은 그대로 보이게 두고, 처음 값만 아주 미세하게 */
        target.style.opacity = "1";
        target.style.transform = "translateY(0)";

        /* 렌더 후 한 박자 뒤에 observer 등록 */
        setTimeout(function () {
            target.style.opacity = "0.999";
            target.style.transform = "translateY(8px)";
            observer.observe(target);
        }, Math.min(index * 40, 200));
    });
}

/* =============================================
   3. 카드 포커스 접근성 보강
   ============================================= */
function initCardKeyboardInteraction() {
    var clickableElements = document.querySelectorAll(
        ".feature-card, .salon-rank-card__btn, .designer-card__link, .section-more a, .cta-banner__btn"
    );

    clickableElements.forEach(function (element) {
        element.addEventListener("focus", function () {
            var card = findCardElement(element);
            if (!card) {
                return;
            }

            card.style.transform = "translateY(-4px)";
            card.style.boxShadow = "var(--shadow-md)";
        });

        element.addEventListener("blur", function () {
            var card = findCardElement(element);
            if (!card) {
                return;
            }

            card.style.transform = "";
            card.style.boxShadow = "";
        });
    });
}

function findCardElement(element) {
    return element.closest(".feature-card")
        || element.closest(".salon-rank-card")
        || element.closest(".designer-card")
        || element.closest(".review-card")
        || element.closest(".cta-banner__inner");
}

/* =============================================
   4. 앵커 링크 부드러운 이동
   ============================================= */
function initSmoothAnchorLinks() {
    var anchors = document.querySelectorAll('a[href^="#"]');

    anchors.forEach(function (anchor) {
        anchor.addEventListener("click", function (event) {
            var targetId = anchor.getAttribute("href");

            if (!targetId || targetId === "#") {
                return;
            }

            var target = document.querySelector(targetId);

            if (!target) {
                return;
            }

            event.preventDefault();

            target.scrollIntoView({
                behavior: "smooth",
                block: "start"
            });
        });
    });
}