document.addEventListener('DOMContentLoaded', () => {
    const cuerpo = document.getElementById('lineasVenta');
    const plantilla = document.getElementById('plantillaLineaVenta');
    const agregar = document.getElementById('agregarLineaVenta');
    const total = document.getElementById('totalVenta');
    // Enteros escalados para que la vista redondee igual que BigDecimal en el servidor.
    const decimal = (valor, escala) => {
        if (!/^\d+(\.\d+)?$/.test(valor)) return null;
        const [entero, fraccion = ''] = valor.split('.');
        if (fraccion.length > escala && /[1-9]/.test(fraccion.slice(escala))) return null;
        if (entero.length > 14) return null;
        return BigInt(entero + fraccion.slice(0, escala).padEnd(escala, '0'));
    };
    const dinero = centimos => {
        const signo = centimos < 0n ? '-' : '';
        const valor = centimos < 0n ? -centimos : centimos;
        return signo + valor / 100n + '.' + String(valor % 100n).padStart(2, '0');
    };
    const campo = (fila, nombre) => fila.querySelector('[data-field="' + nombre + '"]');
    const filas = () => [...cuerpo.querySelectorAll('.venta-linea')];
    const actualizar = () => {
        let suma = 0n;
        let valido = true;
        const productos = filas().map(f => campo(f, 'productoId').value).filter(Boolean);
        filas().forEach((fila, indice) => {
            fila.querySelectorAll('[data-field]').forEach(input => {
                input.name = 'lineas[' + indice + '].' + input.dataset.field;
                input.id = 'venta-' + indice + '-' + input.dataset.field;
            });
            const producto = campo(fila, 'productoId');
            producto.setCustomValidity(producto.value && productos.filter(id => id === producto.value).length > 1
                ? 'Este producto ya está agregado. Modifica su cantidad en la misma fila.' : '');
            const opcion = producto.selectedOptions[0];
            const cantidad = campo(fila, 'cantidad');
            const precio = campo(fila, 'precio');
            const cobrar = campo(fila, 'importeCobrar');
            const q = decimal(cantidad.value, 4);
            const p = decimal(precio.value, 2);
            const esUnidad = opcion?.dataset.unidad === 'UND';
            cantidad.step = esUnidad ? '1' : '0.0001';
            cantidad.min = esUnidad ? '1' : '0.0001';
            const calculado = q !== null && q > 0n && p !== null ? (q * p + 5000n) / 10000n : null;
            fila.querySelector('.calculado-venta').textContent = calculado === null ? '—' : dinero(calculado);
            cobrar.placeholder = calculado === null ? 'Automático' : dinero(calculado);
            const importe = cobrar.value === '' ? calculado : decimal(cobrar.value, 2);
            const ajuste = fila.querySelector('.ajuste-venta');
            ajuste.textContent = importe !== null && calculado !== null && importe !== calculado
                ? 'Ajuste: S/ ' + (importe > calculado ? '+' : '') + dinero(importe - calculado) : '';
            if (importe === null || calculado === null) valido = false;
            else suma += importe;
            const stock = fila.querySelector('.stock-venta');
            stock.className = 'stock-venta';
            if (opcion?.dataset.stock !== undefined) {
                const disponible = decimal(opcion.dataset.stock, 4);
                const minimo = decimal(opcion.dataset.minimo, 4);
                stock.textContent = 'Stock: ' + opcion.dataset.stock + ' ' + opcion.dataset.unidad;
                stock.classList.add(disponible === 0n ? 'stock-agotado' : disponible <= minimo ? 'stock-bajo' : 'stock-disponible');
                if (q !== null && disponible !== null && q > disponible) stock.textContent += ' · Cantidad superior al stock actual';
            } else stock.textContent = '';
        });
        total.textContent = valido ? dinero(suma) : '—';
        const subtotal = (suma * 100n + 59n) / 118n;
        document.getElementById('subtotalVenta').textContent = valido ? dinero(subtotal) : '—';
        document.getElementById('igvVenta').textContent = valido ? dinero(suma - subtotal) : '—';
        document.dispatchEvent(new CustomEvent('venta-total', { detail: valido ? suma.toString() : null }));
        agregar.disabled = filas().length >= 100;
        document.getElementById('sinLineasVenta').hidden = filas().length !== 0;
    };
    agregar.addEventListener('click', () => {
        if (filas().length >= 100) return;
        cuerpo.append(plantilla.content.cloneNode(true));
        actualizar();
        campo(cuerpo.lastElementChild, 'productoId').focus();
    });
    cuerpo.addEventListener('change', event => {
        if (event.target.dataset.field === 'productoId') {
            const fila = event.target.closest('.venta-linea');
            campo(fila, 'precio').value = event.target.selectedOptions[0]?.dataset.precio || '';
            campo(fila, 'cantidad').value = '1';
            campo(fila, 'importeCobrar').value = '';
        }
        actualizar();
    });
    cuerpo.addEventListener('input', actualizar);
    cuerpo.addEventListener('click', event => {
        const fila = event.target.closest('.venta-linea');
        if (!fila) return;
        if (event.target.closest('.quitar-linea')) fila.remove();
        if (event.target.closest('.restaurar-importe')) campo(fila, 'importeCobrar').value = '';
        actualizar();
    });
    actualizar();
});
