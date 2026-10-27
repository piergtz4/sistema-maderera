const rol = document.getElementById("rol");
const sede = document.getElementById("sedeId");
const opcionSede = sede.options[0];

function actualizarSede() {
    const general = rol.value === "ADMINISTRADOR_GENERAL";
    opcionSede.textContent = general ? "Todas" : "Selecciona una sede";
    sede.disabled = general;
    sede.required = !general;
    if (general) sede.value = "";
}

rol.addEventListener("change", actualizarSede);
actualizarSede();

const nuevaClave = document.querySelector('input[name="password"]');
const claveAdministrador = document.getElementById("claveAdministrador");
if (nuevaClave && claveAdministrador) {
    nuevaClave.addEventListener("input", () => {
        claveAdministrador.required = nuevaClave.value.length > 0;
    });
}
