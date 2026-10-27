document.addEventListener('DOMContentLoaded', () => {
    const buscador = document.getElementById('buscador');
    const estado = document.getElementById('filtroEstado');
    const filas = [...document.querySelectorAll('#catalogo [data-row]')];
    const sinResultados = document.getElementById('sinResultados');
    const exportar = document.getElementById('exportarInventario');
    const rutaExportar = exportar?.getAttribute('href');
    const normalizar = texto => texto.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
    const filtrar = () => {
        const texto = normalizar(buscador?.value.trim() || '');
        let visibles = 0;
        filas.forEach(fila => {
            const coincide = normalizar(fila.dataset.busqueda ?? fila.textContent).includes(texto)
                && (!estado?.value || fila.dataset.estado === estado.value);
            fila.hidden = !coincide;
            if (coincide) visibles++;
        });
        if (sinResultados) sinResultados.hidden = filas.length === 0 || visibles > 0;
        if (exportar) {
            const url = new URL(rutaExportar, window.location.origin);
            if (buscador?.value.trim()) url.searchParams.set('buscar', buscador.value.trim());
            if (estado?.value) url.searchParams.set('activo', estado.value);
            exportar.href = url.pathname + url.search;
        }
    };
    buscador?.addEventListener('input', filtrar);
    estado?.addEventListener('change', filtrar);
    document.getElementById('limpiarFiltros')?.addEventListener('click', () => {
        if (buscador) buscador.value = '';
        if (estado) estado.value = '';
        filtrar();
        buscador?.focus();
    });
    filtrar();
});
