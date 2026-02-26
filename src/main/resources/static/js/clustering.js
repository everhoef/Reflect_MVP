var activeSortables = [];

function initSortables(content) {
    let sortables = content.querySelectorAll('.sortable');
    let instances = [];
    for (let i = 0; i < sortables.length; i++) {
        let el = sortables[i];
        let sortableInstance = new Sortable(el, {
            animation: 150,
            group: 'shared',
            ghostClass: 'sortable-ghost',
            filter: '.htmx-indicator',
            onMove: function (evt) {
                return evt.related.className.indexOf('htmx-indicator') === -1;
            },
            onEnd: function (evt) {
                sortableInstance.option('disabled', true);
                htmx.trigger(evt.item, 'end');
            }
        });
        instances.push(sortableInstance);

        el.addEventListener('htmx:afterSwap', function () {
            sortableInstance.option('disabled', false);
        });
    }
    return instances;
}

htmx.onLoad(function (content) {
    activeSortables.forEach(function (s) { s.destroy(); });
    activeSortables = initSortables(content);
});
