document.addEventListener("DOMContentLoaded", function () {
    bindSalonSearchForm();
    bindSalonRegionFilters();
    bindSalonCardFocusEffect();
});

function bindSalonSearchForm() {
    var form = document.querySelector(".salon-search-form");
    if (!form) return;

    var inputs = form.querySelectorAll("input, select");
    inputs.forEach(function (input) {
        input.addEventListener("keypress", function (event) {
            if (event.key === "Enter") {
                event.preventDefault();
                form.submit();
            }
        });
    });
}

function bindSalonRegionFilters() {
    var citySelect = document.getElementById("city");
    var districtSelect = document.getElementById("district");
    var neighborhoodSelect = document.getElementById("neighborhood");
    var filterData = window.regionFilterData;

    if (!citySelect || !districtSelect || !neighborhoodSelect || !filterData) {
        return;
    }

    var addresses = Array.isArray(filterData.addresses) ? filterData.addresses : [];
    var regions = parseRegionOptions(addresses);

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

    citySelect.addEventListener("change", function () {
        districtSelect.dataset.selectedValue = "";
        neighborhoodSelect.dataset.selectedValue = "";
        syncDistricts(true);
    });

    districtSelect.addEventListener("change", function () {
        neighborhoodSelect.dataset.selectedValue = "";
        syncNeighborhoods(true);
    });

    populateSelect(citySelect, regions.cities, "전체 시", citySelect.value || "");
    syncDistricts(false);
}

function parseRegionOptions(addresses) {
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
        districtsByCity: sortRegionMap(districtsByCity),
        neighborhoodsByDistrict: sortRegionMap(neighborhoodsByDistrict)
    };
}

function isNeighborhoodName(value) {
    if (!value) {
        return false;
    }

    return /(?:동|가|읍|면|리)$/.test(String(value).trim());
}

function sortRegionMap(source) {
    var result = {};
    Object.keys(source).forEach(function (key) {
        result[key] = Array.from(source[key]).sort();
    });
    return result;
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
