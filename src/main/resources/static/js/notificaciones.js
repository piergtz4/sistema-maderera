document.querySelectorAll('.toast').forEach(toast => {
    let restante = Number(toast.dataset.duration) || 3600;
    let inicio;
    let temporizador;
    let cerrando = false;
    const boton = toast.querySelector('.toast-close');

    const cerrar = () => {
        if (cerrando) return;
        cerrando = true;
        clearTimeout(temporizador);
        toast.classList.add('toast-leaving');
        boton.disabled = true;
        window.setTimeout(() => {
            const contenedor = toast.parentElement;
            toast.remove();
            if (contenedor && !contenedor.children.length) contenedor.remove();
        }, 200);
    };

    const continuar = () => {
        if (cerrando || toast.contains(document.activeElement)) return;
        clearTimeout(temporizador);
        inicio = performance.now();
        temporizador = window.setTimeout(cerrar, restante);
        toast.classList.remove('toast-paused');
    };

    toast.style.setProperty('--toast-duration', `${restante}ms`);
    boton.addEventListener('click', cerrar);
    toast.addEventListener('focusin', () => {
        if (toast.classList.contains('toast-paused') || cerrando) return;
        clearTimeout(temporizador);
        restante = Math.max(0, restante - (performance.now() - inicio));
        toast.classList.add('toast-paused');
    });
    toast.addEventListener('focusout', () => window.setTimeout(continuar, 0));
    toast.addEventListener('keydown', event => {
        if (event.key === 'Escape') cerrar();
    });
    continuar();
});
