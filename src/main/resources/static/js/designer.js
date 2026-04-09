document.addEventListener("DOMContentLoaded", function () {
    bindDesignerSearchForm();
    bindDesignerDeleteConfirm();
});

function bindDesignerSearchForm() {
    var forms = document.querySelectorAll(".designer-search-form");
    if (!forms.length) return;

    forms.forEach(function (form) {
        form.querySelectorAll("input, select").forEach(function (field) {
            field.addEventListener("keypress", function (event) {
                if (event.key === "Enter") {
                    event.preventDefault();
                    form.submit();
                }
            });
        });
    });
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
