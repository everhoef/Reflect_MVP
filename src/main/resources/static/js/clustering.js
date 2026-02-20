function initSortables(content) {
    var sortables = content.querySelectorAll('.sortable');
    for (var i = 0; i < sortables.length; i++) {
        var el = sortables[i];
        var sortableInstance = new Sortable(el, {
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

        el.addEventListener('htmx:afterSwap', function () {
            sortableInstance.option('disabled', false);
        });
    }
}

htmx.onLoad(function (content) {
    initSortables(content);
});
