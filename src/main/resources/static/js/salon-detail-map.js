document.addEventListener("DOMContentLoaded", function () {
    var mapWrap = document.querySelector(".salon-detail__map-wrap");
    if (!mapWrap) {
        return;
    }

    var notice = document.getElementById("salonDetailMapNotice");
    var mapContainer = document.getElementById("salonDetailMap");
    var hasKey = mapWrap.dataset.hasKey === "true";
    var latitude = parseFloat(mapWrap.dataset.latitude || "");
    var longitude = parseFloat(mapWrap.dataset.longitude || "");
    var salonName = mapWrap.dataset.name || "미용실";
    var address = mapWrap.dataset.address || "";

    function showNotice(message) {
        if (!notice) {
            return;
        }
        notice.hidden = false;
        notice.textContent = message;
    }

    function escapeHtml(value) {
        return String(value)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/\"/g, "&quot;")
            .replace(/'/g, "&#39;");
    }

    function renderMap() {
        if (!mapContainer) {
            return;
        }

        if (Number.isNaN(latitude) || Number.isNaN(longitude)) {
            showNotice("미용실 위치 정보가 없어 지도를 표시할 수 없습니다.");
            return;
        }

        if (typeof window.kakao === "undefined" || !window.kakao.maps) {
            showNotice("카카오맵을 불러오지 못했습니다.");
            return;
        }

        var position = new window.kakao.maps.LatLng(latitude, longitude);
        var map = new window.kakao.maps.Map(mapContainer, {
            center: position,
            level: 3
        });

        var marker = new window.kakao.maps.Marker({
            position: position,
            map: map
        });

        var infoWindow = new window.kakao.maps.InfoWindow({
            content:
                '<div class="salon-map-infowindow"><strong>' +
                escapeHtml(salonName) +
                "</strong><br>" +
                escapeHtml(address) +
                "</div>"
        });

        infoWindow.open(map, marker);
    }

    if (!hasKey) {
        showNotice("카카오맵 JavaScript 키가 없어 지도를 표시할 수 없습니다.");
        return;
    }

    if (typeof window.kakao !== "undefined" && window.kakao.maps && typeof window.kakao.maps.load === "function") {
        window.kakao.maps.load(renderMap);
        return;
    }

    renderMap();
});
