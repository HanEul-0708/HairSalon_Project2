document.addEventListener("DOMContentLoaded", function () {
 document.querySelectorAll("[data-catalog-select]").forEach(function (root) {
  bindCatalogSelect(root);
 });
 bindServiceSearchForm();
 bindServicePagination();
 bindServiceDeleteConfirm();
});

var serviceSearchRequestController = null;
var serviceSearchRequestSequence = 0;
var serviceSearchRequestInFlight = false;
var serviceSearchRequestUrl = null;
var serviceSearchResetControlsPending = false;
var serviceSearchPendingUrl = null;

function bindServiceSearchForm() {
 document.querySelectorAll(".service-search-form").forEach(function (form) {
  if (form.dataset.serviceSearchBound === "true") return;
  form.dataset.serviceSearchBound = "true";
  if (form.dataset.serviceLiveSearch !== "true") {
   form.querySelectorAll("input, select").forEach(function (field) {
    field.addEventListener("keypress", function (event) {
     if (event.key === "Enter" && !event.isComposing && event.keyCode !== 229) {
      event.preventDefault();
      form.requestSubmit();
     }
    });
   });
   return;
  }

  var submitTimerId = 0;
  var isComposing = false;
  function clearPendingSubmit() { window.clearTimeout(submitTimerId); }
  form.addEventListener("service-search-cancel-pending", clearPendingSubmit);
  form.addEventListener("submit", function (event) {
   event.preventDefault();
   clearPendingSubmit();
   if (!isComposing) submitServiceSearchForm(form, document.activeElement?.name === "page");
  });
  function bindTextInput(input) {
   if (input.dataset.serviceTextBound === "true") return;
   input.dataset.serviceTextBound = "true";
   input.addEventListener("compositionstart", function () {
    isComposing = true;
    clearPendingSubmit();
    if (serviceSearchResetControlsPending) cancelServiceSearchRequest();
   });
   input.addEventListener("compositionend", function () {
    isComposing = false;
    queueServiceSearchForm(form, input.name === "page");
   });
   input.addEventListener("input", function () {
    clearPendingSubmit();
    queueServiceSearchForm(form, input.name === "page");
   });
   input.addEventListener("keydown", function (event) {
    if (isComposing || event.isComposing || event.keyCode === 229) return;
    if (event.key === "Enter") {
     event.preventDefault();
     form.requestSubmit();
    }
   });
  }
  function bindTextInputs() { form.querySelectorAll("input[type='text']").forEach(bindTextInput); }
  bindTextInputs();
  form.addEventListener("service-search-bind-fields", bindTextInputs);
  Array.from(form.elements).filter(function (field) { return field.tagName === "SELECT"; }).forEach(function (select) {
   select.addEventListener("change", function () { clearPendingSubmit(); submitServiceSearchForm(form); });
  });
  form.addEventListener("catalog-range-drag-start", clearPendingSubmit);
  form.querySelectorAll("[data-catalog-range-input]").forEach(function (input) {
   input.addEventListener("input", function () {
    clearPendingSubmit();
    cancelServiceSearchRequest();
    if (input.closest("[data-catalog-range]").dataset.dragging !== "true") {
     submitTimerId = window.setTimeout(function () { form.requestSubmit(); }, 180);
    }
   });
   input.addEventListener("change", function () { clearPendingSubmit(); form.requestSubmit(); });
  });
  form.querySelector("[data-service-search-reset]")?.addEventListener("click", function (event) {
   if (event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
   event.preventDefault();
   clearPendingSubmit();
   loadServiceListUrl(event.currentTarget.href, true);
  });
 });
}

function buildServiceSearchUrl(form, includePage) {
 var url = new URL(form.getAttribute("action") || "/salon-services", window.location.origin);
 var params = new URLSearchParams();
 new FormData(form).forEach(function (value, key) {
  if (typeof value !== "string" || value === "" || (key === "page" && !includePage)) return;
  params.append(key, value);
 });
 url.search = params.toString();
 return url.toString();
}

function submitServiceSearchForm(form, includePage) {
 var url = buildServiceSearchUrl(form, includePage);
 if (serviceSearchRequestInFlight && !serviceSearchResetControlsPending && serviceSearchRequestUrl === url) {
  serviceSearchPendingUrl = null;
  return;
 }
 loadServiceListUrl(url);
}

function queueServiceSearchForm(form, includePage) {
 var url = buildServiceSearchUrl(form, includePage);
 // Match salon search: finish the leading request and coalesce subsequent typing.
 if (serviceSearchRequestInFlight && !serviceSearchResetControlsPending) {
  serviceSearchPendingUrl = url === serviceSearchRequestUrl ? null : url;
  return;
 }
 loadServiceListUrl(url);
}

function cancelServiceSearchRequest() {
 serviceSearchRequestSequence += 1;
 serviceSearchRequestController?.abort();
 serviceSearchRequestController = null;
 serviceSearchRequestInFlight = false;
 serviceSearchRequestUrl = null;
 serviceSearchResetControlsPending = false;
 serviceSearchPendingUrl = null;
 setServiceSearchLoadingState(false);
}

function loadServiceListUrl(url, resetSearchControls) {
 url = new URL(url, window.location.origin).toString();
 cancelServiceSearchRequest();
 var requestId = serviceSearchRequestSequence;
 var controller = typeof AbortController === "function" ? new AbortController() : null;
 serviceSearchRequestController = controller;
 serviceSearchRequestInFlight = true;
 serviceSearchRequestUrl = url;
 serviceSearchResetControlsPending = Boolean(resetSearchControls);
 setServiceSearchLoadingState(true);
 return fetch(url, {method: "GET", headers: {"X-Requested-With": "XMLHttpRequest"}, signal: controller?.signal})
  .then(function (response) {
   if (!response.ok) throw new Error("Failed to load service list");
   return response.text();
  })
  .then(function (html) {
   if (requestId !== serviceSearchRequestSequence) return;
   var nextDocument = new DOMParser().parseFromString(html, "text/html");
   if (!nextDocument.getElementById("service-results-area") || !nextDocument.getElementById("service-search-form")) {
    throw new Error("Missing service search content");
   }
   applyServiceListResponse(nextDocument, url, resetSearchControls);
  })
  .catch(function (error) {
   if (requestId !== serviceSearchRequestSequence || error?.name === "AbortError" || serviceSearchPendingUrl) return;
   var message = document.getElementById("service-search-message");
   if (message) {
    message.textContent = "검색 결과를 불러오지 못했습니다. 검색 조건을 다시 변경하면 재시도합니다.";
    message.hidden = false;
   }
  })
  .finally(function () {
   if (requestId !== serviceSearchRequestSequence) return;
   var pendingUrl = serviceSearchPendingUrl;
   serviceSearchRequestController = null;
   serviceSearchRequestInFlight = false;
   serviceSearchRequestUrl = null;
   serviceSearchResetControlsPending = false;
   serviceSearchPendingUrl = null;
   if (pendingUrl) loadServiceListUrl(pendingUrl);
   else setServiceSearchLoadingState(false);
  });
}

function applyServiceListResponse(nextDocument, url, resetSearchControls) {
 var form = document.getElementById("service-search-form");
 var nextForm = nextDocument.getElementById("service-search-form");
 // Preserve the actual form and controls: no blur, cursor restore or IME interruption.
 if (resetSearchControls) {
  ["keyword", "salonKeyword", "region", "sortBy"].forEach(function (name) {
   var input = form.elements.namedItem(name), nextInput = nextForm.elements.namedItem(name);
   if (input && nextInput) input.value = nextInput.value;
  });
  form.querySelectorAll("[data-catalog-range]").forEach(function (root) {
   var nextRoot = nextForm.querySelector("[data-catalog-range='" + root.dataset.catalogRange + "']");
   var input = root.querySelector("[data-catalog-range-input]");
   var nextInput = nextRoot.querySelector("[data-catalog-range-input]");
   root.querySelector("[data-catalog-range-value]").value = nextRoot.querySelector("[data-catalog-range-value]").value;
   input.min = nextInput.min;
   input.max = nextInput.max;
   input.value = nextInput.value;
   root.querySelector("[data-catalog-range-output]").textContent = nextRoot.querySelector("[data-catalog-range-output]").textContent;
   root.dispatchEvent(new Event("catalog-range-sync"));
  });
  form.elements.namedItem("sortBy")?.closest("[data-catalog-select]")?.dispatchEvent(new Event("catalog-select-sync"));
 }
 var requestUrl = new URL(url);
 var currentUrl = new URL(buildServiceSearchUrl(form, requestUrl.searchParams.has("page")));
 if (resetSearchControls || currentUrl.search === requestUrl.search) syncServiceSearchErrors(form, nextForm, resetSearchControls);
 var heading = document.getElementById("service-results-heading");
 var nextHeading = nextDocument.getElementById("service-results-heading");
 if (heading && nextHeading) heading.replaceWith(document.importNode(nextHeading, true));
 document.getElementById("service-results-area").replaceWith(document.importNode(nextDocument.getElementById("service-results-area"), true));
 var message = document.getElementById("service-search-message");
 if (message) { message.textContent = ""; message.hidden = true; }
 if (nextDocument.title) document.title = nextDocument.title;
 window.history.replaceState(null, "", requestUrl.pathname + requestUrl.search + requestUrl.hash);
 bindServicePagination();
}

function syncServiceSearchErrors(form, nextForm, resetSearchControls) {
 form.querySelectorAll("[data-catalog-range]").forEach(function (root) {
  var nextRoot = nextForm.querySelector("[data-catalog-range='" + root.dataset.catalogRange + "']");
  root.dataset.invalid = nextRoot.dataset.invalid;
  root.querySelector(".salon-rating-slider").classList.toggle("is-invalid", nextRoot.dataset.invalid === "true");
  var input = root.querySelector("[data-catalog-range-input]"), nextInput = nextRoot.querySelector("[data-catalog-range-input]");
  input.setAttribute("aria-invalid", nextInput.getAttribute("aria-invalid"));
  if (nextInput.hasAttribute("aria-describedby")) input.setAttribute("aria-describedby", nextInput.getAttribute("aria-describedby"));
  else input.removeAttribute("aria-describedby");
  root.querySelector(".catalog-range__error")?.remove();
  var error = nextRoot.querySelector(".catalog-range__error");
  if (error) root.append(document.importNode(error, true));
 });
 var pageArea = form.querySelector("[data-service-page-error]");
 var nextPageArea = nextForm.querySelector("[data-service-page-error]");
 var pageInput = pageArea?.querySelector("input");
 if (pageArea && nextPageArea) {
  if (!resetSearchControls && pageInput && (nextPageArea.querySelector("input") || document.activeElement === pageInput)) {
   var nextError = nextPageArea.querySelector(".invalid-feedback");
   var error = pageArea.querySelector(".invalid-feedback");
   pageInput.classList.toggle("is-invalid", Boolean(nextError));
   pageInput.setAttribute("aria-invalid", String(Boolean(nextError)));
   if (error) { error.textContent = nextError?.textContent || ""; error.hidden = !nextError; }
  } else {
   pageArea.replaceChildren(...Array.from(nextPageArea.childNodes, function (node) { return document.importNode(node, true); }));
   pageArea.hidden = nextPageArea.hidden;
   form.dispatchEvent(new Event("service-search-bind-fields"));
  }
 }
}

function bindServicePagination() {
 document.querySelectorAll("[data-service-page-link][href]").forEach(function (link) {
  if (link.dataset.servicePageBound === "true") return;
  link.dataset.servicePageBound = "true";
  link.addEventListener("click", function (event) {
   if (event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
   event.preventDefault();
   var form = document.getElementById("service-search-form");
   form.dispatchEvent(new Event("service-search-cancel-pending"));
   var url = new URL(buildServiceSearchUrl(form));
   var page = new URL(link.href).searchParams.get("page");
   if (page) url.searchParams.set("page", page);
   loadServiceListUrl(url.toString());
  });
 });
}

function setServiceSearchLoadingState(loading) {
 document.getElementById("service-search-form")?.classList.toggle("is-live-searching", loading);
 document.getElementById("service-results-area")?.setAttribute("aria-busy", String(loading));
}

function bindServiceDeleteConfirm() {
 var deleteForms = document.querySelectorAll("form[data-service-delete]");
 deleteForms.forEach(function (form) {
  form.addEventListener("submit", function (event) {
   if (!confirm("정말 삭제하시겠습니까?")) {
	event.preventDefault();
   }
  });
 });
}
