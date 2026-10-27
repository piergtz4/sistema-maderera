document.addEventListener('DOMContentLoaded', () => {
    const buscar = document.getElementById('buscarCompra');
    const estado = document.getElementById('estadoCompra');
    const desde = document.getElementById('desdeCompra');
    const hasta = document.getElementById('hastaCompra');
    const filas = [...document.querySelectorAll('[data-compra]')];
    const filtrar = () => {
        const error = desde.value && hasta.value && desde.value > hasta.value;
        document.getElementById('errorFechas').hidden = !error;
        let visibles = 0;
        filas.forEach(fila => {
            const coincide = !error && fila.textContent.toLocaleLowerCase('es').includes(buscar.value.trim().toLocaleLowerCase('es'))
                && (!estado.value || fila.dataset.estado === estado.value)
                && (!desde.value || fila.dataset.fecha >= desde.value)
                && (!hasta.value || fila.dataset.fecha <= hasta.value);
            fila.hidden = !coincide;
            if (coincide) visibles++;
        });
        document.getElementById('sinCompras').hidden = visibles > 0 || filas.length === 0;
    };
    [buscar, estado, desde, hasta].forEach(campo => campo.addEventListener('input', filtrar));
    document.getElementById('limpiarFiltros').addEventListener('click', () => {
        [buscar, estado, desde, hasta].forEach(campo => { campo.value = ''; });
        filtrar();
        buscar.focus();
    });
});
