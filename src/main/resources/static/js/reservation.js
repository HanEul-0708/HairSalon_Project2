document.addEventListener("DOMContentLoaded", function () {
    setMinimumReservationDate();
    buildReservationTimeOptions();
    bindReservationFormSubmit();
    bindReservationCardFocusEffect();
    bindServicePriceSync();
    bindReservationCancelButtons();
});

function setMinimumReservationDate() {
    var dateInput = document.getElementById("reservationDate");
    if (!dateInput) return;

    dateInput.min = formatDate(new Date());
    dateInput.addEventListener("change", buildReservationTimeOptions);
}

function buildReservationTimeOptions() {
    var dateInput = document.getElementById("reservationDate");
    var timeSelect = document.getElementById("reservationTime");
    if (!dateInput || !timeSelect) return;

    var selectedTime = timeSelect.getAttribute("data-selected-time") || timeSelect.value || "";
    var minimumTime = getMinimumSelectableTime(dateInput.value);

    timeSelect.innerHTML = "";

    var defaultOption = document.createElement("option");
    defaultOption.value = "";
    defaultOption.textContent = "예약 시간을 선택하세요";
    timeSelect.appendChild(defaultOption);

    for (var hour = 9; hour <= 20; hour++) {
        for (var minute = 0; minute < 60; minute += 15) {
            if (hour === 20 && minute > 0) {
                break;
            }

            var timeValue = String(hour).padStart(2, "0") + ":" + String(minute).padStart(2, "0");
            if (minimumTime && timeValue < minimumTime) {
                continue;
            }

            var option = document.createElement("option");
            option.value = timeValue;
            option.textContent = timeValue;
            timeSelect.appendChild(option);
        }
    }

    if (selectedTime && (!minimumTime || selectedTime >= minimumTime)) {
        timeSelect.value = selectedTime;
    } else {
        timeSelect.value = "";
    }
}

function getMinimumSelectableTime(selectedDate) {
    if (!selectedDate) {
        return null;
    }

    var now = new Date();
    if (selectedDate !== formatDate(now)) {
        return null;
    }

    var rounded = new Date(now.getTime());
    rounded.setSeconds(0, 0);

    var minutes = rounded.getMinutes();
    var nextQuarter = Math.ceil(minutes / 15) * 15;

    if (nextQuarter === 60) {
        rounded.setHours(rounded.getHours() + 1);
        rounded.setMinutes(0);
    } else {
        rounded.setMinutes(nextQuarter);
    }

    if (rounded.getHours() < 9) {
        return "09:00";
    }

    if (rounded.getHours() > 20 || (rounded.getHours() === 20 && rounded.getMinutes() > 0)) {
        return "99:99";
    }

    return String(rounded.getHours()).padStart(2, "0") + ":" + String(rounded.getMinutes()).padStart(2, "0");
}

function formatDate(date) {
    var year = date.getFullYear();
    var month = String(date.getMonth() + 1).padStart(2, "0");
    var day = String(date.getDate()).padStart(2, "0");
    return year + "-" + month + "-" + day;
}

function bindServicePriceSync() {
    var serviceSelect = document.getElementById("salonServiceId");
    var totalPriceInput = document.getElementById("totalPrice");
    var totalPriceDisplayInput = document.getElementById("totalPriceDisplay");

    if (!serviceSelect || !totalPriceInput || !totalPriceDisplayInput) return;

    function syncPrice() {
        var selectedOption = serviceSelect.options[serviceSelect.selectedIndex];
        var price = selectedOption ? selectedOption.getAttribute("data-price") : "0";
        totalPriceInput.value = price || "0";
        totalPriceDisplayInput.value = formatCurrency(Number(totalPriceInput.value || "0"));
    }

    serviceSelect.addEventListener("change", syncPrice);
    syncPrice();
}

function bindReservationFormSubmit() {
    var form = document.getElementById("reservationForm");
    if (!form) return;

    form.addEventListener("submit", async function (event) {
        event.preventDefault();

        var mode = form.getAttribute("data-mode") || "create";
        var reservationId = document.getElementById("reservationId");
        var memberId = document.getElementById("memberId");
        var designerId = document.getElementById("designerId");
        var salonServiceId = document.getElementById("salonServiceId");
        var reservationDate = document.getElementById("reservationDate");
        var reservationTime = document.getElementById("reservationTime");
        var totalPrice = document.getElementById("totalPrice");
        var paymentMethod = document.getElementById("paymentMethod");

        if (!designerId || !salonServiceId || !reservationDate || !reservationTime || !totalPrice || !paymentMethod) {
            alert("예약 폼 구성이 올바르지 않습니다.");
            return;
        }

        if (!designerId.value || !salonServiceId.value || !reservationDate.value || !reservationTime.value || !paymentMethod.value) {
            alert("디자이너, 시술, 날짜, 시간, 결제 수단을 모두 선택해 주세요.");
            return;
        }

        var requestBody = {
            designerId: Number(designerId.value),
            salonServiceId: Number(salonServiceId.value),
            reservationDate: reservationDate.value,
            reservationTime: reservationTime.value,
            totalPrice: Number(totalPrice.value),
            paymentMethod: paymentMethod.value
        };

        if (mode === "create" && memberId) {
            requestBody.memberId = memberId.value;
        }

        var headers = {
            "Content-Type": "application/json"
        };
        applyCsrfHeaders(headers);

        var url = mode === "edit" ? "/api/reservations/" + reservationId.value : "/api/reservations";
        var method = mode === "edit" ? "PUT" : "POST";
        var actionLabel = mode === "edit" ? "예약 변경" : "예약 생성";
        var isAvailable = await checkReservationAvailability(
            designerId.value,
            reservationDate.value,
            reservationTime.value,
            mode === "edit" ? reservationId.value : null
        );

        if (!isAvailable) {
            alert("선택하신 날짜와 시간에는 이미 해당 디자이너 예약이 있습니다. 다른 시간을 선택해 주세요.");
            reservationTime.focus();
            return;
        }

        try {
            var response = await fetch(url, {
                method: method,
                headers: headers,
                body: JSON.stringify(requestBody)
            });

            if (!response.ok) {
                var errorText = await extractErrorMessage(response);
                alert(actionLabel + "에 실패했습니다.\n" + errorText);
                return;
            }

            alert(mode === "edit" ? "예약이 변경되었습니다." : "예약이 완료되었습니다.");
            window.location.href = "/reservations";
        } catch (error) {
            console.error(error);
            alert("예약 요청 중 오류가 발생했습니다.");
        }
    });
}

function bindReservationCancelButtons() {
    var buttons = document.querySelectorAll("[data-reservation-cancel]");
    if (!buttons.length) return;

    buttons.forEach(function (button) {
        button.addEventListener("click", async function () {
            var reservationId = button.getAttribute("data-reservation-id");
            if (!reservationId) {
                return;
            }

            if (!window.confirm("예약을 취소하시겠습니까?")) {
                return;
            }

            var headers = {};
            applyCsrfHeaders(headers);

            try {
                var response = await fetch("/api/reservations/" + reservationId, {
                    method: "DELETE",
                    headers: headers
                });

                if (!response.ok) {
                    var errorText = await extractErrorMessage(response);
                    alert("예약 취소에 실패했습니다.\n" + errorText);
                    return;
                }

                alert("예약이 취소되었습니다.");
                window.location.reload();
            } catch (error) {
                console.error(error);
                alert("예약 취소 중 오류가 발생했습니다.");
            }
        });
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

function formatCurrency(value) {
    return new Intl.NumberFormat("ko-KR").format(value) + "원";
}

function applyCsrfHeaders(headers) {
    var csrfTokenMeta = document.querySelector('meta[name="_csrf"]');
    var csrfHeaderMeta = document.querySelector('meta[name="_csrf_header"]');

    if (!csrfTokenMeta || !csrfHeaderMeta) {
        return;
    }

    headers[csrfHeaderMeta.getAttribute("content")] = csrfTokenMeta.getAttribute("content");
}

async function extractErrorMessage(response) {
    try {
        var data = await response.json();
        if (data && data.message) {
            return data.message;
        }
    } catch (error) {
        console.error(error);
    }

    return await response.text();
}

async function checkReservationAvailability(designerId, reservationDate, reservationTime, reservationId) {
    var params = new URLSearchParams({
        designerId: designerId,
        reservationDate: reservationDate,
        reservationTime: reservationTime
    });

    if (reservationId) {
        params.append("reservationId", reservationId);
    }

    try {
        var response = await fetch("/api/reservations/availability?" + params.toString(), {
            method: "GET"
        });

        if (!response.ok) {
            return true;
        }

        var data = await response.json();
        return Boolean(data.available);
    } catch (error) {
        console.error(error);
        return true;
    }
}
