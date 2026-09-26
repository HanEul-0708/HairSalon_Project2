// Keep the native select as the submitted value and as a fallback without JavaScript.
function bindCatalogSelect(root) {
 if (!root || root.dataset.catalogSelectBound === "true") return;
 var select = root.querySelector("select");
 var trigger = root.querySelector(".catalog-select__trigger");
 var label = root.querySelector("[data-catalog-select-label]");
 var menu = root.querySelector(".catalog-select__menu");
 if (!select || !trigger || !label || !menu || !select.options.length) return;
 root.dataset.catalogSelectBound = "true";
 var activeIndex = Math.max(0, select.selectedIndex);
 var options = Array.from(select.options).map(function (option, index) {
  var item = document.createElement("li");
  item.id = menu.id + "-" + option.value;
  item.className = "catalog-select__option";
  item.setAttribute("role", "option");
  item.dataset.value = option.value;
  item.textContent = option.textContent;
  item.addEventListener("pointermove", function () { setActive(index); });
  // Keep keyboard focus on the combobox while the pointer chooses an option.
  item.addEventListener("mousedown", function (event) { event.preventDefault(); });
  item.addEventListener("click", function () { commit(index); });
  menu.appendChild(item);
  return item;
 });

 function syncSelection() {
  var selected = select.options[select.selectedIndex];
  label.textContent = selected ? selected.textContent : "";
  options.forEach(function (item, index) {
   item.setAttribute("aria-selected", String(index === select.selectedIndex));
  });
 }

 function setActive(index) {
  activeIndex = Math.max(0, Math.min(options.length - 1, index));
  options.forEach(function (item, optionIndex) {
   item.classList.toggle("is-active", optionIndex === activeIndex);
  });
  trigger.setAttribute("aria-activedescendant", options[activeIndex].id);
 }

 function open() {
  menu.hidden = false;
  root.classList.add("is-open");
  trigger.setAttribute("aria-expanded", "true");
  setActive(Math.max(0, select.selectedIndex));
 }

 function close() {
  menu.hidden = true;
  root.classList.remove("is-open");
  trigger.setAttribute("aria-expanded", "false");
  trigger.removeAttribute("aria-activedescendant");
 }

 function commit(index) {
  var changed = select.selectedIndex !== index;
  select.selectedIndex = index;
  syncSelection();
  close();
  trigger.focus({preventScroll: true});
  if (changed) select.dispatchEvent(new Event("change", {bubbles: true}));
 }

 trigger.addEventListener("click", function () { menu.hidden ? open() : close(); });
 trigger.addEventListener("keydown", function (event) {
  if (event.key === "Escape" || event.key === "Tab") {
   if (!menu.hidden && event.key === "Escape") event.preventDefault();
   close();
   return;
  }
  if (event.key === "Enter" || event.key === " ") {
   event.preventDefault();
   menu.hidden ? open() : commit(activeIndex);
   return;
  }
  if (["ArrowDown", "ArrowUp", "Home", "End"].includes(event.key)) {
   event.preventDefault();
   var wasClosed = menu.hidden;
   if (wasClosed) open();
   if (event.key === "Home") setActive(0);
   else if (event.key === "End") setActive(options.length - 1);
   else if (!wasClosed) setActive(activeIndex + (event.key === "ArrowDown" ? 1 : -1));
   return;
  }
  if (event.key.length === 1 && !event.ctrlKey && !event.metaKey && !event.altKey && !event.isComposing) {
   var match = options.findIndex(function (item) { return item.textContent.startsWith(event.key); });
   if (match >= 0) {
    event.preventDefault();
    if (menu.hidden) open();
    setActive(match);
   }
  }
 });
 root.addEventListener("focusout", function (event) {
  if (!root.contains(event.relatedTarget)) close();
 });
 root.addEventListener("catalog-select-close", close);
 select.addEventListener("change", syncSelection);
 syncSelection();
 select.hidden = true;
 trigger.hidden = false;
}

// One document listener survives AJAX form replacement without retaining old forms.
document.addEventListener("pointerdown", function (event) {
 document.querySelectorAll("[data-catalog-select].is-open").forEach(function (root) {
  if (!root.contains(event.target)) root.dispatchEvent(new Event("catalog-select-close"));
 });
});

