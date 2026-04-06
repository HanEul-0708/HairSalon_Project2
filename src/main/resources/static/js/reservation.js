document.addEventListener("DOMContentLoaded", function () {
    setMinimumReservationDate();
    bindReservationFormSubmit();
    bindReservationCardFocusEffect();
    bindServicePriceSync();
});

function setMinimumReservationDate() {
    var dateInput = document.getElementById("reservationDate");
    if (!dateInput) return;

    var today = new Date();
    var year = today.getFullYear();
    var month = String(today.getMonth() + 1).padStart(2, "0");
    var day = String(today.getDate()).padStart(2, "0");

    dateInput.min = year + "-" + month + "-" + day;
}

function bindServicePriceSync() {
    var serviceSelect = document.getElementById("salonServiceId");
    var totalPriceInput = document.getElementById("totalPrice");

    if (!serviceSelect || !totalPriceInput) return;

    serviceSelect.addEventListener("change", function () {
        var selectedOption = serviceSelect.options[serviceSelect.selectedIndex];
        var price = selectedOption ? selectedOption.getAttribute("data-price") : "0";
        totalPriceInput.value = price || "0";
    });
}

function bindReservationFormSubmit() {
    var form = document.getElementById("reservationForm");
    if (!form) return;

    form.addEventListener("submit", async function (event) {
        event.preventDefault();

        var memberId = document.getElementById("memberId");
        var designerId = document.getElementById("designerId");
        var salonServiceId = document.getElementById("salonServiceId");
        var reservationDate = document.getElementById("reservationDate");
        var reservationTime = document.getElementById("reservationTime");
        var totalPrice = document.getElementById("totalPrice");

        if (!memberId || !designerId || !salonServiceId || !reservationDate || !reservationTime || !totalPrice) {
            alert("예약 폼 구성이 올바르지 않습니다.");
            return;
        }

        if (!designerId.value || !salonServiceId.value || !reservationDate.value || !reservationTime.value) {
            alert("디자이너, 시술, 날짜, 시간을 모두 선택해주세요.");
            return;
        }

        var requestBody = {
            memberId: memberId.value,
            designerId: Number(designerId.value),
            salonServiceId: Number(salonServiceId.value),
            reservationDate: reservationDate.value,
            reservationTime: reservationTime.value,
            totalPrice: Number(totalPrice.value)
        };

        var headers = {
            "Content-Type": "application/json"
        };
        applyCsrfHeaders(headers);

        try {
            var response = await fetch("/api/reservations", {
                method: "POST",
                headers: headers,
                body: JSON.stringify(requestBody)
            });

            if (!response.ok) {
                var errorText = await response.text();
                alert("예약 생성에 실패했습니다.\n" + errorText);
                return;
            }

            alert("예약이 완료되었습니다.");
            window.location.href = "/reservations";
        } catch (error) {
            console.error(error);
            alert("예약 요청 중 오류가 발생했습니다.");
        }
    });
}

function bindReservationCardFocusEffect() {
    var actionButtons = document.querySelectorAll(".reservation-actions .btn");
    if (!actionButtons.length) return;

    actionButtons.forEach(function (button) {
        button.addEventListener("focus", function () {
            var card = button.closest(".reservation-card");
            if (card) {
                card.style.transform = "translateY(-3px)";
                card.style.boxShadow = "var(--shadow-md)";
            }
        });

        button.addEventListener("blur", function () {
            var card = button.closest(".reservation-card");
            if (card) {
                card.style.transform = "";
                card.style.boxShadow = "";
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
