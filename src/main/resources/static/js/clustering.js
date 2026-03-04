function getCsrfToken() {
    return window._csrfToken || null;
}

// Sniff the CSRF token from HTMX's configRequest event (captures the server-injected masked token)
document.body.addEventListener('htmx:configRequest', function(event) {
    var headers = event.detail.headers;
    if (headers['X-XSRF-TOKEN']) {
        window._csrfToken = headers['X-XSRF-TOKEN'];
        window._csrfHeader = 'X-XSRF-TOKEN';
    } else if (headers['X-CSRF-Token']) {
        window._csrfToken = headers['X-CSRF-Token'];
        window._csrfHeader = 'X-CSRF-Token';
    }
});

function initSortables(content) {
    let sortables = content.querySelectorAll ? content.querySelectorAll('.sortable') : [];
    for (let i = 0; i < sortables.length; i++) {
        let el = sortables[i];
        // Destroy existing instance before re-initialising (handles post-swap re-init)
        if (el._sortableInstance) {
            el._sortableInstance.destroy();
            el._sortableInstance = null;
        }
        let sortableInstance = new Sortable(el, {
            animation: 150,
            group: 'shared',
            ghostClass: 'sortable-ghost',
            filter: '.htmx-indicator',
            onMove: function (evt) {
                return evt.related.className.indexOf('htmx-indicator') === -1;
            },
            onEnd: function (evt) {
                const container = evt.to;
                const url = container.getAttribute('data-merge-url');
                if (!url) return;

                const responseIds = Array.from(container.querySelectorAll('[name="responseId"]'))
                    .map(function(input) { return input.value; });

                const csrfToken = getCsrfToken();
                const csrfHeader = window._csrfHeader || 'X-XSRF-TOKEN';
                const headers = { 'Content-Type': 'application/json' };
                if (csrfToken) {
                    headers[csrfHeader] = csrfToken;
                }

                fetch(url, {
                    method: 'POST',
                    credentials: 'include',
                    headers: headers,
                    body: JSON.stringify({ responseIds: responseIds })
                });
            }
        });
        el._sortableInstance = sortableInstance;
    }
}

htmx.onLoad(function (content) {
    initSortables(content);
});

document.addEventListener('htmx:afterSwap', function (e) {
    initSortables(e.detail.elt);
});