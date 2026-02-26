function initSortables(content) {
    let sortables = content.querySelectorAll('.sortable');
    for (let i = 0; i < sortables.length; i++) {
        let el = sortables[i];
        // Skip if already initialized
        if (el._sortableInstance) {
            continue;
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
                let instance = evt.from._sortableInstance;
                if (instance) {
                    instance.option('disabled', true);
                }
                htmx.trigger(evt.item, 'end');
            }
        });
        el._sortableInstance = sortableInstance;

        el.addEventListener('htmx:afterSwap', function () {
            let instance = this._sortableInstance;
            if (instance) {
                instance.option('disabled', false);
            }
        });
    }
}

htmx.onLoad(function (content) {
    initSortables(content);
});
