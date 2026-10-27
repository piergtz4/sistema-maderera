document.addEventListener("DOMContentLoaded", () => {
    const form = document.getElementById("loginForm");
    const message = document.getElementById("loginMessage");
    const password = document.getElementById("password");
    const toggle = document.getElementById("passwordToggle");
    const button = form.querySelector('button[type="submit"]');

    function mostrarMensaje(texto, esError = true) {
        message.textContent = texto;
        message.classList.toggle("success", !esError);
        message.hidden = false;
    }

    const parametros = new URLSearchParams(window.location.search);
    if (parametros.has("logout")) mostrarMensaje("Sesión cerrada correctamente.", false);
    if (parametros.has("clave")) mostrarMensaje("Contraseña actualizada. Inicia sesión de nuevo.", false);
    if (parametros.has("cuenta")) mostrarMensaje("Cuenta actualizada. Inicia sesión de nuevo.", false);
    if (parametros.has("sesion")) mostrarMensaje("Tu sesión terminó porque cambió tu cuenta. Inicia sesión de nuevo.");

    toggle.addEventListener("click", () => {
        const visible = password.type === "password";
        password.type = visible ? "text" : "password";
        toggle.setAttribute("aria-pressed", String(visible));
        toggle.setAttribute("aria-label", visible ? "Ocultar contraseña" : "Mostrar contraseña");
    });

    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        if (button.disabled) return;
        const usuario = document.getElementById("usuario").value.trim();
        if (!usuario || !password.value) {
            mostrarMensaje("Ingresa tu usuario y contraseña.");
            return;
        }

        // FormData incluye el token CSRF generado por Thymeleaf.
        const datos = new URLSearchParams(new FormData(form));
        datos.set("usuario", usuario);
        message.hidden = true;
        button.disabled = true;
        form.setAttribute("aria-busy", "true");
        try {
            const response = await fetch(form.action, {
                method: "POST",
                headers: { Accept: "application/json" },
                body: datos
            });
            if (response.status === 403) {
                mostrarMensaje("La sesión del formulario venció. Recarga la página e intenta nuevamente.");
                return;
            }
            const resultado = await response.json();
            if (response.ok && resultado.success) {
                window.location.assign("/dashboard");
                return;
            }
            mostrarMensaje(resultado.message || "No se pudo iniciar sesión.");
        } catch {
            mostrarMensaje("No se pudo conectar con el servidor. Intenta nuevamente.");
        } finally {
            button.disabled = false;
            form.removeAttribute("aria-busy");
        }
    });
});
