(function () {
 "use strict";

 function formatValue(value, unit) {
  return (unit === "점" ? Number(value).toFixed(1) : BigInt(value).toLocaleString("ko-KR")) + unit;
 }

 function bindCatalogRange(root) {
  if (root.dataset.catalogRangeBound === "true") return;
  var input = root.querySelector("[data-catalog-range-input]");
  var hidden = root.querySelector("[data-catalog-range-value]");
  var output = root.querySelector("[data-catalog-range-output]");
  var control = root.querySelector(".salon-rating-slider__control");
  if (!input || !hidden || !output || !control) return;
  root.dataset.catalogRangeBound = "true";

  var emptyValue = Number(root.dataset.emptyValue);
  var step = Number(root.dataset.step || input.dataset.step || input.step);
  var unit = root.dataset.unit || "";
  var keyboardValue = null;
  // Preserve an existing URL value even when it falls between the usual steps.
  input.step = "any";

  function paint(commit) {
   var value = Number(input.value);
   if (commit) {
    hidden.value = value === emptyValue ? "" : keyboardValue !== null ? keyboardValue
     : value === Number(input.max) ? input.max : String(value);
    root.dataset.invalid = "false";
    input.setAttribute("aria-invalid", "false");
    hidden.classList.remove("is-invalid");
    root.querySelector(".salon-rating-slider").classList.remove("is-invalid");
    root.querySelectorAll(".invalid-feedback").forEach(function (error) { error.hidden = true; });
   }
   var invalid = root.dataset.invalid === "true";
   var hasValue = hidden.value !== "" && !invalid;
   var text = commit ? (invalid ? "입력 오류" : hasValue ? formatValue(hidden.value, unit) : "전체")
    : output.textContent.trim();
   output.textContent = text;
   output.title = text;
   input.setAttribute("aria-valuetext", text);
   var min = Number(input.min), max = Number(input.max);
   var percent = max > min ? (value - min) / (max - min) * 100 : 0;
   control.style.setProperty("--rating-progress", Math.max(0, Math.min(100, percent)) + "%");
  }

  function beginDrag() {
   root.dataset.dragging = "true";
   root.dispatchEvent(new CustomEvent("catalog-range-drag-start", {bubbles: true}));
  }
  function endDrag() {
   if (root.dataset.dragging !== "true") return;
   root.dataset.dragging = "false";
   root.dispatchEvent(new CustomEvent("catalog-range-drag-end", {bubbles: true}));
  }
  input.addEventListener("pointerdown", beginDrag);
  input.addEventListener("pointerup", endDrag);
  input.addEventListener("pointercancel", endDrag);
  input.addEventListener("blur", endDrag);
  input.addEventListener("input", function () {
   var min = Number(input.min), max = Number(input.max);
   var value = Number(input.value);
   if (keyboardValue === null && value !== min && value !== max) {
    var rounded = min + Math.round((value - min) / step) * step;
    input.value = String(Math.max(min, Math.min(max, Number(rounded.toFixed(10)))));
   }
   paint(true);
  });
  input.addEventListener("change", function () { endDrag(); paint(true); });
  root.addEventListener("catalog-range-sync", function () { paint(false); });
  input.addEventListener("keydown", function (event) {
   var direction = {ArrowRight: 1, ArrowUp: 1, ArrowLeft: -1, ArrowDown: -1}[event.key];
   if (!direction && event.key !== "Home" && event.key !== "End") return;
   event.preventDefault();
   var min = Number(input.min), max = Number(input.max), value = Number(input.value);
   if (unit !== "점") {
    var minInteger = BigInt(input.min), maxInteger = BigInt(input.max), stepInteger = BigInt(step);
    var currentInteger = root.dataset.invalid !== "true" && /^-?\d+$/.test(hidden.value)
     ? BigInt(hidden.value) : BigInt(Math.round(value));
    var offset = currentInteger - minInteger;
    var indexInteger = offset / stepInteger;
    var nextInteger = event.key === "Home" ? minInteger : event.key === "End" ? maxInteger
     : minInteger + (direction > 0 ? indexInteger + 1n : indexInteger - (offset % stepInteger === 0n ? 1n : 0n)) * stepInteger;
    if (nextInteger < minInteger) nextInteger = minInteger;
    if (nextInteger > maxInteger) nextInteger = maxInteger;
    if (nextInteger === currentInteger && root.dataset.invalid !== "true") return;
    keyboardValue = String(nextInteger);
   } else {
    var index = (value - min) / step;
    var next = event.key === "Home" ? min : event.key === "End" ? max
     : min + (direction > 0 ? Math.floor(index + 1e-9) + 1 : Math.ceil(index - 1e-9) - 1) * step;
    next = Number(Math.max(min, Math.min(max, next)).toFixed(10));
    if (next === value && root.dataset.invalid !== "true") return;
    keyboardValue = String(next);
   }
   input.value = keyboardValue;
   input.dispatchEvent(new Event("input", {bubbles: true}));
   input.dispatchEvent(new Event("change", {bubbles: true}));
   keyboardValue = null;
  });
  paint(false);
 }

 window.bindCatalogRanges = function (scope) {
  (scope || document).querySelectorAll("[data-catalog-range]").forEach(bindCatalogRange);
 };
 document.addEventListener("DOMContentLoaded", function () { window.bindCatalogRanges(document); });
})();
