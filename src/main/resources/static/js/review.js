document.addEventListener("DOMContentLoaded", function () {
    bindReviewRatingStars();
    bindReviewContentCounter();
    bindReviewFormSubmit();
});

function bindReviewRatingStars() {
    var stars = document.querySelectorAll(".review-rating__star");
    var ratingInput = document.querySelector('input[name="rating"]');

    if (!stars.length || !ratingInput) return;

    function renderStars(value) {
        stars.forEach(function (star, index) {
            if (index < value) {
                star.classList.add("is-active");
            } else {
                star.classList.remove("is-active");
            }
        });
    }

    stars.forEach(function (star) {
        star.addEventListener("click", function () {
            var value = parseInt(star.getAttribute("data-value"), 10);
            if (isNaN(value)) return;

            ratingInput.value = value;
            renderStars(value);
        });
    });

    renderStars(parseInt(ratingInput.value || "0", 10));
}

function bindReviewContentCounter() {
    var textarea = document.querySelector(".review-form textarea");
    if (!textarea) return;

    var counter = document.createElement("p");
    counter.className = "form-help-text";
    textarea.insertAdjacentElement("afterend", counter);

    function updateCounter() {
        counter.textContent = "현재 " + textarea.value.length + "자 입력";
    }

    textarea.addEventListener("input", updateCounter);
    updateCounter();
}

function bindReviewFormSubmit() {
    var form = document.getElementById("reviewForm");
    if (!form) return;

    form.addEventListener("submit", async function (event) {
        event.preventDefault();

        var reservationId = document.getElementById("reservationId");
        var rating = document.getElementById("rating");
        var content = document.getElementById("content");
        var image = document.getElementById("image");

        if (!reservationId || !rating || !content) {
            alert("리뷰 폼 구성이 올바르지 않습니다.");
            return;
        }

        if (!reservationId.value) {
            alert("예약 정보가 없습니다. 예약 화면을 통해 다시 진입해주세요.");
            return;
        }

        if (!content.value.trim()) {
            alert("리뷰 내용을 입력해주세요.");
            return;
        }

        var reviewRequest = {
            reservationId: Number(reservationId.value),
            rating: Number(rating.value),
            content: content.value.trim()
        };

        var jsonHeaders = {
            "Content-Type": "application/json"
        };
        applyCsrfHeaders(jsonHeaders);

        try {
            var reviewResponse = await fetch("/api/reviews", {
                method: "POST",
                headers: jsonHeaders,
                body: JSON.stringify(reviewRequest)
            });

            if (!reviewResponse.ok) {
                var errorText = await reviewResponse.text();
                alert("리뷰 등록에 실패했습니다.\n" + errorText);
                return;
            }

            var createdReview = await reviewResponse.json();

            if (image && image.files && image.files.length > 0) {
                var formData = new FormData();
                formData.append("file", image.files[0]);

                var imageHeaders = {};
                applyCsrfHeaders(imageHeaders);

                var imageResponse = await fetch("/reviews/" + createdReview.reviewId + "/images", {
                    method: "POST",
                    headers: imageHeaders,
                    body: formData
                });

                if (!imageResponse.ok) {
                    var imageErrorText = await imageResponse.text();
                    alert("리뷰는 등록됐지만 이미지는 업로드하지 못했습니다.\n" + imageErrorText);
                    window.location.href = "/reviews";
                    return;
                }
            }

            alert("리뷰가 등록되었습니다.");
            window.location.href = "/reviews";
        } catch (error) {
            console.error(error);
            alert("리뷰 요청 중 오류가 발생했습니다.");
        }
    });
}

function applyCsrfHeaders(headers) {
    var csrfTokenMeta = document.querySelector('meta[name="_csrf"]');
    var csrfHeaderMeta = document.querySelector('meta[name="_csrf_header"]');

    if (!csrfTokenMeta || !csrfHeaderMeta) {
        return;
    }

    headers[csrfHeaderMeta.getAttribute("content")] = csrfTokenMeta.getAttribute("content");
}
