var DESIGNER_RESULT_SORT_STORAGE_KEY = "designer-list-result-sort";
var DESIGNER_RESULT_SORT_DEFAULT = "rating";
var restoredDesignerCatalogState = restoreDesignerCatalogState();

document.addEventListener("DOMContentLoaded", function () {
 bindDesignerFormLayout();
 bindDesignerModeSwitch();
 bindDesignerSearchForm();
 bindDesignerAiLiveSearch();
 bindDesignerResultSortSwitch();
 bindDesignerDeleteConfirm();
 if (restoredDesignerCatalogState && getActiveDesignerMode() === "search") {
  document.dispatchEvent(new CustomEvent("designer-mode-change", {detail: {mode: "search"}}));
 }
});

function bindDesignerFormLayout() {
 var page = document.querySelector(".designer-list-page");
 var search = document.getElementById("designer-search-panel");
 var ai = document.getElementById("designer-ai-panel");
 if (!page || !search || !ai) return;
 var panels = [search, ai];
 if (panels.some(function (panel) { return !panel.querySelector(".designer-form-layout__inputs"); })) return;
 var scheduled = null;

 function synchronize() {
  if (scheduled !== null) window.cancelAnimationFrame(scheduled);
  scheduled = null;
  // Measure natural content again so cleared errors and smaller screens can shrink the shared slot.
  page.style.removeProperty("--designer-form-input-height");
  // Compact forms keep both actions above the inputs, so each mode can use its own content height.
  if (window.getComputedStyle(page).getPropertyValue("--designer-sync-input-height").trim() === "0") return;
  var outerWidth = panels.reduce(function (width, panel) {
   return Math.max(width, panel.hidden ? 0 : panel.getBoundingClientRect().width);
  }, 0);
  if (!outerWidth) return;
  var height = 0;
  panels.forEach(function (panel) {
   var previousStyle = panel.getAttribute("style");
   try {
    if (panel.hidden) {
     // Keep the hidden attribute and native controls intact while measuring offscreen.
     panel.style.setProperty("display", "grid", "important");
     panel.style.setProperty("position", "absolute", "important");
     panel.style.setProperty("visibility", "hidden", "important");
     panel.style.setProperty("pointer-events", "none", "important");
     panel.style.setProperty("left", "-100000px", "important");
     panel.style.setProperty("top", "0", "important");
     panel.style.setProperty("width", outerWidth + "px", "important");
    }
    var slot = panel.querySelector(".designer-form-layout__inputs");
    height = Math.max(height, slot.getBoundingClientRect().height, slot.scrollHeight);
   } finally {
    if (previousStyle === null) panel.removeAttribute("style");
    else panel.setAttribute("style", previousStyle);
   }
  });
  if (height > 0) page.style.setProperty("--designer-form-input-height", Math.ceil(height * 64) / 64 + "px");
 }

 function schedule() {
  if (scheduled === null) scheduled = window.requestAnimationFrame(synchronize);
 }

 document.addEventListener("designer-mode-change", synchronize);
 window.addEventListener("resize", schedule);
 var naturalFields = panels.map(function (panel) {
  return panel.querySelector(".designer-search-form__fields, .designer-ai-inline-form__fields");
 });
 if (typeof ResizeObserver === "function") {
  var sizes = new ResizeObserver(schedule);
  naturalFields.forEach(function (field) { if (field) sizes.observe(field); });
 }
 var errors = new MutationObserver(schedule);
 panels.forEach(function (panel) {
  errors.observe(panel.querySelector(".designer-form-layout__inputs"), {
   childList: true, subtree: true, attributes: true, attributeFilter: ["hidden"]
  });
 });
 if (document.fonts) document.fonts.ready.then(schedule);
 synchronize();
}

function bindDesignerModeSwitch() {
 var buttons = document.querySelectorAll(".designer-search-mode-switch__button[data-designer-mode-target]");
 var panels = document.querySelectorAll("[data-designer-mode-panel]");
 if (!buttons.length || !panels.length) return;

 function activateMode(mode) {
  var previousMode = getActiveDesignerMode();
  buttons.forEach(function (button) {
   var isActive = button.dataset.designerModeTarget === mode;
   button.classList.toggle("is-active", isActive);
   button.setAttribute("aria-pressed", String(isActive));
  });

  document.querySelectorAll("[data-designer-mode-panel]").forEach(function (panel) {
   panel.hidden = panel.dataset.designerModePanel !== mode;
  });
  rememberDesignerCatalogState();
  if (previousMode !== mode) {
   document.dispatchEvent(new CustomEvent("designer-mode-change", {detail: {mode: mode}}));
  }
 }

 buttons.forEach(function (button) {
  button.addEventListener("click", function () {
   activateMode(button.dataset.designerModeTarget);
  });
 });
 if (restoredDesignerCatalogState) activateMode(restoredDesignerCatalogState.mode);
 document.addEventListener("input", rememberFormState);
 document.addEventListener("change", rememberFormState);
 function rememberFormState(event) {
  if (event.target.closest(".designer-ai-inline-form, #designer-search-panel")) rememberDesignerCatalogState();
 }
}

function getActiveDesignerMode() {
 var activeButton = document.querySelector(".designer-search-mode-switch__button.is-active[data-designer-mode-target]");
 return activeButton ? activeButton.dataset.designerModeTarget : "search";
}

function designerSearchFilterKey(url) {
 var filters = new URLSearchParams(url.search);
 filters.delete("page");
 return filters.toString();
}

function buildDesignerModeUrl(mode) {
 var form = document.querySelector(mode === "ai" ? ".designer-ai-inline-form" : "#designer-search-panel");
 if (!form) return null;
 var url = buildDesignerSearchUrl(form);
 url.searchParams.set("mode", mode);
 if (mode !== "ai") {
  var pageField = form.elements.namedItem("page");
  var results = document.getElementById("designer-results-area");
  if (pageField) url.searchParams.set("page", pageField.value.trim());
  else if (form.dataset.designerResultsKey === designerSearchFilterKey(url) && results && Number(results.dataset.currentPage) > 1) {
   url.searchParams.set("page", results.dataset.currentPage);
  }
 }
 return url;
}

function captureDesignerForm(form) {
 var values = {};
 new FormData(form).forEach(function (value, name) { values[name] = String(value); });
 return {values: values, ranges: Array.from(form.querySelectorAll("[data-catalog-range]")).map(function (range) {
  var input = range.querySelector("[data-catalog-range-input]");
  var error = range.querySelector(".invalid-feedback");
  return {name: range.dataset.catalogRange, value: input.value, max: input.max,
   label: range.querySelector("[data-catalog-range-output]").textContent,
   invalid: range.dataset.invalid === "true", error: error && !error.hidden ? error.textContent : ""};
 })};
}

function rememberDesignerCatalogState() {
 var search = document.getElementById("designer-search-panel");
 var ai = document.querySelector(".designer-ai-inline-form");
 if (!search || !ai) return;
 var searchUrl = buildDesignerModeUrl("search");
 var state = {version: 1, url: window.location.href, mode: getActiveDesignerMode(),
  search: captureDesignerForm(search), ai: captureDesignerForm(ai),
  searchPage: searchUrl.searchParams.get("page") || "1"};
 try {
  var previousState = window.history.state;
  if (previousState && JSON.stringify(previousState.designerCatalog) === JSON.stringify(state)) return;
  var historyState = Object.assign({}, previousState, {designerCatalog: state});
  window.history.replaceState(historyState, "");
 } catch (error) {
  // History storage failure must not interrupt editing or mode changes.
 }
}

function restoreDesignerCatalogState() {
 var state = window.history.state && window.history.state.designerCatalog;
 var search = document.getElementById("designer-search-panel");
 var ai = document.querySelector(".designer-ai-inline-form");
 if (!state || state.version !== 1 || state.url !== window.location.href || !search || !ai
  || (state.mode !== "ai" && state.mode !== "search") || !state.search || !state.ai) return null;
 restoreForm(search, state.search);
 restoreForm(ai, state.ai);
 var results = document.getElementById("designer-results-area");
 if (results) results.dataset.currentPage = state.searchPage;
 search.dataset.designerResultsKey = designerSearchFilterKey(buildDesignerSearchUrl(search));
 var validation = document.getElementById("designer-ai-validation");
 if (validation) { validation.replaceChildren(); validation.hidden = true; }
 ai.querySelectorAll("[aria-invalid]").forEach(function (field) {
  field.setAttribute("aria-invalid", "false");
  field.classList.remove("is-invalid");
 });
 return state;

 function restoreForm(form, saved) {
  Object.keys(saved.values || {}).forEach(function (name) {
   if (name === "mode" || name === "searched") return;
   var field = form.elements.namedItem(name);
   if (!field) return;
   var value = saved.values[name];
   if (field.tagName === "SELECT" && !Array.from(field.options).some(function (option) { return option.value === value; })) {
    var option = document.createElement("option");
    option.value = value;
    option.textContent = value;
    field.appendChild(option);
   }
   field.value = value;
  });
  (saved.ranges || []).forEach(function (savedRange) {
   var range = Array.from(form.querySelectorAll("[data-catalog-range]")).find(function (node) {
    return node.dataset.catalogRange === savedRange.name;
   });
   if (!range) return;
   var input = range.querySelector("[data-catalog-range-input]");
   input.step = "any";
   input.max = savedRange.max;
   input.value = savedRange.value;
   range.querySelector("[data-catalog-range-output]").textContent = savedRange.label;
   range.dataset.invalid = String(savedRange.invalid);
   input.setAttribute("aria-invalid", String(savedRange.invalid));
   input.setAttribute("aria-valuetext", savedRange.label);
   range.querySelector(".salon-rating-slider").classList.toggle("is-invalid", savedRange.invalid);
   range.querySelector("[data-catalog-range-value]").classList.toggle("is-invalid", savedRange.invalid);
   var error = range.querySelector(".invalid-feedback");
   if (savedRange.error && !error) {
    error = document.createElement("p");
    error.className = "invalid-feedback catalog-range__error";
    error.id = savedRange.name + "Error";
    error.setAttribute("role", "alert");
    range.appendChild(error);
   }
   if (error) { error.textContent = savedRange.error; error.hidden = !savedRange.error; }
   if (savedRange.invalid && savedRange.error) input.setAttribute("aria-describedby", error.id);
   else input.removeAttribute("aria-describedby");
  });
 }
}

function bindDesignerAiLiveSearch() {
 var form = document.querySelector(".designer-ai-inline-form");
 if (!form || form.dataset.aiLiveSearchBound === "true") return;
 var query = form.querySelector("[name='query']");
 var validation = document.getElementById("designer-ai-validation");
 var results = document.getElementById("designer-ai-results-area");
 var content = document.getElementById("designer-ai-result-content");
 var status = document.getElementById("designer-ai-status");
 if (!query || !validation || !results || !content || !status) return;
 form.dataset.aiLiveSearchBound = "true";

 var timer = null;
 var composing = false;
 var revision = 0;
 var activeRequest = null;
 var pending = null;
 var completedKey = content.dataset.complete === "true" && content.dataset.query === snapshot().query ? snapshot().key : null;

 function snapshot() {
  var text = query.value.trim();
  return {query: text, key: text, revision: revision};
 }

 function isCurrent(request) {
  return getActiveDesignerMode() === "ai" && !composing && request.revision === revision && request.key === snapshot().key;
 }

 function say(message, state) {
  status.textContent = message;
  status.hidden = !message;
  status.dataset.state = state || "";
 }

 function loading(busy) {
  results.setAttribute("aria-busy", String(busy));
  form.classList.toggle("is-ai-searching", busy);
  if (busy) say("추천을 불러오는 중입니다.", "loading");
  else if (status.dataset.state === "loading") say("");
 }

 function syncInvalidFields() {
  var invalid = Boolean(validation.querySelector("[data-designer-ai-field]"));
  query.setAttribute("aria-invalid", String(invalid));
  query.classList.toggle("is-invalid", invalid);
  validation.hidden = !validation.children.length;
 }

 function validate() {
  var errors = [];
  if (query.value.length > 300) errors.push(["query", "추천 요청은 300자 이내로 입력해 주세요."]);
  validation.replaceChildren();
  errors.forEach(function (error) {
   var message = document.createElement("p");
   message.className = "invalid-feedback";
   message.dataset.designerAiField = error[0];
   message.textContent = error[1];
   validation.appendChild(message);
  });
  syncInvalidFields();
  return errors.length === 0;
 }

 function clearResults() {
  content.replaceChildren();
  content.hidden = true;
  content.dataset.complete = "false";
  content.dataset.query = "";
  completedKey = null;
 }

 function urlFor(request) {
  var url = new URL(form.getAttribute("action") || "/designers", window.location.origin);
  url.searchParams.set("mode", "ai");
  if (request.query) url.searchParams.set("query", request.query);
  return url;
 }

 function invalidate(revokeActive) {
  revision += 1;
  window.clearTimeout(timer);
  timer = null;
  pending = null;
  loading(false);
  say("");
  if (revokeActive && activeRequest) activeRequest.reusable = false;
  // Let an existing request finish: aborting fetch cannot cancel its server-side AI call.
 }

 function schedule(delay) {
  invalidate();
  if (getActiveDesignerMode() !== "ai" || composing) return;
  var valid = validate();
  var current = snapshot();
  rememberDesignerCatalogState();
  if (!current.query) {
   if (activeRequest) activeRequest.reusable = false;
   clearResults();
   return;
  }
  if (!valid) return;
  if (current.key === completedKey) {
   return;
  }
  if (activeRequest && activeRequest.reusable && activeRequest.key === current.key) {
   activeRequest.revision = revision;
   loading(true);
   return;
  }
  timer = window.setTimeout(function () {
   timer = null;
   if (!isCurrent(current)) return;
   pending = current;
   flush();
  }, delay);
 }

 function flush() {
  if (activeRequest || !pending) return;
  var request = pending;
  pending = null;
  if (!isCurrent(request) || request.key === completedKey) return;
  activeRequest = request;
  request.reusable = true;
  var url = urlFor(request);
  loading(true);

  fetch(url.toString(), {method: "GET", headers: {"X-Requested-With": "XMLHttpRequest"}})
   .then(function (response) {
    if (!response.ok) throw new Error("Designer AI search failed: " + response.status);
    return response.text();
   })
   .then(function (html) {
    if (!isCurrent(request)) return;
    var doc = new DOMParser().parseFromString(html, "text/html");
    var nextValidation = doc.getElementById("designer-ai-validation");
    var nextContent = doc.getElementById("designer-ai-result-content");
    if (!doc.getElementById("designer-ai-results-area") || !nextValidation || !nextContent) {
     throw new Error("Designer AI response was incomplete.");
    }
    if (nextContent.dataset.complete !== "true" && !nextValidation.children.length) {
     throw new Error("Designer AI result was not found.");
    }
    // Never replace or write to the controls: keep the native caret, selection, scroll and IME state.
    validation.replaceChildren.apply(validation, Array.from(nextValidation.childNodes).map(function (node) {
     return document.importNode(node, true);
    }));
    syncInvalidFields();
    content.replaceChildren.apply(content, Array.from(nextContent.childNodes).map(function (node) {
     return document.importNode(node, true);
    }));
    content.hidden = nextContent.hidden;
    content.dataset.complete = nextContent.dataset.complete;
    content.dataset.query = nextContent.dataset.query || "";
    completedKey = nextContent.dataset.complete === "true" && !validation.children.length ? request.key : null;
    say("");
    rememberDesignerCatalogState();
   })
   .catch(function () {
    if (!isCurrent(request)) return;
    say("추천 결과를 불러오지 못했습니다. 추천 요청 문구를 변경하면 다시 시도합니다.", "error");
   })
   .finally(function () {
    activeRequest = null;
    loading(false);
    flush();
   });
 }

 query.addEventListener("compositionstart", function () {
  composing = true;
  invalidate();
 });
 query.addEventListener("compositionend", function () {
  composing = false;
  schedule(1000);
 });
 query.addEventListener("input", function (event) {
  if (composing || event.isComposing) {
   invalidate();
   return;
  }
  schedule(1000);
 });
 form.addEventListener("submit", function (event) {
  event.preventDefault();
  schedule(0);
 });
 var reset = form.querySelector(".catalog-search-reset");
 if (reset) reset.addEventListener("click", function (event) {
  if (event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
  event.preventDefault();
  invalidate(true);
  composing = false;
  query.value = "";
  validate();
  clearResults();
  rememberDesignerCatalogState();
 });
 document.addEventListener("designer-mode-change", function (event) {
  if (event.detail.mode === "ai") {
   if (!validation.children.length) schedule(1000);
  }
  else invalidate(true);
 });
 if (getActiveDesignerMode() === "ai" && !completedKey && !validation.children.length) {
  schedule(1000);
 }
}

function bindDesignerSearchForm() {
 var forms = document.querySelectorAll(".designer-search-form");
 if (!forms.length) return;

 forms.forEach(function (form) {
  if (form.dataset.designerLiveSearch === "true") {
   bindDesignerLiveSearch(form);
   return;
  }

  bindDesignerEnterSubmit(form);
 });
}

function bindDesignerEnterSubmit(form) {
 form.querySelectorAll("input, select").forEach(function (field) {
  field.addEventListener("keypress", function (event) {
   if (event.key === "Enter") {
	event.preventDefault();
	form.submit();
   }
  });
 });
}

function bindDesignerLiveSearch(form) {
 var fields = form.querySelectorAll(".designer-search-form__fields input:not([type='hidden']), .designer-search-form__fields select, input[name='page']:not([type='hidden'])");
 var debounceTimer = null;
 var abortController = null;
 var requestSequence = 0;
 var isComposing = false;
 var resumeSearchAfterRangeDrag = false;
 form.dataset.designerResultsKey = designerSearchFilterKey(buildDesignerSearchUrl(form));

 function cancelSearch() {
  window.clearTimeout(debounceTimer);
  debounceTimer = null;
  requestSequence += 1;
  if (abortController) abortController.abort();
  abortController = null;
  var resultsArea = document.getElementById("designer-results-area");
  if (resultsArea) resultsArea.setAttribute("aria-busy", "false");
  form.classList.remove("is-live-searching");
 }

 form.addEventListener("catalog-range-drag-start", function () {
  resumeSearchAfterRangeDrag = resumeSearchAfterRangeDrag || debounceTimer !== null;
  cancelSearch();
 });
 form.addEventListener("catalog-range-drag-end", function () {
  if (resumeSearchAfterRangeDrag) {
   resumeSearchAfterRangeDrag = false;
   if (!isComposing) scheduleSearch();
  }
 });

 function scheduleSearch() {
  cancelSearch();
  if (getActiveDesignerMode() !== "search" || isComposing) return;
  debounceTimer = window.setTimeout(function () {
   debounceTimer = null;
   runSearch();
  }, 280);
 }

 function runSearch(preservePage, requestedPage) {
  cancelSearch();
  if (getActiveDesignerMode() !== "search" || isComposing) return;
  resumeSearchAfterRangeDrag = false;
  var resultsArea = document.getElementById("designer-results-area");
  if (!resultsArea) {
   form.submit();
   return;
  }

  var url = preservePage ? buildDesignerModeUrl("search") : buildDesignerSearchUrl(form);
  if (requestedPage) url.searchParams.set("page", requestedPage);
  abortController = typeof AbortController === "function" ? new AbortController() : null;
  var currentRequest = requestSequence;

  resultsArea.setAttribute("aria-busy", "true");
  form.classList.add("is-live-searching");

  fetch(url.toString(), {
   method: "GET",
   headers: {
	"X-Requested-With": "XMLHttpRequest"
   },
   signal: abortController ? abortController.signal : undefined
  })
	  .then(function (response) {
	   if (!response.ok) {
		throw new Error("Designer search failed: " + response.status);
	   }
	   return response.text();
	  })
	  .then(function (html) {
	   if (requestSequence !== currentRequest || getActiveDesignerMode() !== "search") return;
	   var parser = new DOMParser();
	   var doc = parser.parseFromString(html, "text/html");
	   var nextResultsArea = doc.getElementById("designer-results-area");

	   if (!nextResultsArea) {
		throw new Error("Designer results area was not found.");
	   }

	   nextResultsArea.hidden = getActiveDesignerMode() !== "search";
	   resultsArea.replaceWith(nextResultsArea);
	   form.dataset.designerResultsKey = designerSearchFilterKey(url);
	   bindDesignerResultSortSwitch();
	   rememberDesignerCatalogState();
	  })
	  .catch(function (error) {
	   if (requestSequence !== currentRequest || getActiveDesignerMode() !== "search") return;
	   if (error.name === "AbortError") {
		return;
	   }
	   var message = document.createElement("div");
	   message.className = "catalog-validation";
	   message.dataset.designerSearchError = "true";
	   message.setAttribute("role", "alert");
	   message.textContent = "검색 결과를 불러오지 못했습니다. 검색 조건을 변경하면 다시 시도합니다.";
	   var previousError = resultsArea.querySelector("[data-designer-search-error]");
	   if (previousError) previousError.replaceWith(message);
	   else resultsArea.prepend(message);
	  })
	  .finally(function () {
	   if (requestSequence !== currentRequest) {
		return;
	   }
	   abortController = null;
	   var latestResultsArea = document.getElementById("designer-results-area");
	   if (latestResultsArea) {
		latestResultsArea.setAttribute("aria-busy", "false");
	   }
	   form.classList.remove("is-live-searching");
	  });
 }

 fields.forEach(function (field) {
  field.addEventListener("compositionstart", function () {
   isComposing = true;
   cancelSearch();
  });

  field.addEventListener("compositionend", function () {
   isComposing = false;
   scheduleSearch();
  });

  field.addEventListener("input", function () {
   var range = field.closest("[data-catalog-range]");
   if (!isComposing && (!range || range.dataset.dragging !== "true")) {
	scheduleSearch();
   } else cancelSearch();
  });

  field.addEventListener("change", function () {
   if (!isComposing) {
	if (field.matches("[data-catalog-range-input]")) {
	 window.clearTimeout(debounceTimer);
	 runSearch();
	} else scheduleSearch();
   }
  });

  field.addEventListener("keydown", function (event) {
   if (isComposing || event.isComposing || event.keyCode === 229) return;
   if (event.key === "Enter") {
	event.preventDefault();
	window.clearTimeout(debounceTimer);
	runSearch();
   }
  });
 });

 form.addEventListener("submit", function (event) {
  event.preventDefault();
  window.clearTimeout(debounceTimer);
  runSearch();
 });
 var reset = form.querySelector(".catalog-search-reset");
 if (reset) reset.addEventListener("click", function (event) {
  if (event.defaultPrevented || event.button !== 0
   || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
  cancelSearch();
  if (reset.href !== window.location.href) return;
  // Same-address navigation can retain this entry's state; an explicit reset must discard its draft.
  try {
   var state = Object.assign({}, window.history.state);
   delete state.designerCatalog;
   window.history.replaceState(state, "");
  } catch (error) {
   // Preserve the normal reset link when history storage is unavailable.
  }
 });
 document.addEventListener("click", function (event) {
  var link = event.target.closest("#designer-results-area a[data-designer-page-link][href]");
  if (!link || event.defaultPrevented || event.button !== 0
   || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey
   || getActiveDesignerMode() !== "search") return;
  var requestedPage = new URL(link.href).searchParams.get("page");
  if (!requestedPage || !/^[1-9]\d*$/.test(requestedPage)) return;
  event.preventDefault();
  var pageField = form.elements.namedItem("page");
  if (pageField) pageField.value = requestedPage;
  runSearch(false, requestedPage);
 });
 document.addEventListener("designer-mode-change", function (event) {
  if (event.detail.mode === "search") runSearch(true);
  else {
   resumeSearchAfterRangeDrag = false;
   cancelSearch();
  }
 });
}

function buildDesignerSearchUrl(form) {
 var url = new URL(form.getAttribute("action") || window.location.pathname, window.location.origin);
 var formData = new FormData(form);

 formData.forEach(function (value, key) {
  var stringValue = String(value).trim();
  if (key === "searched" || stringValue.length > 0) {
   url.searchParams.append(key, stringValue);
  }
 });

 return url;
}

function bindDesignerResultSortSwitch() {
 var resultsArea = document.getElementById("designer-results-area");
 if (!resultsArea) return;

 var sortControl = resultsArea.querySelector("[data-designer-result-sort]");
 var panels = resultsArea.querySelectorAll("[data-designer-sort-panel]");
 if (!sortControl || !panels.length) return;
 var sortWidget = sortControl.closest("[data-catalog-select]");
 if (sortWidget) bindCatalogSelect(sortWidget);

 function normalizeSort(value) {
  return value === "likes" || value === "newest" ? value : DESIGNER_RESULT_SORT_DEFAULT;
 }

 function applyResultSort(value) {
  var nextSort = normalizeSort(value);
  sortControl.value = nextSort;
  if (sortWidget) sortWidget.dispatchEvent(new Event("catalog-select-sync"));

  panels.forEach(function (panel) {
   panel.hidden = panel.dataset.designerSortPanel !== nextSort;
  });

  window.sessionStorage.setItem(DESIGNER_RESULT_SORT_STORAGE_KEY, nextSort);
 }

 sortControl.addEventListener("change", function () {
  applyResultSort(sortControl.value);
 });

 applyResultSort(window.sessionStorage.getItem(DESIGNER_RESULT_SORT_STORAGE_KEY));
}

function bindDesignerDeleteConfirm() {
 var deleteForms = document.querySelectorAll("form[data-designer-delete-form]");
 if (!deleteForms.length) return;

 deleteForms.forEach(function (form) {
  form.addEventListener("submit", function (event) {
   if (!confirm("정말 이 디자이너를 삭제하시겠습니까?")) {
	event.preventDefault();
   }
  });
 });
}
