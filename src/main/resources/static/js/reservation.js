document.addEventListener("DOMContentLoaded", function () {
    setMinimumReservationDate();
    buildReservationTimeOptions();
    bindReservationSelectionFilters();
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

function bindReservationSelectionFilters() {
    var salonSelect = document.getElementById("salonId");
    var designerSelect = document.getElementById("designerId");
    var serviceSelect = document.getElementById("salonServiceId");
    var likedSalonOnly = document.getElementById("likedSalonOnly");
    var likedDesignerOnly = document.getElementById("likedDesignerOnly");

    if (!salonSelect || !designerSelect || !serviceSelect) {
        return;
    }

    var salonOptions = collectOptionData(salonSelect);
    var designerOptions = collectOptionData(designerSelect);
    var serviceOptions = collectOptionData(serviceSelect);

    function applyFilters() {
        var selectedSalonId = salonSelect.value;
        var likedSalonMode = likedSalonOnly && likedSalonOnly.checked;
        var likedDesignerMode = likedDesignerOnly && likedDesignerOnly.checked;

        rebuildOptions(
            salonSelect,
            salonOptions,
            "미용실을 선택하세요",
            function (option) {
                return !likedSalonMode || option.liked;
            }
        );

        if (selectedSalonId && !optionExists(salonSelect, selectedSalonId)) {
            salonSelect.value = "";
            selectedSalonId = "";
        } else if (selectedSalonId) {
            salonSelect.value = selectedSalonId;
        }

        var currentDesignerId = designerSelect.value;
        rebuildOptions(
            designerSelect,
            designerOptions,
            "디자이너를 선택하세요",
            function (option) {
                var matchesSalon = !selectedSalonId || option.salonId === selectedSalonId;
                var matchesLiked = !likedDesignerMode || option.liked;
                return matchesSalon && matchesLiked;
            }
        );
        if (currentDesignerId && optionExists(designerSelect, currentDesignerId)) {
            designerSelect.value = currentDesignerId;
        }

        var currentServiceId = serviceSelect.value;
        rebuildOptions(
            serviceSelect,
            serviceOptions,
            "시술을 선택하세요",
            function (option) {
                return !selectedSalonId || option.salonId === selectedSalonId;
            }
        );
        if (currentServiceId && optionExists(serviceSelect, currentServiceId)) {
            serviceSelect.value = currentServiceId;
        }

        serviceSelect.dispatchEvent(new Event("change"));
    }

    salonSelect.addEventListener("change", applyFilters);
    if (likedSalonOnly) {
        likedSalonOnly.addEventListener("change", applyFilters);
    }
    if (likedDesignerOnly) {
        likedDesignerOnly.addEventListener("change", applyFilters);
    }

    applyFilters();
}

function collectOptionData(select) {
    return Array.from(select.options)
        .filter(function (option) {
            return option.value !== "";
        })
        .map(function (option) {
            return {
                value: option.value,
                text: option.textContent,
                price: option.getAttribute("data-price"),
                salonId: option.getAttribute("data-salon-id"),
                liked: option.getAttribute("data-liked") === "true"
            };
        });
}

function rebuildOptions(select, options, placeholderText, filterFn) {
    var previousValue = select.value;
    select.innerHTML = "";

    var defaultOption = document.createElement("option");
    defaultOption.value = "";
    defaultOption.textContent = placeholderText;
    select.appendChild(defaultOption);

    options.filter(filterFn).forEach(function (optionData) {
        var option = document.createElement("option");
        option.value = optionData.value;
        option.textContent = optionData.text;
        if (optionData.price) {
            option.setAttribute("data-price", optionData.price);
        }
        if (optionData.salonId) {
            option.setAttribute("data-salon-id", optionData.salonId);
        }
        option.setAttribute("data-liked", optionData.liked ? "true" : "false");
        select.appendChild(option);
    });

    if (previousValue && optionExists(select, previousValue)) {
        select.value = previousValue;
    }
}

function optionExists(select, value) {
    return Array.from(select.options).some(function (option) {
        return option.value === value;
    });
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
            alert("예약 화면 구성이 올바르지 않습니다.");
            return;
        }

        if (!designerId.value || !salonServiceId.value || !reservationDate.value || !reservationTime.value || !paymentMethod.value) {
            alert("디자이너, 시술, 날짜, 시간, 결제 수단을 모두 선택해주세요.");
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

        if (mode === "create") {
            requestBody.memberId = memberId.value;
        }

        var headers = {
            "Content-Type": "application/json"
        };
        applyCsrfHeaders(headers);

        var url = mode === "edit" ? "/api/reservations/" + reservationId.value : "/api/reservations";
        var method = mode === "edit" ? "PUT" : "POST";

        try {
            var response = await fetch(url, {
                method: method,
                headers: headers,
                body: JSON.stringify(requestBody)
            });

            if (!response.ok) {
                var errorText = await response.text();
                alert((mode === "edit" ? "예약 변경" : "예약 생성") + "에 실패했습니다.\n" + errorText);
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
                    var errorText = await response.text();
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
