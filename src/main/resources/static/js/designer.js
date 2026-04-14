var DESIGNER_RESULT_SORT_STORAGE_KEY = "designer-list-result-sort";
var DESIGNER_RESULT_SORT_DEFAULT = "rating";

document.addEventListener("DOMContentLoaded", function () {
    bindDesignerModeSwitch();
    bindDesignerSearchForm();
    bindDesignerResultSortSwitch();
    bindDesignerDeleteConfirm();
});

function bindDesignerModeSwitch() {
    var buttons = document.querySelectorAll(".designer-search-mode-switch__button[data-designer-mode-target]");
    var panels = document.querySelectorAll("[data-designer-mode-panel]");
    if (!buttons.length || !panels.length) return;

    function activateMode(mode) {
        buttons.forEach(function (button) {
            var isActive = button.dataset.designerModeTarget === mode;
            button.classList.toggle("is-active", isActive);
            button.setAttribute("aria-pressed", String(isActive));
        });

        panels.forEach(function (panel) {
            panel.hidden = panel.dataset.designerModePanel !== mode;
        });
    }

    buttons.forEach(function (button) {
        button.addEventListener("click", function () {
            activateMode(button.dataset.designerModeTarget);
        });
    });
}

function getActiveDesignerMode() {
    var activeButton = document.querySelector(".designer-search-mode-switch__button.is-active[data-designer-mode-target]");
    return activeButton ? activeButton.dataset.designerModeTarget : "search";
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
    var fields = form.querySelectorAll(".designer-search-form__fields input, .designer-search-form__fields select");
    var debounceTimer = null;
    var abortController = null;
    var isComposing = false;

    function scheduleSearch() {
        window.clearTimeout(debounceTimer);
        debounceTimer = window.setTimeout(function () {
            runSearch();
        }, 280);
    }

    function runSearch() {
        var resultsArea = document.getElementById("designer-results-area");
        if (!resultsArea) {
            form.submit();
            return;
        }

        var url = buildDesignerSearchUrl(form);
        if (abortController) {
            abortController.abort();
        }
        abortController = new AbortController();
        var currentController = abortController;

        resultsArea.setAttribute("aria-busy", "true");
        form.classList.add("is-live-searching");

        fetch(url.toString(), {
            method: "GET",
            headers: {
                "X-Requested-With": "XMLHttpRequest"
            },
            signal: abortController.signal
        })
            .then(function (response) {
                if (!response.ok) {
                    throw new Error("Designer search failed: " + response.status);
                }
                return response.text();
            })
            .then(function (html) {
                var parser = new DOMParser();
                var doc = parser.parseFromString(html, "text/html");
                var nextResultsArea = doc.getElementById("designer-results-area");

                if (!nextResultsArea) {
                    throw new Error("Designer results area was not found.");
                }

                nextResultsArea.hidden = getActiveDesignerMode() !== "search";
                resultsArea.replaceWith(nextResultsArea);
                bindDesignerResultSortSwitch();
            })
            .catch(function (error) {
                if (error.name === "AbortError") {
                    return;
                }
                window.location.assign(url.toString());
            })
            .finally(function () {
                if (abortController !== currentController) {
                    return;
                }
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
        });

        field.addEventListener("compositionend", function () {
            isComposing = false;
            scheduleSearch();
        });

        field.addEventListener("input", function () {
            if (!isComposing) {
                scheduleSearch();
            }
        });

        field.addEventListener("change", function () {
            if (!isComposing) {
                scheduleSearch();
            }
        });

        field.addEventListener("keydown", function (event) {
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

    function normalizeSort(value) {
        return value === "likes" || value === "newest" ? value : DESIGNER_RESULT_SORT_DEFAULT;
    }

    function applyResultSort(value) {
        var nextSort = normalizeSort(value);
        sortControl.value = nextSort;

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
