document.addEventListener("DOMContentLoaded", function () {
 bindServiceSearchForm();
 bindServiceDeleteConfirm();
});

function bindServiceSearchForm() {
 var form = document.querySelector(".service-search-form");
 if (!form) return;

 form.querySelectorAll("input, select").forEach(function (field) {
  field.addEventListener("keypress", function (event) {
   if (event.key === "Enter") {
	event.preventDefault();
	form.submit();
   }
  });
 });
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
