const rol = document.getElementById("rol");
const permisos = document.getElementById("permisosSedes");
const accesoGeneral = document.getElementById("accesoGeneral");

function actualizarSede() {
    const general = rol.value === "ADMINISTRADOR_GENERAL";
    permisos.hidden = general;
    accesoGeneral.hidden = !general;
    permisos.querySelectorAll('tbody tr').forEach(fila => {
        const consulta = fila.querySelector('[name="sedeIds"]');
        const gestion = fila.querySelector('[name="sedesGestionIds"]');
        consulta.disabled = general;
        gestion.disabled = general || !consulta.checked;
    });
}

permisos.querySelectorAll('tbody tr').forEach(fila => {
    const consulta = fila.querySelector('[name="sedeIds"]');
    const gestion = fila.querySelector('[name="sedesGestionIds"]');
    consulta.addEventListener('change', () => {
        if (!consulta.checked) gestion.checked = false;
        actualizarSede();
    });
});

rol.addEventListener("change", actualizarSede);
actualizarSede();

const nuevaClave = document.querySelector('input[name="password"]');
const claveAdministrador = document.getElementById("claveAdministrador");
if (nuevaClave && claveAdministrador) {
    nuevaClave.addEventListener("input", () => {
        claveAdministrador.required = nuevaClave.value.length > 0;
    });
}
