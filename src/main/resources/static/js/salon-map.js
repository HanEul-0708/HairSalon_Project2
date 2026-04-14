document.addEventListener("DOMContentLoaded", function () {
    var page = document.querySelector(".salon-map-page__layout");
    if (!page) {
        return;
    }

    var hasKey = page.dataset.hasKey === "true";
    var hasRestKey = page.dataset.hasRestKey === "true";
    var citySelect = document.getElementById("mapCity");
    var districtSelect = document.getElementById("mapDistrict");
    var neighborhoodSelect = document.getElementById("mapNeighborhood");
    var keywordInput = document.getElementById("mapKeyword");
    var searchButton = document.getElementById("mapSearchButton");
    var myLocationButton = document.getElementById("myLocationButton");
    var resetButton = document.getElementById("mapResetButton");
    var resultsBox = document.getElementById("mapResults");
    var noticeBox = document.getElementById("mapNotice");
    var regionData = window.mapRegionFilterData;

    bindMapRegionFilters(citySelect, districtSelect, neighborhoodSelect, regionData);

    function showNotice(message) {
        if (!noticeBox) {
            return;
        }
        noticeBox.hidden = false;
        noticeBox.textContent = message;
    }

    if (!hasKey) {
        showNotice("카카오 JavaScript 키가 비어 있습니다. application-secret.properties 설정을 확인해주세요.");
        return;
    }

    if (!hasRestKey) {
        showNotice("카카오 REST API 키가 비어 있습니다. application-secret.properties 설정을 확인해주세요.");
        return;
    }

    if (typeof kakao === "undefined" || !kakao.maps) {
        showNotice("카카오 지도 SDK를 불러오지 못했습니다. JavaScript 키와 도메인 설정을 확인해주세요.");
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
    var myLocationMarker = null;
    var isMyLocationSearch = false;

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
        var city = (citySelect.value || "").trim();
        var district = (districtSelect.value || "").trim();
        var neighborhood = (neighborhoodSelect.value || "").trim();
        var keyword = (keywordInput.value || "").trim();

        var params = new URLSearchParams();
        if (keyword) {
            params.set("keyword", keyword);
        }
        if (city) {
            params.set("city", city);
        }
        if (district) {
            params.set("district", district);
        }
        if (neighborhood) {
            params.set("neighborhood", neighborhood);
        }
        return params.toString();
    }

    function renderEmpty(message) {
        resultsBox.innerHTML = '<div class="salon-map-results__empty">' + message + "</div>";
    }

    function escapeHtml(value) {
        return String(value)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/\"/g, "&quot;")
            .replace(/'/g, "&#39;");
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
            item.dataset.markerKey = place.markerKey || "";

            var detailUrl = place.detailUrl || (place.salonId != null ? "/salons/" + place.salonId : null);
            item.innerHTML =
                '<strong class="salon-map-result__name">' + escapeHtml(place.name || "미용실") + "</strong>" +
                (place.externalLabel
                    ? '<span class="salon-map-result__meta">' + escapeHtml(place.externalLabel) + " 검색 결과</span>"
                    : "") +
                '<span class="salon-map-result__meta">' + escapeHtml(place.roadAddress || place.address || "주소 정보 없음") + "</span>" +
                (place.phone
                    ? '<span class="salon-map-result__meta">' + escapeHtml(place.phone) + "</span>"
                    : "") +
                (detailUrl
                    ? '<div class="salon-map-result__actions"><a class="btn btn-outline btn-sm" href="' + escapeHtml(detailUrl) + '">상세보기</a></div>'
                    : "");

            item.addEventListener("click", function () {
                map.setCenter(position);
                infoWindow.setContent(
                    '<div class="salon-map-infowindow"><strong>' + escapeHtml(place.name || "") + "</strong><br>" +
                    escapeHtml(place.roadAddress || place.address || "") + "</div>"
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

            if (index === 0 && !isMyLocationSearch) {
                item.click();
            }
        });

        map.setBounds(bounds);
        isMyLocationSearch = false;
    }

    function searchPlaces() {
        var query = buildQuery();
        isMyLocationSearch = false;
        if (!query) {
            renderEmpty("지역이나 키워드를 선택해주세요.");
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
                renderEmpty("검색을 처리할 수 없습니다. 카카오 API 설정을 확인해주세요.");
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

                console.log("현재 위치 위도:", latitude);
                console.log("현재 위치 경도:", longitude);
                console.log("위치 정확도(m):", position.coords.accuracy);

                var currentPosition = new kakao.maps.LatLng(latitude, longitude);

                isMyLocationSearch = true;

                map.setCenter(currentPosition);
                map.setLevel(4);
                setMyLocationMarker(latitude, longitude);

                var keyword = (keywordInput.value || "").trim();
                if (!keyword) {
                    keyword = "미용실";
                    keywordInput.value = keyword;
                }

                clearMarkers();
                renderEmpty("내 위치 기준으로 주변 미용실을 검색 중입니다...");

                var places = new kakao.maps.services.Places();

                places.keywordSearch(
                    keyword,
                    function (data, status) {
                        if (status === kakao.maps.services.Status.OK) {
                            var mappedResults = data.map(function (place) {
                                return {
                                    salonId: null,
                                    markerKey: "kakao-" + place.id,
                                    name: place.place_name,
                                    address: place.address_name,
                                    roadAddress: place.road_address_name,
                                    phone: place.phone,
                                    detailUrl: place.place_url,
                                    external: true,
                                    externalLabel: "카카오",
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
                        showNotice("위치 권한이 거부되었습니다. 브라우저 설정에서 위치 허용 후 다시 시도해주세요.");
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
        citySelect.value = "";
        districtSelect.dataset.selectedValue = "";
        neighborhoodSelect.dataset.selectedValue = "";
        bindMapRegionFilters(citySelect, districtSelect, neighborhoodSelect, regionData);
        keywordInput.value = "";
        clearMarkers();
        clearMyLocationMarker();
        renderEmpty("검색 결과가 여기에 표시됩니다.");
        map.setCenter(new kakao.maps.LatLng(37.5665, 126.9780));
        map.setLevel(5);
    });

    [citySelect, districtSelect, neighborhoodSelect, keywordInput].forEach(function (input) {
        input.addEventListener("keydown", function (event) {
            if (event.key === "Enter") {
                event.preventDefault();
                searchPlaces();
            }
        });
    });

    renderEmpty("지역과 키워드를 선택하면 지도에서 바로 검색합니다.");
});

function bindMapRegionFilters(citySelect, districtSelect, neighborhoodSelect, regionData) {
    if (!citySelect || !districtSelect || !neighborhoodSelect || !regionData) {
        return;
    }

    var addresses = Array.isArray(regionData.addresses) ? regionData.addresses : [];
    var regions = parseMapRegions(addresses);

    function populateSelect(select, options, defaultLabel, selectedValue) {
        select.innerHTML = "";

        var defaultOption = document.createElement("option");
        defaultOption.value = "";
        defaultOption.textContent = defaultLabel;
        select.appendChild(defaultOption);

        options.forEach(function (value) {
            var option = document.createElement("option");
            option.value = value;
            option.textContent = value;
            select.appendChild(option);
        });

        select.value = selectedValue || "";
        if (select.value !== (selectedValue || "")) {
            select.value = "";
        }
    }

    function syncDistricts(resetNeighborhood) {
        var selectedCity = citySelect.value || "";
        var selectedDistrict = resetNeighborhood ? "" : (districtSelect.dataset.selectedValue || districtSelect.value || "");
        populateSelect(districtSelect, regions.districtsByCity[selectedCity] || [], "전체 구", selectedDistrict);
        districtSelect.dataset.selectedValue = districtSelect.value || "";
        syncNeighborhoods(resetNeighborhood);
    }

    function syncNeighborhoods(forceReset) {
        var selectedCity = citySelect.value || "";
        var selectedDistrict = districtSelect.value || "";
        var key = selectedCity + "||" + selectedDistrict;
        var selectedNeighborhood = forceReset ? "" : (neighborhoodSelect.dataset.selectedValue || neighborhoodSelect.value || "");
        populateSelect(neighborhoodSelect, regions.neighborhoodsByDistrict[key] || [], "전체 동", selectedNeighborhood);
        neighborhoodSelect.dataset.selectedValue = neighborhoodSelect.value || "";
    }

    citySelect.onchange = function () {
        districtSelect.dataset.selectedValue = "";
        neighborhoodSelect.dataset.selectedValue = "";
        syncDistricts(true);
    };

    districtSelect.onchange = function () {
        neighborhoodSelect.dataset.selectedValue = "";
        syncNeighborhoods(true);
    };

    populateSelect(citySelect, regions.cities, "전체 시", citySelect.value || "");
    syncDistricts(false);
}

function parseMapRegions(addresses) {
    var citySet = new Set();
    var districtsByCity = {};
    var neighborhoodsByDistrict = {};

    addresses.forEach(function (address) {
        if (!address) {
            return;
        }

        var tokens = String(address).trim().split(/\s+/);
        var city = tokens[0] || "";
        var district = tokens[1] || "";
        var neighborhood = isNeighborhoodName(tokens[2]) ? tokens[2] : "";

        if (!city) {
            return;
        }

        citySet.add(city);

        if (!districtsByCity[city]) {
            districtsByCity[city] = new Set();
        }
        if (district) {
            districtsByCity[city].add(district);
        }

        if (district) {
            var key = city + "||" + district;
            if (!neighborhoodsByDistrict[key]) {
                neighborhoodsByDistrict[key] = new Set();
            }
            if (neighborhood) {
                neighborhoodsByDistrict[key].add(neighborhood);
            }
        }
    });

    return {
        cities: Array.from(citySet).sort(),
        districtsByCity: sortMapRegionMap(districtsByCity),
        neighborhoodsByDistrict: sortMapRegionMap(neighborhoodsByDistrict)
    };
}

function isNeighborhoodName(value) {
    if (!value) {
        return false;
    }

    return /(?:동|가|읍|면|리)$/.test(String(value).trim());
}

function sortMapRegionMap(source) {
    var result = {};
    Object.keys(source).forEach(function (key) {
        result[key] = Array.from(source[key]).sort();
    });
    return result;
}
