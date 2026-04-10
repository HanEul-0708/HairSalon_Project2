document.addEventListener('DOMContentLoaded', function () {
    var scrollStorageKey = 'admin-pagination-scroll-state';

    function finishScrollRestore() {
        document.documentElement.classList.remove('admin-scroll-restoring');
    }

    function restorePaginationScroll() {
        var savedState;

        try {
            savedState = sessionStorage.getItem(scrollStorageKey);
        } catch (e) {
            finishScrollRestore();
            return;
        }

        if (!savedState) {
            finishScrollRestore();
            return;
        }

        var parsedState;
        try {
            parsedState = JSON.parse(savedState);
        } catch (e) {
            sessionStorage.removeItem(scrollStorageKey);
            finishScrollRestore();
            return;
        }

        if (!parsedState || parsedState.path !== window.location.pathname) {
            sessionStorage.removeItem(scrollStorageKey);
            finishScrollRestore();
            return;
        }

        sessionStorage.removeItem(scrollStorageKey);

        var scrollY = parseInt(parsedState.scrollY, 10);
        if (Number.isNaN(scrollY)) {
            finishScrollRestore();
            return;
        }

        requestAnimationFrame(function () {
            window.scrollTo(0, scrollY);
            requestAnimationFrame(finishScrollRestore);
        });
    }

    restorePaginationScroll();

    var adminSearchInputs = document.querySelectorAll('.admin-search-input');
    adminSearchInputs.forEach(function (input) {
        input.addEventListener('keypress', function (e) {
            if (e.key === 'Enter') {
                e.preventDefault();
                if (input.form) {
                    input.form.submit();
                }
            }
        });
    });

    var paginationLinks = document.querySelectorAll('.admin-page-link');
    paginationLinks.forEach(function (link) {
        link.addEventListener('click', function () {
            try {
                sessionStorage.setItem(scrollStorageKey, JSON.stringify({
                    path: window.location.pathname,
                    scrollY: window.scrollY
                }));
            } catch (e) {
                finishScrollRestore();
            }
        });
    });
});
