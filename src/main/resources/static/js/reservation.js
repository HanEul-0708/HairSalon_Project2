var reservationTimeOptionsRequest = 0;

document.addEventListener("DOMContentLoaded", function () {
    setMinimumReservationDate();
    bindReservationTimeOptionTriggers();
    buildReservationTimeOptions();
    bindReservationSelectionFilters();
    bindReservationFormSubmit();
    bindReservationCardFocusEffect();
    bindServicePriceSync();
    bindReservationCancelButtons();
    bindReservationStatusButtons();
});

function setMinimumReservationDate() {
    var dateInput = document.getElementById("reservationDate");
    if (!dateInput) return;

    dateInput.min = formatDate(new Date());
}

function bindReservationTimeOptionTriggers() {
    var dateInput = document.getElementById("reservationDate");
    var designerSelect = document.getElementById("designerId");

    if (dateInput) {
        dateInput.addEventListener("change", buildReservationTimeOptions);
    }
    if (designerSelect) {
        designerSelect.addEventListener("change", buildReservationTimeOptions);
    }
}

async function buildReservationTimeOptions() {
    var currentRequest = ++reservationTimeOptionsRequest;
    var dateInput = document.getElementById("reservationDate");
    var timeSelect = document.getElementById("reservationTime");
    if (!dateInput || !timeSelect) return;

    var selectedTime = timeSelect.getAttribute("data-selected-time") || timeSelect.value || "";
    timeSelect.removeAttribute("data-selected-time");
    var minimumTime = getMinimumSelectableTime(dateInput.value);
    var candidateTimes = [];

    for (var hour = 9; hour <= 20; hour++) {
        for (var minute = 0; minute < 60; minute += 30) {
            if (hour === 20 && minute > 0) {
                break;
            }

            var timeValue = String(hour).padStart(2, "0") + ":" + String(minute).padStart(2, "0");
            if (minimumTime && timeValue < minimumTime) {
                continue;
            }

            candidateTimes.push(timeValue);
        }
    }

    var designerSelect = document.getElementById("designerId");
    var reservationIdInput = document.getElementById("reservationId");
    var designerId = designerSelect ? designerSelect.value : "";
    var reservationId = reservationIdInput ? reservationIdInput.value : "";
    var unavailableTimes = await fetchUnavailableReservationTimes(
            designerId,
            dateInput.value,
            reservationId,
            candidateTimes
    );

    if (currentRequest !== reservationTimeOptionsRequest) {
        return;
    }

    timeSelect.innerHTML = "";

    var defaultOption = document.createElement("option");
    defaultOption.value = "";
    defaultOption.textContent = "예약 시간을 선택하세요";
    timeSelect.appendChild(defaultOption);

    candidateTimes.forEach(function (timeValue) {
        var option = document.createElement("option");
        option.value = timeValue;
        option.textContent = unavailableTimes.has(timeValue) ? timeValue + " (예약 마감)" : timeValue;
        option.disabled = unavailableTimes.has(timeValue);
        timeSelect.appendChild(option);
    });

    var selectedOption = timeSelect.querySelector('option[value="' + selectedTime + '"]');
    if (selectedOption && !selectedOption.disabled) {
        timeSelect.value = selectedTime;
    } else {
        timeSelect.value = "";
    }
}

function bindReservationSelectionFilters() {
    var citySelect = document.getElementById("city");
    var districtSelect = document.getElementById("district");
    var neighborhoodSelect = document.getElementById("neighborhood");
    var salonSelect = document.getElementById("salonId");
    var designerSelect = document.getElementById("designerId");
    var serviceSelect = document.getElementById("salonServiceId");
    var likedSalonOnly = document.getElementById("likedSalonOnly");
    var likedDesignerOnly = document.getElementById("likedDesignerOnly");

    if (!citySelect || !districtSelect || !neighborhoodSelect || !salonSelect || !designerSelect || !serviceSelect) {
        return;
    }

    var salonOptions = collectOptionData(salonSelect);
    var designerOptions = collectOptionData(designerSelect);
    var serviceOptions = collectOptionData(serviceSelect);
    var initialSalonId = salonSelect.value;
    var initialSalon = salonOptions.find(function (option) {
        return option.value === initialSalonId;
    });

    function baseSalons() {
        var likedSalonMode = likedSalonOnly && likedSalonOnly.checked;
        return salonOptions.filter(function (option) {
            return !likedSalonMode || option.liked;
        });
    }

    function populateSelect(select, options, placeholderText, selectedValue) {
        select.innerHTML = "";

        var defaultOption = document.createElement("option");
        defaultOption.value = "";
        defaultOption.textContent = placeholderText;
        select.appendChild(defaultOption);

        options.forEach(function (optionData) {
            var option = document.createElement("option");
            option.value = optionData.value;
            option.textContent = optionData.text;
            if (optionData.price) {
                option.setAttribute("data-price", optionData.price);
            }
            if (optionData.salonId) {
                option.setAttribute("data-salon-id", optionData.salonId);
            }
            if (optionData.address) {
                option.setAttribute("data-address", optionData.address);
            }
            option.setAttribute("data-liked", optionData.liked ? "true" : "false");
            select.appendChild(option);
        });

        select.value = selectedValue || "";
        if (select.value !== (selectedValue || "")) {
            select.value = "";
        }
    }

    function populateTextSelect(select, values, placeholderText, selectedValue) {
        populateSelect(
            select,
            values.map(function (value) {
                return { value: value, text: value };
            }),
            placeholderText,
            selectedValue
        );
    }

    function disableSelect(select, placeholderText) {
        populateSelect(select, [], placeholderText, "");
        select.disabled = true;
    }

    function enableSelect(select) {
        select.disabled = false;
    }

    function uniqueSorted(values) {
        return Array.from(new Set(values.filter(Boolean))).sort();
    }

    function salonsByRegion() {
        var selectedCity = citySelect.value;
        var selectedDistrict = districtSelect.value;
        var selectedNeighborhood = neighborhoodSelect.value;

        return baseSalons().filter(function (salon) {
            return salon.city === selectedCity
                && salon.district === selectedDistrict
                && salon.neighborhood === selectedNeighborhood;
        });
    }

    function syncCities(resetBelow) {
        var selectedCity = resetBelow ? "" : (citySelect.value || (initialSalon ? initialSalon.city : ""));
        populateTextSelect(
            citySelect,
            uniqueSorted(baseSalons().map(function (salon) { return salon.city; })),
            "시를 선택하세요",
            selectedCity
        );
        syncDistricts(resetBelow);
    }

    function syncDistricts(resetBelow) {
        var selectedCity = citySelect.value;
        if (!selectedCity) {
            disableSelect(districtSelect, "시를 먼저 선택하세요");
            syncNeighborhoods(true);
            return;
        }

        var selectedDistrict = resetBelow ? "" : (districtSelect.value || (initialSalon && initialSalon.city === selectedCity ? initialSalon.district : ""));
        var districts = uniqueSorted(baseSalons()
            .filter(function (salon) { return salon.city === selectedCity; })
            .map(function (salon) { return salon.district; }));
        enableSelect(districtSelect);
        populateTextSelect(districtSelect, districts, "구를 선택하세요", selectedDistrict);
        syncNeighborhoods(resetBelow);
    }

    function syncNeighborhoods(resetBelow) {
        var selectedCity = citySelect.value;
        var selectedDistrict = districtSelect.value;
        if (!selectedCity || !selectedDistrict) {
            disableSelect(neighborhoodSelect, "구를 먼저 선택하세요");
            syncSalons(true);
            return;
        }

        var selectedNeighborhood = resetBelow ? "" : (neighborhoodSelect.value || (initialSalon
            && initialSalon.city === selectedCity
            && initialSalon.district === selectedDistrict ? initialSalon.neighborhood : ""));
        var neighborhoods = uniqueSorted(baseSalons()
            .filter(function (salon) {
                return salon.city === selectedCity && salon.district === selectedDistrict;
            })
            .map(function (salon) { return salon.neighborhood; }));
        enableSelect(neighborhoodSelect);
        populateTextSelect(neighborhoodSelect, neighborhoods, "동을 선택하세요", selectedNeighborhood);
        syncSalons(resetBelow);
    }

    function syncSalons(resetBelow) {
        var selectedNeighborhood = neighborhoodSelect.value;
        if (!selectedNeighborhood) {
            disableSelect(salonSelect, "동을 먼저 선택하세요");
            syncDesigners(true);
            return;
        }

        var selectedSalonId = resetBelow ? "" : salonSelect.value;
        if (!selectedSalonId && initialSalon && initialSalon.neighborhood === selectedNeighborhood) {
            selectedSalonId = initialSalon.value;
        }
        enableSelect(salonSelect);
        populateSelect(salonSelect, salonsByRegion(), "미용실을 선택하세요", selectedSalonId);
        syncDesigners(resetBelow);
    }

    function syncDesigners(resetBelow) {
        var selectedSalonId = salonSelect.value;
        var likedDesignerMode = likedDesignerOnly && likedDesignerOnly.checked;
        if (!selectedSalonId) {
            disableSelect(designerSelect, "미용실을 먼저 선택하세요");
            syncServices(true);
            return;
        }

        enableSelect(designerSelect);
        populateSelect(
            designerSelect,
            designerOptions.filter(function (option) {
                return option.salonId === selectedSalonId
                    && (!likedDesignerMode || option.liked);
            }),
            "디자이너를 선택하세요",
            resetBelow ? "" : designerSelect.value
        );
        syncServices(resetBelow);
    }

    function syncServices(resetBelow) {
        var selectedSalonId = salonSelect.value;
        if (!selectedSalonId) {
            disableSelect(serviceSelect, "미용실을 먼저 선택하세요");
            serviceSelect.dispatchEvent(new Event("change"));
            buildReservationTimeOptions();
            return;
        }

        enableSelect(serviceSelect);
        populateSelect(
            serviceSelect,
            serviceOptions.filter(function (option) {
                return option.salonId === selectedSalonId;
            }),
            "시술을 선택하세요",
            resetBelow ? "" : serviceSelect.value
        );
        serviceSelect.dispatchEvent(new Event("change"));
        buildReservationTimeOptions();
    }

    citySelect.addEventListener("change", function () {
        initialSalon = null;
        syncDistricts(true);
    });
    districtSelect.addEventListener("change", function () {
        initialSalon = null;
        syncNeighborhoods(true);
    });
    neighborhoodSelect.addEventListener("change", function () {
        initialSalon = null;
        syncSalons(true);
    });
    salonSelect.addEventListener("change", function () {
        initialSalon = null;
        syncDesigners(true);
    });
    if (likedSalonOnly) {
        likedSalonOnly.addEventListener("change", function () {
            initialSalon = null;
            syncCities(false);
        });
    }
    if (likedDesignerOnly) {
        likedDesignerOnly.addEventListener("change", function () {
            syncDesigners(false);
        });
    }

    syncCities(false);
}

function collectOptionData(select) {
    return Array.from(select.options)
        .filter(function (option) {
            return option.value !== "";
        })
        .map(function (option) {
            var address = option.getAttribute("data-address") || "";
            var regionParts = parseAddressRegion(address);
            return {
                value: option.value,
                text: option.textContent,
                price: option.getAttribute("data-price"),
                salonId: option.getAttribute("data-salon-id"),
                address: address,
                city: regionParts.city,
                district: regionParts.district,
                neighborhood: regionParts.neighborhood,
                liked: option.getAttribute("data-liked") === "true"
            };
        });
}

function parseAddressRegion(address) {
    var tokens = (address || "").trim().split(/\s+/).filter(Boolean);
    return {
        city: tokens[0] || "",
        district: tokens[1] || "",
        neighborhood: normalizeNeighborhood(tokens[2] || "")
    };
}

function normalizeNeighborhood(value) {
    if (!value) {
        return "";
    }

    if (/[동가읍면리]$/.test(value)) {
        return value;
    }

    return "";
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
    var nextQuarter = Math.ceil(minutes / 30) * 30;

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

async function fetchUnavailableReservationTimes(designerId, reservationDate, reservationId, candidateTimes) {
    if (!designerId || !reservationDate || !candidateTimes.length) {
        return new Set();
    }

    var params = new URLSearchParams({
        designerId: designerId,
        reservationDate: reservationDate
    });
    if (reservationId) {
        params.append("reservationId", reservationId);
    }

    try {
        var response = await fetch("/api/reservations/unavailable-times?" + params.toString());
        if (!response.ok) {
            return new Set();
        }

        var data = await response.json();
        return new Set(data.unavailableTimes || []);
    } catch (error) {
        console.error(error);
        return new Set();
    }
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

function bindReservationStatusButtons() {
    var buttons = document.querySelectorAll("[data-reservation-status]");
    if (!buttons.length) return;

    buttons.forEach(function (button) {
        button.addEventListener("click", async function () {
            var reservationId = button.getAttribute("data-reservation-id");
            var status = button.getAttribute("data-reservation-status-value");
            if (!reservationId || !status) {
                return;
            }

            if (!window.confirm(status === "COMPLETED" ? "방문 완료로 처리하시겠습니까?" : "예약 상태를 변경하시겠습니까?")) {
                return;
            }

            var headers = {
                "Content-Type": "application/json"
            };
            applyCsrfHeaders(headers);

            try {
                var response = await fetch("/api/reservations/" + reservationId + "/status", {
                    method: "PATCH",
                    headers: headers,
                    body: JSON.stringify({ status: status })
                });

                if (!response.ok) {
                    var errorText = await response.text();
                    alert("예약 상태 변경에 실패했습니다.\n" + errorText);
                    return;
                }

                alert(status === "COMPLETED" ? "방문 완료로 처리되었습니다." : "예약 상태가 변경되었습니다.");
                window.location.reload();
            } catch (error) {
                console.error(error);
                alert("예약 상태 변경 중 오류가 발생했습니다.");
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
