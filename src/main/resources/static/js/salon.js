document.addEventListener("DOMContentLoaded", function () {
 bindSalonSearchForms();
 bindSalonPagination();
 bindSalonCardFocusEffect();
 bindBranchMap(0);
});

var CURRENT_LOCATION_STORAGE_KEY = "salonCurrentLocation";
var CURRENT_LOCATION_MAX_AGE_MS = 5 * 60 * 1000;
var salonSearchRequestController = null;
var salonSearchRequestSequence = 0;
var salonSearchRequestInFlight = false;
var salonSearchRequestUrl = null;
var salonSearchResetControlsPending = false;
var salonSearchPendingUrl = null;

function bindSalonSearchForms() {
 var forms = document.querySelectorAll(".salon-search-form[data-auto-submit='true']");
 if (!forms.length) return;

 forms.forEach(function (form) {
  bindAutoSubmitForm(form);
 });
}

function bindAutoSubmitForm(form) {
 if (!form || form.dataset.autoSubmitBound === "true") return;

 form.dataset.autoSubmitBound = "true";
 bindSalonSort(form);

 var textInputs = Array.from(form.querySelectorAll("input[type='text']"));
 var controls = Array.from(form.querySelectorAll("select, input[type='checkbox']"));
 var ratingSliderInput = form.querySelector("[data-rating-slider-input]");
 var presetLinks = Array.from(form.querySelectorAll("[data-preset-mode]"));
 var resetButton = form.querySelector("[data-search-reset]");
 var submitTimerId = 0;
 var isComposing = false;
 var isDraggingRatingSlider = false;

 form.addEventListener("submit", function (event) {
  event.preventDefault();
  window.clearTimeout(submitTimerId);
  if (isComposing) return;
  submitSalonSearchForm(form);
 });

 form.addEventListener("salon-search-cancel-pending", function () {
  window.clearTimeout(submitTimerId);
 });

 function scheduleSubmit(delay) {
  window.clearTimeout(submitTimerId);
  submitTimerId = window.setTimeout(function () {
   form.requestSubmit();
  }, delay);
 }

 textInputs.forEach(function (input) {
  input.addEventListener("compositionstart", function () {
   isComposing = true;
   window.clearTimeout(submitTimerId);
   if (salonSearchResetControlsPending) cancelSalonSearchRequest();
  });

  input.addEventListener("compositionend", function () {
   isComposing = false;
   queueSalonSearchForm(form);
  });

  input.addEventListener("input", function () {
   window.clearTimeout(submitTimerId);
   queueSalonSearchForm(form);
  });

  input.addEventListener("keydown", function (event) {
   if (isComposing || event.isComposing || event.keyCode === 229) return;
   if (event.key === "Enter") {
	event.preventDefault();
	window.clearTimeout(submitTimerId);
	form.requestSubmit();
   }
  });
 });

 controls.forEach(function (control) {
  control.addEventListener("change", function () {
   window.clearTimeout(submitTimerId);
   form.requestSubmit();
  });
 });

 if (ratingSliderInput) {
  syncRatingSlider(form, ratingSliderInput, false);

  function beginRatingSliderDrag() {
   window.clearTimeout(submitTimerId);
   isDraggingRatingSlider = true;
  }

  function endRatingSliderDrag() {
   isDraggingRatingSlider = false;
  }

  ratingSliderInput.addEventListener("pointerdown", beginRatingSliderDrag);
  ratingSliderInput.addEventListener("pointerup", endRatingSliderDrag);
  ratingSliderInput.addEventListener("pointercancel", endRatingSliderDrag);
  ratingSliderInput.addEventListener("mousedown", beginRatingSliderDrag);
  ratingSliderInput.addEventListener("mouseup", endRatingSliderDrag);
  ratingSliderInput.addEventListener("touchstart", beginRatingSliderDrag, {passive: true});
  ratingSliderInput.addEventListener("touchend", endRatingSliderDrag);
  ratingSliderInput.addEventListener("touchcancel", endRatingSliderDrag);
  ratingSliderInput.addEventListener("blur", endRatingSliderDrag);

  ratingSliderInput.addEventListener("input", function () {
   cancelSalonSearchRequest();
   syncRatingSlider(form, ratingSliderInput, true);
   if (!isDraggingRatingSlider) {
	scheduleSubmit(180);
   }
  });

  ratingSliderInput.addEventListener("change", function () {
   endRatingSliderDrag();
   syncRatingSlider(form, ratingSliderInput, true);
   window.clearTimeout(submitTimerId);
   form.requestSubmit();
  });
 }

 if (resetButton) resetButton.addEventListener("click", function () {
  window.clearTimeout(submitTimerId);
  var resetUrl = new URL(form.dataset.resetUrl || form.getAttribute("action") || "/salons", window.location.origin);
  var presetMode = getCurrentPresetMode(form);
  if (presetMode === "all") resetUrl.searchParams.delete("preset");
  else resetUrl.searchParams.set("preset", presetMode);
  loadSalonListUrl(resetUrl.toString(), true);
 });
 presetLinks.forEach(function (link) {
  link.addEventListener("click", function (event) {
   event.preventDefault();
   if (!applyPresetMode(form, link.dataset.presetMode || "all")) return;
   window.clearTimeout(submitTimerId);
   form.requestSubmit();
  });
 });
}

function bindSalonSort(form) {
 bindCatalogSelect(form.querySelector("[data-salon-sort]"));
}

function bindSalonPagination() {
 document.querySelectorAll("[data-salon-page-link]").forEach(function (link) {
  if (link.dataset.pageBound === "true") return;
  link.dataset.pageBound = "true";
  link.addEventListener("click", function (event) {
   if (event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
   event.preventDefault();
   var form = document.querySelector(".salon-search-form--primary");
   var pageUrl = new URL(link.href, window.location.origin);
   if (form) {
    form.dispatchEvent(new Event("salon-search-cancel-pending"));
    var searchUrl = new URL(buildSalonSearchUrl(form));
    if (pageUrl.searchParams.has("page")) searchUrl.searchParams.set("page", pageUrl.searchParams.get("page"));
    pageUrl = searchUrl;
   }
   loadSalonListUrl(pageUrl.toString());
  });
 });
}

function getCurrentPresetMode(form) {
 if (!form) return "all";

 var presetInput = form.querySelector("input[name='preset']");
 if (!presetInput || !presetInput.value) return "all";

 return presetInput.value;
}

function applyPresetMode(form, presetMode) {
 if (!form) return false;

 var normalizedPreset = presetMode || "all";
 var currentPreset = getCurrentPresetMode(form);
 var presetInput = form.querySelector("input[name='preset']");
 var keywordInput = form.querySelector("input[name='keyword']");
 var ratingSliderInput = form.querySelector("[data-rating-slider-input]");

 if (currentPreset === normalizedPreset) return false;

 if (keywordInput && (
	 (currentPreset === "all" && normalizedPreset === "recommend-by-service") ||
	 (currentPreset === "recommend-by-service" && normalizedPreset === "all")
 )) keywordInput.value = "";

 if (normalizedPreset === "all") {
  if (presetInput) presetInput.remove();
  return true;
 }

 if (!presetInput) {
  presetInput = document.createElement("input");
  presetInput.type = "hidden";
  presetInput.name = "preset";
  form.insertBefore(presetInput, form.firstChild);
 }

 presetInput.value = normalizedPreset;

 var presetRatingValue = getPresetRatingValue(normalizedPreset);
 if (presetRatingValue !== null && ratingSliderInput) setRatingSliderValue(form, ratingSliderInput, presetRatingValue, false);

 return true;
}

function getPresetRatingValue(presetMode) {
 if (presetMode === "top-rated") return 4;
 if (presetMode === "top-rated-4-5") return 4.5;
 return null;
}

function normalizeRatingValue(rawValue) {
 var numericValue = Number.parseFloat(rawValue);
 if (!Number.isFinite(numericValue) || numericValue <= 0) return "";

 if (Math.abs(numericValue - Math.round(numericValue)) < 0.001) return String(Math.round(numericValue));

 return numericValue.toFixed(1);
}

function formatRatingValueText(ratingValue) {
 if (!ratingValue) return "전체";

 var numericValue = Number.parseFloat(ratingValue);
 if (!Number.isFinite(numericValue)) return ratingValue + "점";

 return numericValue.toFixed(1) + "점";
}

function updateRatingSliderProgress(sliderInput, ratingValue) {
 if (!sliderInput) return;

 var sliderValue = Number.parseFloat(ratingValue || sliderInput.value || "0");
 var percent = Math.max(0, Math.min(100, (sliderValue / 5) * 100));
 var sliderControl = sliderInput.closest(".salon-rating-slider__control");
 if (sliderControl) sliderControl.style.setProperty("--rating-progress", percent + "%");
}

function syncRatingPresetState(form, ratingValue) {
 if (!form) return;

 var currentPreset = getCurrentPresetMode(form);
 if (currentPreset !== "top-rated" && currentPreset !== "top-rated-4-5") return;

 var presetInput = form.querySelector("input[name='preset']");
 if (ratingValue === "4") {
  if (!presetInput) {
   presetInput = document.createElement("input");
   presetInput.type = "hidden";
   presetInput.name = "preset";
   form.insertBefore(presetInput, form.firstChild);
  }
  presetInput.value = "top-rated";
  return;
 }

 if (ratingValue === "4.5") {
  if (!presetInput) {
   presetInput = document.createElement("input");
   presetInput.type = "hidden";
   presetInput.name = "preset";
   form.insertBefore(presetInput, form.firstChild);
  }
  presetInput.value = "top-rated-4-5";
  return;
 }

 if (presetInput) presetInput.remove();
}

function syncRatingSlider(form, sliderInput, shouldSyncPreset) {
 if (!form || !sliderInput) return;

 var ratingValue = normalizeRatingValue(sliderInput.value);
 var ratingValueElement = form.querySelector("[data-rating-slider-value]");
 var hiddenMinRatingInput = form.querySelector("input[name='minRating']");

 if (hiddenMinRatingInput) hiddenMinRatingInput.value = ratingValue;

 if (ratingValueElement) ratingValueElement.textContent = formatRatingValueText(ratingValue);

 updateRatingSliderProgress(sliderInput, ratingValue);

 if (shouldSyncPreset) syncRatingPresetState(form, ratingValue);
}

function setRatingSliderValue(form, sliderInput, ratingValue, shouldSyncPreset) {
 if (!sliderInput) return;

 var normalizedValue = normalizeRatingValue(ratingValue);
 sliderInput.value = normalizedValue || "0";
 syncRatingSlider(form, sliderInput, shouldSyncPreset);
}

function submitSalonSearchForm(form) {
 if (!form) return;

 var url = buildSalonSearchUrl(form);
 if (salonSearchRequestInFlight && !salonSearchResetControlsPending && salonSearchRequestUrl === url) {
  salonSearchPendingUrl = null;
  return;
 }
 loadSalonListUrl(url);
}

function queueSalonSearchForm(form) {
 if (!form) return;

 var url = buildSalonSearchUrl(form);
 // Finish the current search so continuous typing cannot starve result updates.
 // A reset is superseded instead, because its response would write control values.
 if (salonSearchRequestInFlight && !salonSearchResetControlsPending) {
  salonSearchPendingUrl = url === salonSearchRequestUrl ? null : url;
  return;
 }
 loadSalonListUrl(url);
}

function buildSalonSearchUrl(form) {
 var action = form.getAttribute("action") || window.location.pathname || "/salons";
 var url = new URL(action, window.location.origin);
 var formData = new FormData(form);
 var params = new URLSearchParams();

 formData.forEach(function (value, key) {
  if (typeof value !== "string" || value === "" || key === "page") return;
  params.append(key, value);
 });

 url.search = params.toString();
 return url.toString();
}

function cancelSalonSearchRequest() {
 // Explicit filter changes supersede both the active request and queued typing.
 salonSearchRequestSequence += 1;
 if (salonSearchRequestController) salonSearchRequestController.abort();
 salonSearchRequestController = null;
 salonSearchRequestInFlight = false;
 salonSearchRequestUrl = null;
 salonSearchResetControlsPending = false;
 salonSearchPendingUrl = null;
 setSalonSearchLoadingState(false);
}

function loadSalonListUrl(url, resetSearchControls) {
 if (!url) return;

 url = new URL(url, window.location.origin).toString();
 cancelSalonSearchRequest();

 var requestId = salonSearchRequestSequence;
 var controller = typeof AbortController === "function" ? new AbortController() : null;
 salonSearchRequestController = controller;
 salonSearchRequestInFlight = true;
 salonSearchRequestUrl = url;
 salonSearchResetControlsPending = Boolean(resetSearchControls);
 setSalonSearchLoadingState(true);

 return fetch(url, {
  method: "GET",
  headers: {
   "X-Requested-With": "XMLHttpRequest"
  },
  signal: controller ? controller.signal : undefined
 })
	 .then(function (response) {
	  if (!response.ok) throw new Error("Failed to load salon list");
	  return response.text();
	 })
	 .then(function (html) {
	  if (requestId !== salonSearchRequestSequence) return;

	  var parsedDocument = new DOMParser().parseFromString(html, "text/html");
	  if (!parsedDocument.getElementById("salon-primary-search-area")) {
	   throw new Error("Missing salon search area");
	  }

	  applySalonListResponse(parsedDocument, url, resetSearchControls);
	 })
	 .catch(function (error) {
	  if (requestId !== salonSearchRequestSequence) return;
	  if (error && error.name === "AbortError") return;
	  if (salonSearchPendingUrl) return;
	  showSalonSearchError();
	 })
	 .finally(function () {
	  if (requestId === salonSearchRequestSequence) {
	   var pendingUrl = salonSearchPendingUrl;
	   salonSearchRequestController = null;
	   salonSearchRequestInFlight = false;
	   salonSearchRequestUrl = null;
	   salonSearchResetControlsPending = false;
	   salonSearchPendingUrl = null;
	   if (pendingUrl) loadSalonListUrl(pendingUrl);
	   else setSalonSearchLoadingState(false);
	  }
	 });
}

function applySalonListResponse(parsedDocument, url, resetSearchControls) {
 replaceElementById("salon-list-hero", parsedDocument);
 replaceElementById("salon-list-message", parsedDocument);
 syncSalonSearchForm(parsedDocument, resetSearchControls);
 replaceElementById("salon-kakao-sync-area", parsedDocument);
 replaceElementById("salon-kakao-results-area", parsedDocument);
 replaceElementById("salon-results-area", parsedDocument);
 syncBranchMapSupplementalContent(parsedDocument);

 if (parsedDocument.title) document.title = parsedDocument.title;

 window.history.replaceState(null, "", toRelativeSalonUrl(url));
 bindSalonSearchForms();
 bindSalonPagination();
 bindSalonCardFocusEffect();
 bindBranchMap(0);
}

function syncSalonSearchForm(parsedDocument, resetSearchControls) {
 var selector = "#salon-primary-search-area .salon-search-form";
 var form = document.querySelector(selector);
 var nextForm = parsedDocument.querySelector(selector);
 if (!form || !nextForm) return;

 // Keep the actual controls and their listeners, caret, selection and IME state.
 form.setAttribute("action", nextForm.getAttribute("action") || "/salons");
 form.dataset.resetUrl = nextForm.dataset.resetUrl || form.getAttribute("action");
 var keywordInput = form.querySelector("input[name='keyword']");
 var nextKeywordInput = nextForm.querySelector("input[name='keyword']");
 if (keywordInput && nextKeywordInput) keywordInput.placeholder = nextKeywordInput.placeholder;

 var presetInput = form.querySelector("input[name='preset']");
 var nextPresetInput = nextForm.querySelector("input[name='preset']");
 if (nextPresetInput) {
  if (!presetInput) {
   presetInput = document.createElement("input");
   presetInput.type = "hidden";
   presetInput.name = "preset";
   form.prepend(presetInput);
  }
  presetInput.value = nextPresetInput.value;
 } else if (presetInput) presetInput.remove();

 form.querySelectorAll("[data-preset-mode]").forEach(function (link) {
  var nextLink = nextForm.querySelector("[data-preset-mode='" + link.dataset.presetMode + "']");
  if (!nextLink) return;
  link.classList.toggle("is-active", nextLink.classList.contains("is-active"));
  link.setAttribute("href", nextLink.getAttribute("href"));
 });

 var locationButton = form.querySelector("[data-current-location-trigger]");
 var nextLocationButton = nextForm.querySelector("[data-current-location-trigger]");
 if (locationButton && nextLocationButton && locationButton.dataset.locationPending !== "true") {
  locationButton.disabled = nextLocationButton.disabled;
 }

 if (resetSearchControls) {
  ["keyword", "region", "reservable", "sort"].forEach(function (name) {
   var control = form.elements.namedItem(name);
   var nextControl = nextForm.elements.namedItem(name);
   if (!control || !nextControl) return;
   if (control.type === "checkbox") control.checked = nextControl.checked;
   else control.value = nextControl.value;
  });
  var sliderInput = form.querySelector("[data-rating-slider-input]");
  var nextSliderInput = nextForm.querySelector("[data-rating-slider-input]");
  if (sliderInput && nextSliderInput) setRatingSliderValue(form, sliderInput, nextSliderInput.value, false);
  var sortRoot = form.querySelector("[data-salon-sort]");
  if (sortRoot) sortRoot.dispatchEvent(new Event("catalog-select-sync"));
 }

 var ratingSlider = form.querySelector("[data-rating-slider]");
 var minRatingInput = form.elements.namedItem("minRating");
 if (ratingSlider && minRatingInput) ratingSlider.dataset.ratingActive = minRatingInput.value ? "true" : "false";
}

function showSalonSearchError() {
 var messageArea = document.getElementById("salon-list-message");
 if (!messageArea) return;
 var alert = document.createElement("div");
 alert.className = "alert alert-danger";
 alert.setAttribute("role", "alert");
 alert.textContent = "검색 결과를 불러오지 못했습니다. 검색 조건을 다시 변경하면 재시도합니다.";
 messageArea.replaceChildren(alert);
}

function replaceElementById(elementId, parsedDocument) {
 var currentElement = document.getElementById(elementId);
 var nextElement = parsedDocument.getElementById(elementId);

 if (!currentElement || !nextElement) return;

 currentElement.replaceWith(document.importNode(nextElement, true));
}

function syncBranchMapSupplementalContent(parsedDocument) {
 var currentMapElement = document.getElementById("salon-branch-map");
 var nextMapElement = parsedDocument.getElementById("salon-branch-map");

 if (currentMapElement && nextMapElement) currentMapElement.dataset.kakaoEnabled = nextMapElement.dataset.kakaoEnabled || "false";

 replaceOptionalElementById("salon-branch-map-status", parsedDocument, "#salon-branch-map-section");
 replaceOptionalElementById("salon-branch-marker-data", parsedDocument, "#salon-branch-map-section");
}

function replaceOptionalElementById(elementId, parsedDocument, parentSelector) {
 var currentElement = document.getElementById(elementId);
 var nextElement = parsedDocument.getElementById(elementId);

 if (currentElement && nextElement) {
  currentElement.replaceWith(document.importNode(nextElement, true));
  return;
 }

 if (currentElement && !nextElement) {
  currentElement.remove();
  return;
 }

 if (!currentElement && nextElement) {
  var currentParent = document.querySelector(parentSelector);
  if (currentParent) currentParent.appendChild(document.importNode(nextElement, true));
 }
}

function toRelativeSalonUrl(url) {
 var resolvedUrl = new URL(url, window.location.origin);
 return resolvedUrl.pathname + resolvedUrl.search + resolvedUrl.hash;
}

function setSalonSearchLoadingState(isLoading) {
 var shell = document.getElementById("salon-search-map-shell");
 if (!shell) return;

 shell.dataset.loading = isLoading ? "true" : "false";
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

function bindBranchMap(attempt) {
 var mapElement = document.getElementById("salon-branch-map");
 if (!mapElement) {
  window.__salonBranchMapState = null;
  return;
 }

 var statusElement = document.querySelector("[data-branch-map-status]");
 var currentLocationButton = document.querySelector("[data-current-location-trigger]");
 var branchMapState = window.__salonBranchMapState || null;

 if (mapElement.dataset.kakaoEnabled !== "true") {
  disableCurrentLocationAction(currentLocationButton);
  if (statusElement) statusElement.textContent = "카카오 JavaScript 키가 설정되지 않아 지도를 표시할 수 없습니다.";
  if (branchMapState) clearBranchMarkers(branchMapState);
  return;
 }

 if (branchMapState && branchMapState.map && branchMapState.mapElement === mapElement) {
  bindBranchMapControls(branchMapState);
  refreshBranchMap(branchMapState);
  return;
 }

 if (!window.kakao || !window.kakao.maps || typeof window.kakao.maps.load !== "function") {
  if (attempt < 20) {
   if (statusElement) statusElement.textContent = "카카오 지도 SDK를 불러오는 중입니다.";
   window.setTimeout(function () {
	bindBranchMap(attempt + 1);
   }, 250);
   return;
  }

  disableCurrentLocationAction(currentLocationButton);
  if (statusElement) statusElement.textContent = buildKakaoDomainStatusMessage();
  return;
 }

 window.kakao.maps.load(function () {
  try {
   if (typeof window.kakao.maps.Map !== "function"
	   || typeof window.kakao.maps.LatLng !== "function"
	   || typeof window.kakao.maps.Marker !== "function") throw new Error("Kakao Maps core constructors are unavailable");

   var map = new window.kakao.maps.Map(mapElement, {
	center: new window.kakao.maps.LatLng(37.5665, 126.9780),
	level: 8
   });
   mapElement.dataset.mapInitialized = "true";

   if (typeof window.kakao.maps.ZoomControl === "function"
	   && window.kakao.maps.ControlPosition
	   && window.kakao.maps.ControlPosition.RIGHT) map.addControl(
	   new window.kakao.maps.ZoomControl(),
	   window.kakao.maps.ControlPosition.RIGHT
   );

   branchMapState = {
	map: map,
	mapElement: mapElement,
	geocoder: createRegionGeocoder(),
	currentLocationOverlay: createCurrentLocationOverlay(),
	markerEntries: []
   };
   window.__salonBranchMapState = branchMapState;

   bindBranchMapControls(branchMapState);
   refreshBranchMap(branchMapState);
  } catch (error) {
   mapElement.dataset.mapInitialized = "false";
   disableCurrentLocationAction(currentLocationButton);
   if (statusElement) statusElement.textContent = buildKakaoDomainStatusMessage();
  }
 });
}

function bindBranchMapControls(branchMapState) {
 if (!branchMapState || !branchMapState.mapElement) return;

 branchMapState.statusElement = document.querySelector("[data-branch-map-status]");
 branchMapState.currentLocationButton = document.querySelector("[data-current-location-trigger]");
 branchMapState.regionInput = document.querySelector(".salon-search-form__control--region[name='region']");

 bindCurrentLocationAction(
	 branchMapState.currentLocationButton,
	 branchMapState.map,
	 branchMapState.statusElement,
	 branchMapState.geocoder,
	 branchMapState.regionInput,
	 branchMapState.currentLocationOverlay
 );
 if (navigator.geolocation && branchMapState.currentLocationButton) setCurrentLocationButtonState(
	 branchMapState.currentLocationButton,
	 branchMapState.currentLocationButton.dataset.locationPending === "true"
 );
}

function clearBranchMarkers(branchMapState) {
 if (!branchMapState || !Array.isArray(branchMapState.markerEntries)) return;

 branchMapState.markerEntries.forEach(function (entry) {
  if (entry.infowindow) entry.infowindow.close();
  if (entry.marker) entry.marker.setMap(null);
  if (entry.card) entry.card.classList.remove("is-active");
 });

 branchMapState.markerEntries = [];
}

function refreshBranchMap(branchMapState) {
 if (!branchMapState || !branchMapState.map || !branchMapState.mapElement) return;

 var map = branchMapState.map;
 var mapElement = branchMapState.mapElement;
 var statusElement = branchMapState.statusElement;
 var markerElements = Array.from(document.querySelectorAll(".js-branch-map-marker"));
 var branchCards = Array.from(document.querySelectorAll(".salon-branch-card"));
 var branchCardMap = new Map();
 var bounds = new window.kakao.maps.LatLngBounds();
 var markerEntries = [];
 var invalidMarkerCount = 0;

 if (typeof map.relayout === "function") map.relayout();

 clearBranchMarkers(branchMapState);

 branchCards.forEach(function (card) {
  if (card.dataset.salonId) branchCardMap.set(card.dataset.salonId, card);
 });

 markerElements.forEach(function (element) {
  var latitude = parseFloat(normalizeDataValue(element.dataset.latitude));
  var longitude = parseFloat(normalizeDataValue(element.dataset.longitude));

  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
   invalidMarkerCount += 1;
   return;
  }

  var position = new window.kakao.maps.LatLng(latitude, longitude);
  var marker = new window.kakao.maps.Marker({
   map: map,
   position: position,
   title: normalizeDataValue(element.dataset.name)
  });
  var infowindow = new window.kakao.maps.InfoWindow({
   content: buildBranchPopupHtml(element)
  });

  bounds.extend(position);

  var salonId = normalizeDataValue(element.dataset.salonId);
  var card = branchCardMap.get(salonId) || null;

  window.kakao.maps.event.addListener(marker, "click", function () {
   focusBranchMarker(map, markerEntries, salonId);
  });

  if (card) card.addEventListener("click", function () {
   focusBranchMarker(map, markerEntries, salonId);
  });

  markerEntries.push({
   salonId: salonId,
   marker: marker,
   infowindow: infowindow,
   card: card,
   position: position
  });
 });

 branchMapState.markerEntries = markerEntries;

 var persistedCurrentLocation = readPersistedCurrentLocationState();
 var restoredCurrentLocation = null;
 var isLocationPending = branchMapState.currentLocationButton
	 && branchMapState.currentLocationButton.dataset.locationPending === "true";

 if (!markerEntries.length) {
  if (persistedCurrentLocation) restoredCurrentLocation = renderCurrentLocation(
	  map,
	  branchMapState.currentLocationOverlay,
	  persistedCurrentLocation,
	  true
  );

  if (statusElement) statusElement.textContent = isLocationPending
	  ? "현재 위치를 확인하는 중입니다."
	  : restoredCurrentLocation
	  ? buildCurrentLocationStatusMessage(
		  restoredCurrentLocation.accuracyText,
		  restoredCurrentLocation.lowAccuracyHint,
		  restoredCurrentLocation.regionKeyword,
		  restoredCurrentLocation.regionKeywordApplied
	  )
	  : "표시할 지점 정보가 없습니다. 현재 위치만 확인할 수 있습니다.";
  return;
 }

 if (markerEntries.length === 1) {
  map.setCenter(markerEntries[0].position);
  map.setLevel(4);
 } else map.setBounds(bounds);

 if (persistedCurrentLocation) restoredCurrentLocation = renderCurrentLocation(
	 map,
	 branchMapState.currentLocationOverlay,
	 persistedCurrentLocation,
	 true
 );

 if (statusElement) statusElement.textContent = isLocationPending
	 ? "현재 위치를 확인하는 중입니다."
	 : restoredCurrentLocation
	 ? buildCurrentLocationStatusMessage(
		 restoredCurrentLocation.accuracyText,
		 restoredCurrentLocation.lowAccuracyHint,
		 restoredCurrentLocation.regionKeyword,
		 restoredCurrentLocation.regionKeywordApplied
	 )
	 : (invalidMarkerCount > 0
		 ? "일부 지점의 좌표를 찾지 못해 지도에서 제외했습니다."
		 : "카카오 지도에서 지점 위치를 확인할 수 있습니다.");
}

function trackCurrentRegionEdits(regionInput) {
 if (!regionInput) return null;
 if (regionInput.__salonLocationEditState) return regionInput.__salonLocationEditState;

 var editState = {revision: 0, isComposing: false};
 regionInput.__salonLocationEditState = editState;
 regionInput.addEventListener("input", function () {
  editState.revision += 1;
 });
 regionInput.addEventListener("compositionstart", function () {
  editState.revision += 1;
  editState.isComposing = true;
 });
 regionInput.addEventListener("compositionend", function () {
  editState.isComposing = false;
 });
 return editState;
}

function bindCurrentLocationAction(button, map, statusElement, geocoder, regionInput, overlayState) {
 trackCurrentRegionEdits(regionInput);
 if (!button || button.dataset.bound === "true") return;

 if (!navigator.geolocation) {
  disableCurrentLocationAction(button);
  if (statusElement && !statusElement.textContent.trim()) statusElement.textContent = "브라우저가 현재 위치 기능을 지원하지 않습니다.";
  return;
 }

 button.dataset.bound = "true";
 button.dataset.defaultLabel = button.textContent.trim();

 var currentLocationOverlay = overlayState || createCurrentLocationOverlay();

 function updateCurrentLocationStatus(message) {
  var currentStatusElement = document.querySelector("[data-branch-map-status]");
  if (currentStatusElement) currentStatusElement.textContent = message;
  else if (statusElement && statusElement.isConnected) statusElement.textContent = message;
 }

 button.addEventListener("click", function () {
  if (button.dataset.locationPending === "true") return;
  var requestedRegionInput = document.querySelector(".salon-search-form__control--region[name='region']") || regionInput;
  var requestedRegionEdits = trackCurrentRegionEdits(requestedRegionInput);
  var requestedRegionSnapshot = requestedRegionInput && requestedRegionEdits ? {
   input: requestedRegionInput,
   value: requestedRegionInput.value,
   revision: requestedRegionEdits.revision,
   isComposing: requestedRegionEdits.isComposing
  } : null;
  setCurrentLocationButtonState(button, true);

  updateCurrentLocationStatus("현재 위치 권한을 확인하는 중입니다.");

  navigator.geolocation.getCurrentPosition(
	  function (position) {
	   var currentPosition = new window.kakao.maps.LatLng(
		   position.coords.latitude,
		   position.coords.longitude
	   );
	   var currentLocationState = {
		latitude: position.coords.latitude,
		longitude: position.coords.longitude,
		accuracy: position.coords.accuracy
	   };
	   var renderedCurrentLocation = renderCurrentLocation(
		   map,
		   currentLocationOverlay,
		   currentLocationState,
		   true
	   );

	   persistCurrentLocationState(currentLocationState);

	   resolveCurrentRegionKeyword(geocoder, position.coords, function (regionKeyword) {
		currentLocationState.regionKeyword = regionKeyword;
		currentLocationState.regionKeywordApplied = applyCurrentRegionKeyword(
			document.querySelector(".salon-search-form__control--region[name='region']") || regionInput,
			regionKeyword,
			requestedRegionSnapshot
		);
		persistCurrentLocationState(currentLocationState);

		updateCurrentLocationStatus(buildCurrentLocationStatusMessage(
			renderedCurrentLocation.accuracyText,
			renderedCurrentLocation.lowAccuracyHint,
			regionKeyword,
			currentLocationState.regionKeywordApplied
		));

		setCurrentLocationButtonState(button, false);
	   });
	  },
	  function (error) {
	   updateCurrentLocationStatus(getGeolocationErrorMessage(error));
	   setCurrentLocationButtonState(button, false);
	  },
	  {
	   enableHighAccuracy: true,
	   timeout: 10000,
	   maximumAge: 0
	  }
  );
 });
}

function createCurrentLocationOverlay() {
 return {
  marker: null,
  circle: null,
  infoWindow: null
 };
}

function clearCurrentLocationOverlay(overlayState) {
 if (!overlayState) return;

 if (overlayState.marker) {
  overlayState.marker.setMap(null);
  overlayState.marker = null;
 }
 if (overlayState.circle) {
  overlayState.circle.setMap(null);
  overlayState.circle = null;
 }
 if (overlayState.infoWindow) {
  overlayState.infoWindow.close();
  overlayState.infoWindow = null;
 }
}

function renderCurrentLocation(map, overlayState, locationState, focusMap) {
 if (!map
	 || !overlayState
	 || !locationState
	 || !Number.isFinite(locationState.latitude)
	 || !Number.isFinite(locationState.longitude)) return {
  accuracyText: formatAccuracy(0),
  lowAccuracyHint: "",
  regionKeyword: ""
 };

 clearCurrentLocationOverlay(overlayState);

 var currentPosition = new window.kakao.maps.LatLng(
	 locationState.latitude,
	 locationState.longitude
 );
 var accuracy = Number.isFinite(locationState.accuracy) ? locationState.accuracy : 0;
 var accuracyText = formatAccuracy(accuracy);
 var lowAccuracyHint = buildLowAccuracyHint(accuracy);
 var regionKeyword = normalizeDataValue(locationState.regionKeyword);

 overlayState.marker = new window.kakao.maps.Marker({
  map: map,
  position: currentPosition,
  title: "현재 위치",
  image: createCurrentLocationMarkerImage()
 });

 overlayState.circle = new window.kakao.maps.Circle({
  center: currentPosition,
  radius: Math.max(Math.round(accuracy || 0), 60),
  strokeWeight: 1,
  strokeColor: "#1f6feb",
  strokeOpacity: 0.35,
  strokeStyle: "solid",
  fillColor: "#1f6feb",
  fillOpacity: 0.12
 });
 overlayState.circle.setMap(map);

 overlayState.infoWindow = new window.kakao.maps.InfoWindow({
  content: [
   "<div class=\"salon-map-infowindow salon-map-infowindow--current\">",
   "<strong>현재 위치</strong>",
   "<div>정확도 " + escapeHtml(accuracyText) + "</div>",
   "<div>브라우저 위치 권한 기준입니다.</div>",
   "</div>"
  ].join("")
 });
 overlayState.infoWindow.open(map, overlayState.marker);

 if (focusMap !== false) {
  map.panTo(currentPosition);
  map.setLevel(Math.min(getMapLevel(map), 4));
 }

 return {
  position: currentPosition,
  accuracyText: accuracyText,
  lowAccuracyHint: lowAccuracyHint,
  regionKeyword: regionKeyword,
  regionKeywordApplied: locationState.regionKeywordApplied !== false
 };
}

function getCurrentLocationStorage() {
 try {
  return window.sessionStorage;
 } catch (error) {
  return null;
 }
}

function persistCurrentLocationState(locationState) {
 var storage = getCurrentLocationStorage();
 if (!storage || !locationState) return;

 storage.setItem(CURRENT_LOCATION_STORAGE_KEY, JSON.stringify({
  latitude: locationState.latitude,
  longitude: locationState.longitude,
  accuracy: locationState.accuracy,
  regionKeyword: normalizeDataValue(locationState.regionKeyword),
  regionKeywordApplied: locationState.regionKeywordApplied === true,
  savedAt: Date.now(),
  pathname: window.location.pathname
 }));
}

function readPersistedCurrentLocationState() {
 var storage = getCurrentLocationStorage();
 if (!storage) return null;

 var rawValue = storage.getItem(CURRENT_LOCATION_STORAGE_KEY);
 if (!rawValue) return null;

 try {
  var parsedValue = JSON.parse(rawValue);
  if (!parsedValue
	  || !Number.isFinite(parsedValue.latitude)
	  || !Number.isFinite(parsedValue.longitude)) {
   storage.removeItem(CURRENT_LOCATION_STORAGE_KEY);
   return null;
  }

  if (parsedValue.pathname !== window.location.pathname) return null;

  if (!Number.isFinite(parsedValue.savedAt)
	  || (Date.now() - parsedValue.savedAt) > CURRENT_LOCATION_MAX_AGE_MS) {
   storage.removeItem(CURRENT_LOCATION_STORAGE_KEY);
   return null;
  }

  return parsedValue;
 } catch (error) {
  storage.removeItem(CURRENT_LOCATION_STORAGE_KEY);
  return null;
 }
}

function createRegionGeocoder() {
 if (!window.kakao
	 || !window.kakao.maps
	 || !window.kakao.maps.services
	 || typeof window.kakao.maps.services.Geocoder !== "function") return null;

 return new window.kakao.maps.services.Geocoder();
}

function resolveCurrentRegionKeyword(geocoder, coords, callback) {
 if (!geocoder || !coords || typeof callback !== "function") {
  callback("");
  return;
 }

 geocoder.coord2RegionCode(
	 coords.longitude,
	 coords.latitude,
	 function (result, status) {
	  if (status !== window.kakao.maps.services.Status.OK || !Array.isArray(result)) {
	   callback("");
	   return;
	  }

	  callback(extractRegionKeyword(result));
	 }
 );
}

function extractRegionKeyword(regionResults) {
 var preferredRegion = regionResults.find(function (region) {
  return region && region.region_type === "H";
 }) || regionResults.find(function (region) {
  return region && region.region_type === "B";
 }) || regionResults[0];

 if (!preferredRegion) return "";

 return normalizeDataValue(preferredRegion.region_2depth_name)
	 || normalizeDataValue(preferredRegion.region_3depth_name)
	 || normalizeDataValue(preferredRegion.region_1depth_name)
	 || "";
}

function applyCurrentRegionKeyword(regionInput, regionKeyword, requestedRegionSnapshot) {
 if (!regionInput || !regionKeyword || !requestedRegionSnapshot) return false;

 var editState = trackCurrentRegionEdits(regionInput);
 // A late location response must not replace newer typing, including reverted edits
 // or composition that has started before its first input event.
 if (regionInput !== requestedRegionSnapshot.input
	 || regionInput.value !== requestedRegionSnapshot.value
	 || editState.revision !== requestedRegionSnapshot.revision
	 || editState.isComposing
	 || requestedRegionSnapshot.isComposing) return false;

 regionInput.value = regionKeyword;
 regionInput.dispatchEvent(new Event("input", {bubbles: true}));
 return true;
}

function buildCurrentLocationStatusMessage(accuracyText, lowAccuracyHint, regionKeyword, regionKeywordApplied) {
 var regionMessage = regionKeyword
	 ? (regionKeywordApplied === false
		 ? " 입력한 지역 검색 조건을 유지했습니다."
		 : " 지역 키워드에 " + regionKeyword + "를 입력했습니다.")
	 : "";

 return lowAccuracyHint
	 ? "현재 위치를 지도에 표시했습니다." + regionMessage + " 정확도 " + accuracyText + ". " + lowAccuracyHint
	 : "현재 위치를 지도에 표시했습니다." + regionMessage + " 정확도 " + accuracyText + ".";
}

function createCurrentLocationMarkerImage() {
 if (!window.kakao.maps.MarkerImage
	 || !window.kakao.maps.Size
	 || !window.kakao.maps.Point) return null;

 var imageSvg = [
  "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"34\" height=\"34\" viewBox=\"0 0 34 34\">",
  "<circle cx=\"17\" cy=\"17\" r=\"13\" fill=\"#1f6feb\" fill-opacity=\"0.2\"/>",
  "<circle cx=\"17\" cy=\"17\" r=\"8\" fill=\"#1f6feb\" stroke=\"#ffffff\" stroke-width=\"3\"/>",
  "</svg>"
 ].join("");
 var imageSrc = "data:image/svg+xml;charset=UTF-8," + encodeURIComponent(imageSvg);

 return new window.kakao.maps.MarkerImage(
	 imageSrc,
	 new window.kakao.maps.Size(34, 34),
	 {
	  offset: new window.kakao.maps.Point(17, 17)
	 }
 );
}

function setCurrentLocationButtonState(button, pending) {
 if (!button) return;

 button.dataset.locationPending = pending ? "true" : "false";
 var mapElement = document.getElementById("salon-branch-map");
 var disabled = pending || !navigator.geolocation || !mapElement || mapElement.dataset.kakaoEnabled !== "true";
 button.disabled = disabled;
 button.classList.toggle("is-disabled", disabled);
 button.textContent = pending
	 ? "위치 확인 중"
	 : (button.dataset.defaultLabel || "현재 위치 표시");
}

function disableCurrentLocationAction(button) {
 if (!button) return;

 button.disabled = true;
 button.classList.add("is-disabled");
}

function getGeolocationErrorMessage(error) {
 if (!error) return "현재 위치를 확인할 수 없습니다.";

 switch (error.code) {
  case error.PERMISSION_DENIED:
   return "현재 위치 권한이 거부되었습니다. 브라우저 권한을 허용해 주세요.";
  case error.POSITION_UNAVAILABLE:
   return "현재 위치 정보를 가져올 수 없습니다.";
  case error.TIMEOUT:
   return "현재 위치 확인 시간이 초과되었습니다. 다시 시도해 주세요.";
  default:
   return "현재 위치를 확인할 수 없습니다.";
 }
}

function getMapLevel(map) {
 if (!map || typeof map.getLevel !== "function") return 4;
 return map.getLevel();
}

function formatAccuracy(accuracy) {
 if (!Number.isFinite(accuracy) || accuracy <= 0) return "확인 불가";

 if (accuracy >= 1000) return "약 " + (accuracy / 1000).toFixed(1) + "km";

 return "약 " + Math.round(accuracy) + "m";
}

function buildLowAccuracyHint(accuracy) {
 if (!Number.isFinite(accuracy) || accuracy <= 0) return "";

 if (accuracy >= 1000) return "현재 브라우저가 GPS가 아닌 네트워크 기반 위치를 반환해 오차가 클 수 있습니다.";

 if (accuracy >= 300) return "현재 위치 오차가 다소 큰 편입니다.";

 return "";
}

function buildBranchPopupHtml(element) {
 var roadAddress = normalizeDataValue(element.dataset.roadAddress);
 var address = normalizeDataValue(element.dataset.address);
 var detailUrl = normalizeDataValue(element.dataset.detailUrl);
 var phone = normalizeDataValue(element.dataset.phone);
 var lines = [
  "<div class=\"salon-map-infowindow\">",
  "<strong>" + escapeHtml(normalizeDataValue(element.dataset.name)) + "</strong>"
 ];

 if (roadAddress || address) lines.push("<div>" + escapeHtml(roadAddress || address) + "</div>");
 if (phone) lines.push("<div>" + escapeHtml(phone) + "</div>");
 if (detailUrl) lines.push("<div><a href=\"" + escapeHtml(detailUrl) + "\">상세 보기</a></div>");
 lines.push("</div>");

 return lines.join("");
}

function focusBranchMarker(map, markerEntries, salonId) {
 markerEntries.forEach(function (entry) {
  var isActive = entry.salonId === salonId;

  if (entry.card) entry.card.classList.toggle("is-active", isActive);

  if (isActive) {
   map.setCenter(entry.position);
   map.setLevel(4);
   entry.infowindow.open(map, entry.marker);
   if (entry.card) entry.card.scrollIntoView({block: "nearest", behavior: "smooth"});
  } else entry.infowindow.close();
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
 if (!value || value === "null" || value === "undefined") return "";
 return value;
}

function buildKakaoDomainStatusMessage() {
 return "카카오 지도를 표시하려면 Kakao Developers에 "
	 + window.location.origin
	 + " 도메인을 JavaScript SDK 웹 플랫폼으로 등록해야 합니다.";
}
