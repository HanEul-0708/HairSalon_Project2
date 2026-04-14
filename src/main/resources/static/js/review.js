document.addEventListener("DOMContentLoaded", function () {
    bindReviewFilterCascade();
    bindReviewRatingStars();
    bindReviewContentCounter();
    bindReviewFormSubmit();
    bindReviewDeleteButton();
    bindReviewLikeButtons();
});

var REVIEW_CONTENT_MAX_LENGTH = 1000;
var REVIEW_IMAGE_MAX_SIZE = 5 * 1024 * 1024;

function bindReviewFilterCascade() {
    var citySelect = document.getElementById("city");
    var districtSelect = document.getElementById("district");
    var neighborhoodSelect = document.getElementById("neighborhood");
    var salonSelect = document.getElementById("salonId");
    var designerSelect = document.getElementById("designerId");
    var filterData = window.reviewFilterData;

    if (!citySelect || !districtSelect || !neighborhoodSelect || !salonSelect || !designerSelect || !filterData) {
        return;
    }

    var salons = Array.isArray(filterData.salons) ? filterData.salons : [];
    var designers = Array.isArray(filterData.designers) ? filterData.designers : [];
    var cities = uniqueSorted(salons.map(function (salon) { return normalizeValue(salon.city); }).filter(Boolean));

    function normalizeValue(value) {
        return value == null ? "" : String(value).trim();
    }

    function uniqueSorted(values) {
        return Array.from(new Set(values)).sort();
    }

    function populateSelect(select, options, defaultLabel, valueKey, textKey, selectedValue) {
        select.innerHTML = "";

        var defaultOption = document.createElement("option");
        defaultOption.value = "";
        defaultOption.textContent = defaultLabel;
        select.appendChild(defaultOption);

        options.forEach(function (optionItem) {
            var option = document.createElement("option");
            option.value = valueKey ? normalizeValue(optionItem[valueKey]) : normalizeValue(optionItem);
            option.textContent = textKey ? optionItem[textKey] : optionItem;
            select.appendChild(option);
        });

        select.value = normalizeValue(selectedValue);
        if (select.value !== normalizeValue(selectedValue)) {
            select.value = "";
        }
    }

    function disableSelect(select, defaultLabel) {
        populateSelect(select, [], defaultLabel, null, null, "");
        select.disabled = true;
    }

    function enableSelect(select) {
        select.disabled = false;
    }

    function salonsByRegion() {
        var selectedCity = normalizeValue(citySelect.value);
        var selectedDistrict = normalizeValue(districtSelect.value);
        var selectedNeighborhood = normalizeValue(neighborhoodSelect.value);

        return salons.filter(function (salon) {
            return (!selectedCity || normalizeValue(salon.city) === selectedCity)
                && (!selectedDistrict || normalizeValue(salon.district) === selectedDistrict)
                && (!selectedNeighborhood || normalizeValue(salon.neighborhood) === selectedNeighborhood);
        });
    }

    function syncDistricts(resetBelow) {
        var selectedCity = normalizeValue(citySelect.value);
        if (!selectedCity) {
            disableSelect(districtSelect, "시를 먼저 선택하세요");
            syncNeighborhoods(true);
            return;
        }

        var districts = uniqueSorted(
            salons
                .filter(function (salon) {
                    return normalizeValue(salon.city) === selectedCity;
                })
                .map(function (salon) { return normalizeValue(salon.district); })
                .filter(Boolean)
        );
        var selectedDistrict = resetBelow ? "" : (districtSelect.dataset.selectedValue || districtSelect.value || "");
        enableSelect(districtSelect);
        populateSelect(districtSelect, districts, "전체 구", null, null, selectedDistrict);
        districtSelect.dataset.selectedValue = districtSelect.value || "";
        syncNeighborhoods(resetBelow);
    }

    function syncNeighborhoods(resetBelow) {
        var selectedCity = normalizeValue(citySelect.value);
        var selectedDistrict = normalizeValue(districtSelect.value);
        if (!selectedCity || !selectedDistrict) {
            disableSelect(neighborhoodSelect, "구를 먼저 선택하세요");
            syncSalonOptions(true);
            return;
        }

        var neighborhoods = uniqueSorted(
            salons
                .filter(function (salon) {
                    return normalizeValue(salon.city) === selectedCity
                        && normalizeValue(salon.district) === selectedDistrict;
                })
                .map(function (salon) { return normalizeValue(salon.neighborhood); })
                .filter(Boolean)
        );
        var selectedNeighborhood = resetBelow ? "" : (neighborhoodSelect.dataset.selectedValue || neighborhoodSelect.value || "");
        enableSelect(neighborhoodSelect);
        populateSelect(neighborhoodSelect, neighborhoods, "전체 동", null, null, selectedNeighborhood);
        neighborhoodSelect.dataset.selectedValue = neighborhoodSelect.value || "";
        syncSalonOptions(resetBelow);
    }

    function syncSalonOptions(resetSelection) {
        var selectedNeighborhood = normalizeValue(neighborhoodSelect.value);
        if (!selectedNeighborhood) {
            disableSelect(salonSelect, "동을 먼저 선택하세요");
            syncDesignerOptions(true, []);
            return;
        }

        var filteredSalons = salonsByRegion();
        var selectedSalonId = resetSelection ? "" : salonSelect.value;
        enableSelect(salonSelect);
        populateSelect(salonSelect, filteredSalons, "전체 미용실", "salonId", "salonName", selectedSalonId);
        syncDesignerOptions(resetSelection, filteredSalons);
    }

    function syncDesignerOptions(resetSelection, filteredSalons) {
        var selectedSalonId = normalizeValue(salonSelect.value);
        if (!selectedSalonId) {
            disableSelect(designerSelect, "미용실을 먼저 선택하세요");
            return;
        }

        var allowedSalonIds = filteredSalons.map(function (salon) {
            return normalizeValue(salon.salonId);
        });

        var filteredDesigners = designers.filter(function (designer) {
            var designerSalonId = normalizeValue(designer.salonId);
            return allowedSalonIds.indexOf(designerSalonId) >= 0
                && designerSalonId === selectedSalonId;
        });

        var selectedDesignerId = resetSelection ? "" : designerSelect.value;
        enableSelect(designerSelect);
        populateSelect(designerSelect, filteredDesigners, "전체 디자이너", "designerId", "designerName", selectedDesignerId);
    }

    citySelect.addEventListener("change", function () {
        districtSelect.dataset.selectedValue = "";
        neighborhoodSelect.dataset.selectedValue = "";
        syncDistricts(true);
    });

    districtSelect.addEventListener("change", function () {
        neighborhoodSelect.dataset.selectedValue = "";
        syncNeighborhoods(true);
    });

    neighborhoodSelect.addEventListener("change", function () {
        syncSalonOptions(true);
    });

    salonSelect.addEventListener("change", function () {
        syncDesignerOptions(true, salonsByRegion());
    });

    populateSelect(citySelect, cities, "전체 시", null, null, citySelect.value || "");
    syncDistricts(false);
}

function bindReviewRatingStars() {
    var stars = document.querySelectorAll(".review-rating__star");
    var ratingInput = document.querySelector('input[name="rating"]');

    if (!stars.length || !ratingInput) return;

    function renderStars(value) {
        stars.forEach(function (star, index) {
            star.classList.toggle("is-active", index < value);
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
        counter.textContent = "현재 " + textarea.value.length + " / " + REVIEW_CONTENT_MAX_LENGTH + "자";
        counter.classList.toggle("is-error", textarea.value.length > REVIEW_CONTENT_MAX_LENGTH);
    }

    textarea.addEventListener("input", updateCounter);
    updateCounter();
}

function bindReviewFormSubmit() {
    var form = document.getElementById("reviewForm");
    if (!form) return;

    form.addEventListener("submit", async function (event) {
        event.preventDefault();

        var mode = form.getAttribute("data-mode") || "create";
        var rating = document.getElementById("rating");
        var content = document.getElementById("content");

        if (!rating || !content) {
            alert("리뷰 입력 구성이 올바르지 않습니다.");
            return;
        }

        if (!content.value.trim()) {
            alert("리뷰 내용을 입력해주세요.");
            content.focus();
            return;
        }

        if (content.value.trim().length > REVIEW_CONTENT_MAX_LENGTH) {
            alert("리뷰 내용은 " + REVIEW_CONTENT_MAX_LENGTH.toLocaleString() + "자 이하로 입력해주세요.");
            content.focus();
            return;
        }

        if (mode === "edit") {
            await submitReviewUpdate(form, rating, content);
            return;
        }

        await submitReviewCreate(rating, content);
    });
}

async function submitReviewCreate(rating, content) {
    var reservationId = document.getElementById("reservationId");
    var image = document.getElementById("image");

    if (!reservationId || !reservationId.value) {
        alert("예약 정보가 없습니다. 예약 목록에서 다시 진입해주세요.");
        return;
    }

    if (image && image.files && image.files.length > 0 && image.files[0].size > REVIEW_IMAGE_MAX_SIZE) {
        alert("리뷰 이미지는 5MB 이하만 업로드할 수 있습니다.");
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
            alert("리뷰 등록에 실패했습니다.\n" + await reviewResponse.text());
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
                alert("리뷰는 등록됐지만 이미지 업로드에 실패했습니다.\n" + await imageResponse.text());
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
}

async function submitReviewUpdate(form, rating, content) {
    var reviewId = document.getElementById("reviewId");
    if (!reviewId || !reviewId.value) {
        alert("리뷰 정보가 올바르지 않습니다.");
        return;
    }

    var requestBody = {
        rating: Number(rating.value),
        content: content.value.trim()
    };

    var headers = {
        "Content-Type": "application/json"
    };
    applyCsrfHeaders(headers);

    try {
        var response = await fetch("/api/reviews/" + reviewId.value, {
            method: "PUT",
            headers: headers,
            body: JSON.stringify(requestBody)
        });

        if (!response.ok) {
            alert("리뷰 수정에 실패했습니다.\n" + await response.text());
            return;
        }

        alert("리뷰가 수정되었습니다.");
        window.location.href = "/reviews/" + reviewId.value;
    } catch (error) {
        console.error(error);
        alert("리뷰 수정 중 오류가 발생했습니다.");
    }
}

function bindReviewDeleteButton() {
    var deleteButton = document.querySelector("[data-review-delete]");
    if (!deleteButton) return;

    deleteButton.addEventListener("click", async function () {
        var reviewId = deleteButton.getAttribute("data-review-id");
        if (!reviewId) return;

        if (!window.confirm("이 리뷰를 삭제하시겠습니까?")) {
            return;
        }

        var headers = {};
        applyCsrfHeaders(headers);

        try {
            var response = await fetch("/api/reviews/" + reviewId, {
                method: "DELETE",
                headers: headers
            });

            if (!response.ok) {
                alert("리뷰 삭제에 실패했습니다.\n" + await response.text());
                return;
            }

            alert("리뷰가 삭제되었습니다.");
            window.location.href = "/reviews";
        } catch (error) {
            console.error(error);
            alert("리뷰 삭제 중 오류가 발생했습니다.");
        }
    });
}

function bindReviewLikeButtons() {
    var likeButtons = document.querySelectorAll("[data-review-like]");
    if (!likeButtons.length) return;

    likeButtons.forEach(function (button) {
        button.addEventListener("click", async function (event) {
            event.preventDefault();
            event.stopPropagation();

            if (button.disabled) {
                alert("좋아요는 로그인 후 이용할 수 있습니다.");
                return;
            }

            var reviewId = button.getAttribute("data-review-like");
            if (!reviewId) return;

            var headers = {};
            applyCsrfHeaders(headers);

            try {
                var response = await fetch("/api/reviews/" + reviewId + "/likes", {
                    method: "POST",
                    headers: headers
                });

                if (!response.ok) {
                    alert("좋아요 처리에 실패했습니다.\n" + await response.text());
                    return;
                }

                var result = await response.json();
                button.classList.toggle("is-liked", result.liked);

                var count = button.querySelector(".review-like-button__count");
                if (count) {
                    count.textContent = result.likeCount;
                }
            } catch (error) {
                console.error(error);
                alert("좋아요 처리 중 오류가 발생했습니다.");
            }
        });
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
