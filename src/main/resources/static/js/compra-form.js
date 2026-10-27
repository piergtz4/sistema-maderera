document.addEventListener('DOMContentLoaded', () => {
    const filas = document.getElementById('lineasCompra');
    const agregar = document.getElementById('agregarCompra');
    const plantilla = filas.querySelector('[data-linea]').cloneNode(true);
    const escalado = (valor, decimales) => {
        if (!/^\d+(\.\d*)?$/.test(valor)) return 0n;
        const [entero, decimal = ''] = valor.split('.');
        if (decimal.length > decimales) return 0n;
        return BigInt(entero + decimal.padEnd(decimales, '0'));
    };
    const soles = centimos => `${centimos / 100n}.${(centimos % 100n).toString().padStart(2, '0')}`;
    const actualizar = () => {
        let total = 0n;
        const registros = [...filas.querySelectorAll('[data-linea]')];
        registros.forEach((fila, indice) => {
            fila.querySelectorAll('[data-campo]').forEach(campo => {
                campo.name = `lineas[${indice}].${campo.dataset.campo}`;
                campo.id = `compra-${indice}-${campo.dataset.campo}`;
            });
            const producto = fila.querySelector('select');
            const cantidad = fila.querySelector('[data-campo="cantidad"]');
            const unidades = producto.selectedOptions[0]?.dataset.unidad === 'UND';
            cantidad.step = unidades ? '1' : '0.0001';
            cantidad.min = unidades ? '1' : '0.0001';
            const precio = fila.querySelector('[data-campo="precio"]');
            const importe = (escalado(cantidad.value, 4) * escalado(precio.value, 2) + 5000n) / 10000n;
            fila.querySelector('[data-importe]').textContent = soles(importe);
            total += importe;
        });
        agregar.disabled = registros.length >= 100;
        document.getElementById('totalCompra').textContent = soles(total);
    };
    agregar.addEventListener('click', () => {
        const fila = plantilla.cloneNode(true);
        fila.querySelectorAll('input, select').forEach(campo => { campo.value = ''; });
        filas.appendChild(fila);
        actualizar();
        fila.querySelector('select').focus();
    });
    filas.addEventListener('click', evento => {
        const boton = evento.target.closest('[data-quitar]');
        if (!boton) return;
        boton.closest('[data-linea]').remove();
        actualizar();
    });
    filas.addEventListener('change', evento => {
        if (evento.target.matches('select')) {
            const fila = evento.target.closest('[data-linea]');
            fila.querySelector('[data-campo="precio"]').value = evento.target.selectedOptions[0]?.dataset.precio || '';
        }
        actualizar();
    });
    filas.addEventListener('input', actualizar);
    document.getElementById('formCompra').addEventListener('submit', evento => {
        if (!filas.querySelector('[data-linea]')) {
            evento.preventDefault();
            agregar.click();
            document.getElementById('formCompra').reportValidity();
        }
    });
    actualizar();
});
