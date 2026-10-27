document.addEventListener('DOMContentLoaded', () => {
    const tabla = document.getElementById('filasPagos');
    const plantilla = document.getElementById('plantillaPago');
    const agregar = document.getElementById('agregarPago');
    const tipo = document.getElementById('tipo');
    const bloque = document.getElementById('pagosVenta');
    const error = document.getElementById('errorPagos');
    const exacto = document.getElementById('efectivoExacto');
    const revisar = document.getElementById('revisarVenta');
    const estado = document.getElementById('estadoPagos');
    let total = null;
    const dinero = n => (n / 100n).toString() + '.' + String(n % 100n).padStart(2, '0');
    const centimos = valor => {
        if (!/^\d{1,16}(\.\d{1,2})?$/.test(valor)) return null;
        const [entero, fraccion = ''] = valor.split('.');
        return BigInt(entero + fraccion.padEnd(2, '0'));
    };
    const filas = () => [...tabla.querySelectorAll('.pago-linea')];
    const campo = (fila, nombre) => fila.querySelector('[data-pago="' + nombre + '"]');
    const actualizar = () => {
        const cotizacion = tipo.value === 'COTIZACION';
        bloque.hidden = cotizacion;
        bloque.disabled = cotizacion;
        document.getElementById('avisoCotizacion').hidden = !cotizacion;
        let recibido = 0n;
        let digital = 0n;
        let valido = total !== null;
        const referencias = new Set();
        let mensaje = '';
        filas().forEach((fila, indice) => {
            fila.querySelectorAll('[data-pago]').forEach(input => {
                input.name = 'pagos[' + indice + '].' + input.dataset.pago;
                input.id = 'pago-' + indice + '-' + input.dataset.pago;
            });
            const medio = campo(fila, 'medio').value;
            const referencia = campo(fila, 'referencia');
            const banco = campo(fila, 'banco');
            const destino = campo(fila, 'cuentaDestinoId');
            const selector = fila.querySelector('[data-cuenta]');
            if (!fila.dataset.medioActual) selector.value = destino.value;
            else if (fila.dataset.medioActual !== medio) selector.value = '';
            fila.dataset.medioActual = medio;
            for (const opcion of selector.options) {
                if (!opcion.value) continue;
                opcion.hidden = medio === 'YAPE' && opcion.dataset.yape !== 'true';
                opcion.textContent = medio === 'YAPE' ? opcion.dataset.banco : opcion.dataset.etiqueta + (opcion.disabled ? ' (inactiva)' : '');
            }
            if (medio === 'YAPE' && !selector.value) {
                selector.value = [...selector.options].find(o => o.value && !o.disabled && o.dataset.yape === 'true')?.value || '';
            }
            if (medio === 'EFECTIVO') selector.value = '';
            const cuenta = selector.selectedOptions[0];
            destino.value = selector.value;
            destino.disabled = medio === 'EFECTIVO';
            selector.disabled = medio !== 'TRANSFERENCIA';
            selector.required = medio === 'TRANSFERENCIA';
            banco.value = cuenta?.dataset.banco || '';
            referencia.disabled = medio === 'EFECTIVO';
            referencia.required = medio !== 'EFECTIVO';
            banco.disabled = medio === 'EFECTIVO';
            referencia.setCustomValidity('');
            const monto = centimos(campo(fila, 'recibido').value);
            if (monto === null || monto <= 0n) valido = false;
            else {
                recibido += monto;
                if (medio !== 'EFECTIVO') digital += monto;
            }
            if (medio !== 'EFECTIVO' && !referencia.value.trim()) valido = false;
            if (medio !== 'EFECTIVO' && (!destino.value || cuenta?.disabled || medio === 'YAPE' && cuenta?.dataset.yape !== 'true')) {
                valido = false;
                mensaje = medio === 'YAPE' ? 'Configura una cuenta BCP con celular Yape activo en Configuración de empresa.' : 'Selecciona una cuenta de destino activa para la transferencia.';
            }
            if (medio !== 'EFECTIVO' && referencia.value.trim()) {
                const clave = medio + '|' + (medio === 'TRANSFERENCIA' ? banco.value.trim().toUpperCase() : '') + '|' + referencia.value.trim().toUpperCase();
                if (referencias.has(clave)) {
                    referencia.setCustomValidity('Código de operación repetido.');
                    valido = false;
                }
                referencias.add(clave);
            }
        });
        exacto.disabled = filas().length > 0;
        if (exacto.disabled) exacto.checked = false;
        const automatico = exacto.checked && !cotizacion;
        document.getElementById('avisoEfectivoExacto').hidden = !automatico;
        if (automatico && total !== null) recibido = total;
        if (total !== null && digital > total) mensaje = 'Los pagos digitales superan el total. Revisa los abonos; ese exceso no es vuelto en efectivo.';
        if (recibido > 999999999999999999n) mensaje = 'El monto recibido supera el máximo permitido.';
        error.textContent = mensaje;
        error.hidden = !mensaje || cotizacion;
        document.getElementById('recibidoVenta').textContent = dinero(recibido);
        document.getElementById('saldoVenta').textContent = total !== null ? dinero(total > recibido ? total - recibido : 0n) : '—';
        document.getElementById('vueltoVenta').textContent = total !== null ? dinero(!mensaje && recibido > total ? recibido - total : 0n) : '—';
        const completo = valido && total > 0n && recibido >= total && !mensaje && (automatico || filas().length > 0);
        revisar.disabled = !cotizacion && !completo;
        estado.textContent = cotizacion ? '' : completo ? 'Pago preparado. Puedes guardar y revisar.'
            : 'Completa los montos, códigos y banco que correspondan para revisar. Puedes guardar los datos pendientes.';
        agregar.disabled = filas().length >= 30;
    };
    document.addEventListener('venta-total', event => {
        total = event.detail === null ? null : BigInt(event.detail);
        actualizar();
    });
    agregar.addEventListener('click', () => {
        if (filas().length >= 30) return;
        exacto.checked = false;
        tabla.append(plantilla.content.cloneNode(true));
        actualizar();
        campo(tabla.lastElementChild, 'medio').focus();
    });
    tabla.addEventListener('input', actualizar);
    tabla.addEventListener('change', actualizar);
    tabla.addEventListener('click', event => {
        if (event.target.closest('.quitar-pago')) {
            event.target.closest('.pago-linea').remove();
            actualizar();
        }
    });
    tipo.addEventListener('change', actualizar);
    exacto.addEventListener('change', actualizar);
    bloque.closest('form').addEventListener('submit', event => {
        if (event.submitter?.value !== 'revisar' || tipo.value === 'COTIZACION') return;
        actualizar();
        if (revisar.disabled) { event.preventDefault(); return; }
        if (exacto.checked) {
            tabla.append(plantilla.content.cloneNode(true));
            campo(tabla.lastElementChild, 'recibido').value = dinero(total);
            actualizar();
        }
    });
    actualizar();
});
