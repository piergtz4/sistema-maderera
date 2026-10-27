document.addEventListener('DOMContentLoaded', () => {
    const tipo = document.getElementById('tipo');
    const serie = document.getElementById('serieDocumento');
    const numero = document.getElementById('numeroDocumentoVenta');
    const actualizarNumero = () => {
        const opcion = tipo.selectedOptions[0];
        serie.value = opcion?.dataset.serie || '';
        numero.value = opcion?.dataset.numero || '';
    };
    tipo.addEventListener('change', actualizarNumero);
    actualizarNumero();

    const modal = document.getElementById('modalCliente');
    const form = document.getElementById('formClienteRapido');
    const error = document.getElementById('errorCliente');
    const guardar = document.getElementById('guardarClienteRapido');
    const cancelar = document.getElementById('cancelarCliente');
    const cerrar = document.getElementById('cerrarCliente');
    const selector = document.getElementById('clienteId');
    const actualizarCliente = () => {
        const datos = selector.selectedOptions[0]?.dataset;
        document.getElementById('clienteDocumento').value = datos?.documento ? `${datos.tipo}: ${datos.documento}` : '';
        document.getElementById('clienteNombre').value = datos?.nombre || '';
        document.getElementById('clienteDireccion').value = datos?.direccion || '';
        document.getElementById('clienteAgregado').textContent = '';
    };
    selector.addEventListener('change', actualizarCliente);
    actualizarCliente();
    const identificacion = form.elements.tipoIdentificacion;
    const documento = form.elements.numeroDocumento;
    let enviando = false;
    const actualizarIdentificacion = () => {
        const sinDocumento = identificacion.value === 'SIN_DOCUMENTO';
        documento.disabled = sinDocumento;
        documento.required = !sinDocumento;
        if (sinDocumento) documento.value = '';
        documento.maxLength = identificacion.value === 'DNI' ? 8 : identificacion.value === 'RUC' ? 11 : 20;
        documento.inputMode = ['DNI', 'RUC'].includes(identificacion.value) ? 'numeric' : 'text';
    };
    identificacion.addEventListener('change', actualizarIdentificacion);
    document.getElementById('abrirCliente').addEventListener('click', () => {
        form.reset();
        error.hidden = true;
        identificacion.value = tipo.value === 'FACTURA' ? 'RUC' : 'DNI';
        actualizarIdentificacion();
        modal.showModal();
        documento.focus();
    });
    cancelar.addEventListener('click', () => modal.close());
    cerrar.addEventListener('click', () => modal.close());
    modal.addEventListener('cancel', event => { if (enviando) event.preventDefault(); });
    form.addEventListener('submit', async event => {
        event.preventDefault();
        if (enviando || !form.reportValidity()) return;
        enviando = true;
        guardar.disabled = cancelar.disabled = cerrar.disabled = true;
        error.hidden = true;
        try {
            const respuesta = await fetch(form.action, {
                method: 'POST', body: new FormData(form), headers: { Accept: 'application/json' }
            });
            if (respuesta.redirected || !respuesta.headers.get('content-type')?.includes('application/json')) {
                throw new Error('No se pudo guardar. Comprueba que tu sesión siga activa antes de intentarlo nuevamente.');
            }
            const datos = await respuesta.json();
            if (!respuesta.ok) throw new Error(datos.errores?.join(' ') || 'No se pudo registrar el cliente.');
            const etiqueta = datos.nombre + (datos.numeroDocumento ? ' · ' + datos.numeroDocumento : '');
            const opcion = new Option(etiqueta, datos.id, false, true);
            Object.assign(opcion.dataset, { nombre: datos.nombre, documento: datos.numeroDocumento || '',
                tipo: datos.tipoIdentificacion, direccion: datos.direccion || '' });
            selector.add(opcion);
            selector.dispatchEvent(new Event('change', { bubbles: true }));
            document.getElementById('clienteAgregado').textContent = 'Cliente registrado y seleccionado: ' + datos.nombre;
            modal.close();
            selector.focus();
        } catch (e) {
            error.textContent = e.message || 'No se pudo registrar el cliente. Revisa la conexión.';
            error.hidden = false;
        } finally {
            enviando = false;
            guardar.disabled = cancelar.disabled = cerrar.disabled = false;
        }
    });
});
