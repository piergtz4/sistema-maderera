(() => {
    const formulario = document.getElementById('formularioPlan');
    if (!formulario) return;

    const plan = document.getElementById('plan');
    const periodicidad = document.getElementById('periodicidad');
    const sedes = document.getElementById('sedesAdicionales');
    const usuarios = document.getElementById('usuariosAdicionales');
    const resumen = document.getElementById('resumenPlan');
    const moneda = new Intl.NumberFormat('es-PE', { style: 'currency', currency: 'PEN' });
    const escribir = (id, valor) => {
        const elemento = document.getElementById(id);
        if (elemento) elemento.textContent = valor;
    };

    function actualizar() {
        const opcion = plan.selectedOptions[0];
        const modalidad = periodicidad.selectedOptions[0];
        const cantidades = [sedes.valueAsNumber, usuarios.valueAsNumber];
        if (!opcion || !opcion.value || !modalidad || !cantidades.every(n => Number.isInteger(n) && n >= 0 && n <= 2147483647)) {
            resumen.hidden = true;
            return;
        }
        const base = Math.round(Number(opcion.dataset.precio) * 100);
        const extraSedes = cantidades[0] * Math.round(Number(formulario.dataset.precioSede) * 100);
        const extraUsuarios = cantidades[1] * Math.round(Number(formulario.dataset.precioUsuario) * 100);
        const mensual = base + extraSedes + extraUsuarios;
        const meses = Number(modalidad.dataset.meses);

        escribir('totalSedes', Number(opcion.dataset.sedes) + cantidades[0]);
        escribir('totalUsuarios', Number(opcion.dataset.usuarios) + cantidades[1]);
        escribir('totalVentas', opcion.dataset.ventas);
        escribir('importeBase', moneda.format(base / 100));
        escribir('importeSedes', moneda.format(extraSedes / 100));
        escribir('importeUsuarios', moneda.format(extraUsuarios / 100));
        escribir('totalMensual', moneda.format(mensual / 100));
        escribir('etiquetaPeriodo', meses === 12 ? 'Total anual' : 'Total del mes');
        escribir('totalPeriodo', moneda.format(mensual * meses / 100));
        resumen.hidden = false;
    }

    formulario.addEventListener('input', actualizar);
    formulario.addEventListener('change', actualizar);
    actualizar();
})();
