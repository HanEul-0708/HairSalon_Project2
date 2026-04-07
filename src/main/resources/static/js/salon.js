document.addEventListener("DOMContentLoaded", function () {
    bindSalonSearchForms();
    bindSalonCardFocusEffect();
    bindSalonMap(0);
});

function bindSalonSearchForms() {
    var forms = document.querySelectorAll(".salon-search-form");
    if (!forms.length) return;

    forms.forEach(function (form) {
        var inputs = form.querySelectorAll("input, select");
        inputs.forEach(function (input) {
            input.addEventListener("keypress", function (event) {
                if (event.key === "Enter") {
                    event.preventDefault();
                    form.submit();
                }
            });
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

function bindSalonMap(attempt) {
    var mapElement = document.getElementById("salon-map");
    if (!mapElement) return;
    if (mapElement.dataset.mapInitialized === "true") return;

    var statusElement = document.querySelector("[data-map-status]");
    if (mapElement.dataset.kakaoEnabled !== "true") {
        if (statusElement) {
            statusElement.textContent = "카카오 JavaScript 키가 설정되지 않아 지도를 표시할 수 없습니다.";
        }
        return;
    }

    if (!window.kakao || !window.kakao.maps || typeof window.kakao.maps.load !== "function") {
        if (attempt < 20) {
            if (statusElement) {
                statusElement.textContent = "카카오 지도 SDK를 불러오는 중입니다.";
            }
            window.setTimeout(function () {
                bindSalonMap(attempt + 1);
            }, 250);
            return;
        }

        if (statusElement) {
            statusElement.textContent = "카카오 지도 SDK를 불러오지 못했습니다.";
        }
        return;
    }

    window.kakao.maps.load(function () {
        mapElement.dataset.mapInitialized = "true";

        var centerLatitude = parseFloat(mapElement.dataset.centerLatitude || "37.5665");
        var centerLongitude = parseFloat(mapElement.dataset.centerLongitude || "126.9780");
        var defaultZoom = parseInt(mapElement.dataset.defaultZoom || "11", 10);

        var map = new window.kakao.maps.Map(mapElement, {
            center: new window.kakao.maps.LatLng(centerLatitude, centerLongitude),
            level: normalizeKakaoLevel(defaultZoom)
        });

        map.addControl(
            new window.kakao.maps.MapTypeControl(),
            window.kakao.maps.ControlPosition.TOPRIGHT
        );
        map.addControl(
            new window.kakao.maps.ZoomControl(),
            window.kakao.maps.ControlPosition.RIGHT
        );

        var markerElements = Array.from(document.querySelectorAll(".js-salon-marker"));
        var bounds = new window.kakao.maps.LatLngBounds();
        var markerEntries = [];

        markerElements.forEach(function (element) {
            var latitude = parseFloat(element.dataset.latitude);
            var longitude = parseFloat(element.dataset.longitude);
            var name = normalizeDataValue(element.dataset.name);
            var address = normalizeDataValue(element.dataset.address);
            var rating = normalizeDataValue(element.dataset.rating);
            var distance = normalizeDataValue(element.dataset.distance);
            var detailUrl = normalizeDataValue(element.dataset.detailUrl);

            if (Number.isNaN(latitude) || Number.isNaN(longitude)) {
                return;
            }

            var position = new window.kakao.maps.LatLng(latitude, longitude);
            var marker = new window.kakao.maps.Marker({
                map: map,
                position: position,
                title: name
            });

            var popupLines = [
                "<div class=\"salon-map-infowindow\">",
                "<strong>" + escapeHtml(name || "") + "</strong>"
            ];
            if (address) {
                popupLines.push("<div>" + escapeHtml(address) + "</div>");
            }
            if (rating) {
                popupLines.push("<div>평점 " + escapeHtml(rating) + "</div>");
            }
            if (distance) {
                popupLines.push("<div>거리 " + escapeHtml(distance) + " km</div>");
            }
            if (detailUrl) {
                popupLines.push("<div><a href=\"" + escapeHtml(detailUrl) + "\">상세 보기</a></div>");
            }
            popupLines.push("</div>");

            var infowindow = new window.kakao.maps.InfoWindow({
                content: popupLines.join("")
            });

            bounds.extend(position);
            markerEntries.push({
                salonId: element.dataset.salonId,
                marker: marker,
                infowindow: infowindow,
                element: element,
                position: position
            });

            window.kakao.maps.event.addListener(marker, "click", function () {
                focusSalonMarker(map, markerEntries, element.dataset.salonId);
            });

            element.addEventListener("click", function () {
                focusSalonMarker(map, markerEntries, element.dataset.salonId);
            });
        });

        if (markerEntries.length > 1) {
            map.setBounds(bounds);
        } else if (markerEntries.length === 1) {
            map.setCenter(markerEntries[0].position);
            map.setLevel(4);
        }

        if (statusElement) {
            statusElement.textContent = markerEntries.length
                ? "카카오 지도에서 미용실 위치를 확인하고 목록에서 상세 정보를 열 수 있습니다."
                : "좌표가 등록된 미용실만 카카오 지도에 표시됩니다.";
        }
    });
}

function focusSalonMarker(map, markerEntries, salonId) {
    markerEntries.forEach(function (entry) {
        var isActive = entry.salonId === salonId;
        entry.element.classList.toggle("is-active", isActive);

        if (isActive) {
            map.setCenter(entry.position);
            map.setLevel(4);
            entry.infowindow.open(map, entry.marker);
        } else {
            entry.infowindow.close();
        }
    });
}

function escapeHtml(value) {
    return String(value)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#39;");
}

function normalizeDataValue(value) {
    if (!value || value === "null" || value === "undefined") {
        return "";
    }
    return value;
}

function normalizeKakaoLevel(defaultZoom) {
    if (defaultZoom >= 14) return 3;
    if (defaultZoom >= 13) return 4;
    if (defaultZoom >= 12) return 5;
    if (defaultZoom >= 11) return 6;
    if (defaultZoom >= 10) return 7;
    return 8;
}
