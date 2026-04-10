document.addEventListener("DOMContentLoaded", function () {
    var page = document.querySelector(".salon-map-page__layout");
    if (!page) {
        return;
    }

    var hasKey = page.dataset.hasKey === "true";
    var hasRestKey = page.dataset.hasRestKey === "true";
    var regionInput = document.getElementById("mapRegion");
    var keywordInput = document.getElementById("mapKeyword");
    var searchButton = document.getElementById("mapSearchButton");
    var myLocationButton = document.getElementById("myLocationButton");
    var resetButton = document.getElementById("mapResetButton");
    var resultsBox = document.getElementById("mapResults");
    var noticeBox = document.getElementById("mapNotice");

    function showNotice(message) {
        if (!noticeBox) {
            return;
        }
        noticeBox.hidden = false;
        noticeBox.textContent = message;
    }

    if (!hasKey) {
        showNotice("카카오 JavaScript 키가 비어 있습니다. application-secret.properties 설정을 확인하세요.");
        return;
    }

    if (!hasRestKey) {
        showNotice("카카오 REST API 키가 비어 있습니다. application-secret.properties의 kakao.rest-api-key 설정을 확인하세요.");
        return;
    }

    if (typeof kakao === "undefined" || !kakao.maps) {
        showNotice("카카오 지도 SDK를 불러오지 못했습니다. JavaScript 키와 Web 플랫폼 도메인에 http://localhost:8081, http://127.0.0.1:8081 이 등록되어 있는지 확인하세요.");
        return;
    }

    var mapContainer = document.getElementById("salonMapCanvas");
    var map = new kakao.maps.Map(mapContainer, {
        center: new kakao.maps.LatLng(37.5665, 126.9780),
        level: 5
    });
    var bounds = new kakao.maps.LatLngBounds();
    var infoWindow = new kakao.maps.InfoWindow({ zIndex: 1 });
    var markers = [];

    /* 현재 사용자 위치 마커를 따로 관리하는 변수 */
    var myLocationMarker = null;

    function clearMarkers() {
        markers.forEach(function (marker) {
            marker.setMap(null);
        });
        markers = [];
        bounds = new kakao.maps.LatLngBounds();
    }

    function clearMyLocationMarker() {
        if (myLocationMarker) {
            myLocationMarker.setMap(null);
            myLocationMarker = null;
        }
    }

    function setMyLocationMarker(latitude, longitude) {
        clearMyLocationMarker();

        var position = new kakao.maps.LatLng(latitude, longitude);

        myLocationMarker = new kakao.maps.Marker({
            position: position
        });

        myLocationMarker.setMap(map);

        var myLocationInfoWindow = new kakao.maps.InfoWindow({
            content: '<div class="salon-map-infowindow"><strong>내 위치</strong></div>'
        });

        kakao.maps.event.addListener(myLocationMarker, "click", function () {
            myLocationInfoWindow.open(map, myLocationMarker);
        });
    }

    function buildQuery() {
        var region = (regionInput.value || "").trim();
        var keyword = (keywordInput.value || "").trim();

        var params = new URLSearchParams();
        if (keyword) {
            params.set("keyword", keyword);
        }
        if (region) {
            params.set("region", region);
        }
        return params.toString();
    }

    function renderEmpty(message) {
        resultsBox.innerHTML = '<div class="salon-map-results__empty">' + message + "</div>";
    }

    function renderResults(data) {
        if (!data || !data.length) {
            renderEmpty("검색 결과가 없습니다.");
            return;
        }

        resultsBox.innerHTML = "";

        data.forEach(function (place, index) {
            if (!place || place.latitude == null || place.longitude == null) {
                return;
            }

            var position = new kakao.maps.LatLng(place.latitude, place.longitude);
            var marker = new kakao.maps.Marker({
                map: map,
                position: position
            });

            markers.push(marker);
            bounds.extend(position);

            var item = document.createElement("div");
            item.className = "salon-map-result";
            item.tabIndex = 0;
            item.innerHTML =
                '<strong class="salon-map-result__name">' + (place.name || "미용실") + "</strong>" +
                '<span class="salon-map-result__meta">' + (place.roadAddress || place.address || "주소 정보 없음") + "</span>" +
                (place.salonId != null
                    ? '<div class="salon-map-result__actions"><a class="btn btn-outline btn-sm" href="/salons/' + place.salonId + '">상세보기</a></div>'
                    : "");

            item.addEventListener("click", function () {
                map.setCenter(position);
                infoWindow.setContent(
                    '<div class="salon-map-infowindow"><strong>' + (place.name || "") + "</strong><br>" +
                    (place.roadAddress || place.address || "") + "</div>"
                );
                infoWindow.open(map, marker);
            });

            item.addEventListener("keydown", function (event) {
                if (event.key === "Enter" || event.key === " ") {
                    event.preventDefault();
                    item.click();
                }
            });

            kakao.maps.event.addListener(marker, "click", function () {
                item.click();
            });

            resultsBox.appendChild(item);

            if (index === 0) {
                item.click();
            }
        });

        map.setBounds(bounds);
    }

    function searchPlaces() {
        var query = buildQuery();
        if (!query) {
            renderEmpty("지역이나 키워드를 입력하세요.");
            return;
        }

        clearMarkers();
        renderEmpty("검색 중입니다...");

        fetch("/salons/map/results?" + query, { headers: { "Accept": "application/json" } })
            .then(function (response) {
                if (!response.ok) {
                    return response.text().then(function (text) {
                        throw new Error(text || ("HTTP " + response.status));
                    });
                }
                return response.json();
            })
            .then(function (payload) {
                if (payload && payload.error) {
                    showNotice(payload.error);
                }

                var results = payload && payload.results ? payload.results : [];
                var debug = payload && payload.debug ? payload.debug : null;

                if (results && results.length) {
                    renderResults(results);
                    return;
                }

                if (debug) {
                    renderEmpty(debug);
                    return;
                }

                renderResults([]);
            })
            .catch(function (error) {
                renderEmpty("검색을 처리할 수 없습니다. (카카오 키 설정을 확인하세요.)");
                if (error && error.message) {
                    showNotice(error.message);
                }
            });
    }

    function moveToMyLocationAndSearch() {
        if (!navigator.geolocation) {
            showNotice("이 브라우저에서는 위치 정보를 지원하지 않습니다.");
            renderEmpty("현재 위치를 사용할 수 없습니다.");
            return;
        }

        renderEmpty("현재 위치를 확인하는 중입니다...");

        navigator.geolocation.getCurrentPosition(
            function (position) {
                var latitude = position.coords.latitude;
                var longitude = position.coords.longitude;

                var currentPosition = new kakao.maps.LatLng(latitude, longitude);

                /* 지도 중심을 현재 위치로 이동 */
                map.setCenter(currentPosition);
                map.setLevel(4);

                /* 내 위치 마커 표시 */
                setMyLocationMarker(latitude, longitude);

                /*
                 * 현재 위치 기준으로 카카오 장소 검색을 수행한다.
                 * 키워드가 비어 있으면 기본값으로 "미용실"을 사용한다.
                 */
                var keyword = (keywordInput.value || "").trim();
                if (!keyword) {
                    keyword = "미용실";
                    keywordInput.value = keyword;
                }

                clearMarkers();
                renderEmpty("내 위치 기준으로 주변 미용실을 검색하는 중입니다...");

                var places = new kakao.maps.services.Places();

                places.keywordSearch(
                    keyword,
                    function (data, status) {
                        if (status === kakao.maps.services.Status.OK) {
                            var mappedResults = data.map(function (place) {
                                return {
                                    salonId: null,
                                    name: place.place_name,
                                    address: place.address_name,
                                    roadAddress: place.road_address_name,
                                    latitude: parseFloat(place.y),
                                    longitude: parseFloat(place.x)
                                };
                            });

                            renderResults(mappedResults);
                            return;
                        }

                        if (status === kakao.maps.services.Status.ZERO_RESULT) {
                            renderEmpty("내 주변에서 검색 결과가 없습니다.");
                            return;
                        }

                        renderEmpty("내 위치 기준 검색 중 오류가 발생했습니다.");
                        showNotice("현재 위치 기반 검색에 실패했습니다.");
                    },
                    {
                        location: currentPosition,
                        radius: 3000,
                        sort: kakao.maps.services.SortBy.DISTANCE
                    }
                );
            },
            function (error) {
                switch (error.code) {
                    case error.PERMISSION_DENIED:
                        showNotice("위치 권한이 거부되었습니다. 브라우저에서 위치 허용 후 다시 시도하세요.");
                        break;
                    case error.POSITION_UNAVAILABLE:
                        showNotice("현재 위치를 확인할 수 없습니다.");
                        break;
                    case error.TIMEOUT:
                        showNotice("위치 확인 시간이 초과되었습니다.");
                        break;
                    default:
                        showNotice("위치 정보를 가져오지 못했습니다.");
                        break;
                }

                renderEmpty("현재 위치를 사용할 수 없습니다.");
            },
            {
                enableHighAccuracy: true,
                timeout: 10000,
                maximumAge: 0
            }
        );
    }

    searchButton.addEventListener("click", searchPlaces);

    if (myLocationButton) {
        myLocationButton.addEventListener("click", moveToMyLocationAndSearch);
    }

    resetButton.addEventListener("click", function () {
        regionInput.value = "";
        keywordInput.value = "";
        clearMarkers();
        clearMyLocationMarker();
        renderEmpty("검색 결과가 여기에 표시됩니다.");
        map.setCenter(new kakao.maps.LatLng(37.5665, 126.9780));
        map.setLevel(5);
    });

    [regionInput, keywordInput].forEach(function (input) {
        input.addEventListener("keydown", function (event) {
            if (event.key === "Enter") {
                event.preventDefault();
                searchPlaces();
            }
        });
    });

    renderEmpty("지역이나 키워드를 입력하면 지도에서 바로 검색합니다.");
});