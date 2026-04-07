document.addEventListener("DOMContentLoaded", function () {
    bindSalonSearchForms();
    bindSalonCardFocusEffect();
});

function bindSalonSearchForms() {
    var forms = document.querySelectorAll(".salon-search-form");
    if (!forms.length) return;

    forms.forEach(function (form) {
        var inputs = form.querySelectorAll("input, select");
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
