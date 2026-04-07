document.addEventListener("DOMContentLoaded", function () {
    bindDesignerSearchForm();
    bindDesignerCardFocusEffect();
    bindDesignerDeleteConfirm();
});

function bindDesignerSearchForm() {
    var forms = document.querySelectorAll(".designer-search-form");
    if (!forms.length) return;

    forms.forEach(function (form) {
        var inputs = form.querySelectorAll("input");
        inputs.forEach(function (input) {
            input.addEventListener("keypress", function (event) {
                if (event.key === "Enter") {
                    event.preventDefault();
                    form.submit();
                }
            });
        });
    });
}

function bindDesignerCardFocusEffect() {
    var links = document.querySelectorAll(".designer-card a, .designer-table a");
    links.forEach(function (link) {
        link.addEventListener("focus", function () {
            var card = link.closest(".designer-card");
            if (card) {
                card.style.transform = "translateY(-4px)";
                card.style.boxShadow = "var(--shadow-md)";
            }
        });

        link.addEventListener("blur", function () {
            var card = link.closest(".designer-card");
            if (card) {
                card.style.transform = "";
                card.style.boxShadow = "";
            }
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
