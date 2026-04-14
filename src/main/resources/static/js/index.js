document.addEventListener("DOMContentLoaded", function () {
    initScrollRevealEffect();
    initCardKeyboardInteraction();
    initSmoothAnchorLinks();
    initRegionalHighlightsRotation();
});

function initRegionalHighlightsRotation() {
    var highlights = Array.isArray(window.regionalHighlights) ? window.regionalHighlights : [];
    var regionLabel = document.getElementById("currentRegionLabel");
    var salonGrid = document.getElementById("topSalonsGrid");
    var designerGrid = document.getElementById("topDesignersGrid");
    var reviewGrid = document.getElementById("recentReviewsGrid");
    var dotsBox = document.getElementById("regionSpotlightDots");

    if (!highlights.length || !regionLabel || !salonGrid || !designerGrid || !reviewGrid || !dotsBox) {
        return;
    }

    var index = 0;
    var intervalMs = 10000;

    dotsBox.innerHTML = highlights.map(function (_, dotIndex) {
        return '<button type="button" class="region-spotlight__dot' + (dotIndex === 0 ? ' is-active' : '') +
            '" data-region-dot="' + dotIndex + '" aria-label="지역 ' + (dotIndex + 1) + ' 보기"></button>';
    }).join("");

    function escapeHtml(value) {
        return String(value == null ? "" : value)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#39;");
    }

    function ratingStars(count) {
        var stars = "";
        for (var i = 0; i < Number(count || 0); i += 1) {
            stars += "<span>★</span>";
        }
        return stars || "<span>☆</span>";
    }

    function renderSalons(items) {
        if (!items.length) {
            salonGrid.innerHTML = '<div class="region-empty-card">이 지역의 추천 살롱 데이터가 없습니다.</div>';
            return;
        }

        salonGrid.innerHTML = items.map(function (salon, itemIndex) {
            return '' +
                '<div class="salon-rank-card salon-rank-card--' + (itemIndex + 1) + '">' +
                '  <div class="salon-rank-card__rank">' + (itemIndex + 1) + '위</div>' +
                '  <div class="salon-rank-card__body">' +
                '    <h3 class="salon-rank-card__name">' + escapeHtml(salon.salonName) + '</h3>' +
                '    <p class="salon-rank-card__addr">' + escapeHtml(salon.address || "") + '</p>' +
                '    <div class="salon-rank-card__stars">' +
                '      <span class="star-filled">★★★★★</span>' +
                '      <strong>' + escapeHtml(salon.averageRating || "0.0") + '</strong>' +
                '      <span class="salon-rank-card__count">리뷰 ' + escapeHtml(salon.reviewCount || 0) + '건</span>' +
                '    </div>' +
                '    <a href="/salons/' + encodeURIComponent(salon.salonId) + '" class="salon-rank-card__btn">매장 보기 →</a>' +
                '  </div>' +
                '</div>';
        }).join("");
    }

    function renderDesigners(items) {
        if (!items.length) {
            designerGrid.innerHTML = '<div class="region-empty-card">이 지역의 인기 디자이너 데이터가 없습니다.</div>';
            return;
        }

        designerGrid.innerHTML = items.map(function (designer) {
            var initial = designer.designerName ? designer.designerName.charAt(0) : "디";
            return '' +
                '<div class="designer-card">' +
                '  <div class="designer-card__avatar">' + escapeHtml(initial) + '</div>' +
                '  <div class="designer-card__body">' +
                '    <h4 class="designer-card__name">' + escapeHtml(designer.designerName) + '</h4>' +
                '    <p class="designer-card__salon">' + escapeHtml(designer.salonName) + '</p>' +
                '    <p class="designer-card__career">경력 ' + escapeHtml(designer.careerYears || 0) + '년</p>' +
                '    <div class="designer-card__stars">' +
                '      <span class="star-filled">★★★★★</span>' +
                '      <span>' + escapeHtml(designer.averageRating || "0.0") + '</span>' +
                '      <span class="designer-card__count">리뷰 ' + escapeHtml(designer.reviewCount || 0) + '건</span>' +
                '    </div>' +
                '  </div>' +
                '  <a href="/designers/' + encodeURIComponent(designer.designerId) + '" class="designer-card__link">프로필 보기</a>' +
                '</div>';
        }).join("");
    }

    function renderReviews(items) {
        if (!items.length) {
            reviewGrid.innerHTML = '<div class="region-empty-card">이 지역의 최근 리뷰 데이터가 없습니다.</div>';
            return;
        }

        reviewGrid.innerHTML = items.map(function (review) {
            var initial = review.maskedMemberName ? review.maskedMemberName.charAt(0) : "고";
            return '' +
                '<div class="review-card">' +
                '  <div class="review-card__header">' +
                '    <div class="review-card__avatar">' + escapeHtml(initial) + '</div>' +
                '    <div>' +
                '      <strong class="review-card__name">' + escapeHtml(review.maskedMemberName) + '</strong>' +
                '      <p class="review-card__info">' + escapeHtml(review.salonName) + ' · ' + escapeHtml(review.serviceName) + '</p>' +
                '    </div>' +
                '    <div class="review-card__stars">' + ratingStars(review.rating) + '</div>' +
                '  </div>' +
                '  <p class="review-card__content">' + escapeHtml(review.content) + '</p>' +
                '  <p class="review-card__date">' + escapeHtml(review.createdDate) + '</p>' +
                '</div>';
        }).join("");
    }

    function activate(currentIndex) {
        var highlight = highlights[currentIndex];
        regionLabel.textContent = highlight.label || "전국";
        renderSalons(Array.isArray(highlight.topSalons) ? highlight.topSalons : []);
        renderDesigners(Array.isArray(highlight.topDesigners) ? highlight.topDesigners : []);
        renderReviews(Array.isArray(highlight.recentReviews) ? highlight.recentReviews : []);

        dotsBox.querySelectorAll("[data-region-dot]").forEach(function (dot, dotIndex) {
            dot.classList.toggle("is-active", dotIndex === currentIndex);
        });
    }

    dotsBox.addEventListener("click", function (event) {
        var button = event.target.closest("[data-region-dot]");
        if (!button) {
            return;
        }
        index = Number(button.getAttribute("data-region-dot")) || 0;
        activate(index);
        restartInterval();
    });

    var timerId = null;

    function restartInterval() {
        if (timerId) {
            window.clearInterval(timerId);
        }
        timerId = window.setInterval(function () {
            index = (index + 1) % highlights.length;
            activate(index);
        }, intervalMs);
    }

    activate(index);
    if (highlights.length > 1) {
        restartInterval();
    }
}

function initScrollRevealEffect() {
    var targets = document.querySelectorAll(
        ".feature-card, .salon-rank-card, .designer-card, .review-card, .cta-banner__inner, .region-spotlight__panel"
    );

    if (!targets.length || !("IntersectionObserver" in window)) {
        return;
    }

    var observer = new IntersectionObserver(function (entries, obs) {
        entries.forEach(function (entry) {
            if (!entry.isIntersecting) {
                return;
            }

            entry.target.style.opacity = "1";
            entry.target.style.transform = "translateY(0)";
            obs.unobserve(entry.target);
        });
    }, {
        threshold: 0.08,
        rootMargin: "0px 0px -40px 0px"
    });

    targets.forEach(function (target, index) {
        target.style.transition = "transform 0.45s ease, box-shadow 0.25s ease, border-color 0.25s ease, opacity 0.45s ease";
        target.style.opacity = "0.999";
        target.style.transform = "translateY(8px)";
        window.setTimeout(function () {
            observer.observe(target);
        }, Math.min(index * 40, 200));
    });
}

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
        || element.closest(".cta-banner__inner")
        || element.closest(".region-spotlight__panel");
}

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
