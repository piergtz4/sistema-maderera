(() => {
    const menuPrincipal = document.getElementById('menuPrincipal');
    const volverAlInicio = document.querySelector('[data-menu-top]');

    if (!menuPrincipal || !volverAlInicio) return;
    const escritorio = window.matchMedia('(min-width: 701px)');
    const actualizar = () => {
        volverAlInicio.hidden = !escritorio.matches || menuPrincipal.scrollHeight <= menuPrincipal.clientHeight + 1;
        volverAlInicio.disabled = menuPrincipal.scrollTop < 8;
    };

    volverAlInicio.addEventListener('click', () => {
        const reducirMovimiento = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
        menuPrincipal.scrollTo({ top: 0, behavior: reducirMovimiento ? 'instant' : 'smooth' });
        menuPrincipal.querySelector('.menu-home').focus({ preventScroll: true });
    });
    menuPrincipal.addEventListener('scroll', actualizar, { passive: true });
    escritorio.addEventListener('change', actualizar);
    new ResizeObserver(actualizar).observe(menuPrincipal);
    actualizar();
})();
