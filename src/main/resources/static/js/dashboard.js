document.addEventListener('DOMContentLoaded', () => {
    const periodo = document.getElementById('periodoDashboard');
    periodo.addEventListener('change', () => {
        if (periodo.value !== 'personalizado') periodo.form.requestSubmit();
    });
    for (const id of ['desdeDashboard', 'hastaDashboard']) {
        document.getElementById(id).addEventListener('change', () => { periodo.value = 'personalizado'; });
    }
    document.querySelectorAll('[data-dialog-open]').forEach(boton => boton.addEventListener('click', () => {
        document.getElementById(boton.dataset.dialogOpen).showModal();
    }));
    document.querySelectorAll('.dashboard-dialog').forEach(dialogo => {
        dialogo.querySelector('[data-dialog-close]').addEventListener('click', () => dialogo.close());
        dialogo.addEventListener('click', event => {
            if (event.target !== dialogo) return;
            const limites = dialogo.getBoundingClientRect();
            if (event.clientX < limites.left || event.clientX > limites.right
                    || event.clientY < limites.top || event.clientY > limites.bottom) dialogo.close();
        });
    });
    const datos = [...document.querySelectorAll('#datosGrafico tr')].map(fila => ({
        etiqueta: fila.cells[0].textContent, monto: Number(fila.cells[1].dataset.monto)
    }));
    const contenedor = document.getElementById('graficoVentas');
    const ns = 'http://www.w3.org/2000/svg';
    const crear = (tipo, attrs, texto) => {
        const nodo = document.createElementNS(ns, tipo);
        for (const [clave, valor] of Object.entries(attrs)) nodo.setAttribute(clave, valor);
        if (texto !== undefined) nodo.textContent = texto;
        return nodo;
    };
    const dibujar = () => {
        const ancho = Math.max(260, contenedor.clientWidth), alto = ancho < 500 ? 190 : 210;
        const izq = ancho < 500 ? 64 : 82, derecha = ancho - 18, arriba = 14, base = alto - 30;
        const max = Math.max(4, Math.ceil(Math.max(0, ...datos.map(d => d.monto)) * 1.15 / 4) * 4);
        const svg = crear('svg', {viewBox: `0 0 ${ancho} ${alto}`, 'aria-hidden': 'true'});
        const moneda = new Intl.NumberFormat('es-PE', {maximumFractionDigits: 0, notation: max > 999999 ? 'compact' : 'standard'});
        for (let i = 0; i <= 4; i++) {
            const y = base - i * (base - arriba) / 4;
            svg.append(crear('line', {x1: izq, y1: y, x2: derecha, y2: y, stroke: '#dce4ee', 'stroke-dasharray': '4 4'}));
            svg.append(crear('text', {x: izq - 10, y: y + 4, 'text-anchor': 'end', class: 'chart-axis'}, 'S/ ' + moneda.format(max * i / 4)));
        }
        const puntos = datos.map((d, i) => [izq + i * (derecha - izq) / Math.max(1, datos.length - 1), base - d.monto / max * (base - arriba)]);
        if (puntos.length) {
            const linea = puntos.map((p, i) => (i ? 'L' : 'M') + p.join(',')).join(' ');
            const defs = crear('defs', {});
            const degradado = crear('linearGradient', {id: 'areaVentas', x1: '0', y1: '0', x2: '0', y2: '1'});
            degradado.append(crear('stop', {offset: '0%', 'stop-color': '#769bd7', 'stop-opacity': '.24'}));
            degradado.append(crear('stop', {offset: '100%', 'stop-color': '#769bd7', 'stop-opacity': '.03'}));
            defs.append(degradado);
            svg.append(defs);
            svg.append(crear('path', {d: `${linea} L${puntos.at(-1)[0]},${base} L${izq},${base} Z`, fill: 'url(#areaVentas)'}));
            svg.append(crear('path', {d: linea, fill: 'none', stroke: '#386cc8', 'stroke-width': 2.5, 'stroke-linejoin': 'round'}));
            const cantidadEtiquetas = Math.min(datos.length, ancho < 600 ? 4 : 8);
            const etiquetas = new Set(Array.from({length: cantidadEtiquetas}, (_, i) =>
                Math.round(i * (datos.length - 1) / Math.max(1, cantidadEtiquetas - 1))));
            puntos.forEach((p, i) => {
                const circulo = crear('circle', {cx: p[0], cy: p[1], r: datos.length > 35 ? 2 : 4, fill: '#386cc8'});
                circulo.append(crear('title', {}, `${datos[i].etiqueta}: S/ ${datos[i].monto.toFixed(2)}`));
                svg.append(circulo);
                if (etiquetas.has(i)) svg.append(crear('text', {x: p[0], y: alto - 8,
                    'text-anchor': i === 0 ? 'start' : i === datos.length - 1 ? 'end' : 'middle', class: 'chart-axis'}, datos[i].etiqueta));
            });
        }
        contenedor.replaceChildren(svg);
    };
    dibujar();
    new ResizeObserver(dibujar).observe(contenedor);
});
