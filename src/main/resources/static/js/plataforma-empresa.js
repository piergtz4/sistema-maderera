(() => {
    const barra = document.querySelector('.pestanas-empresa');
    if (!barra) return;

    const pestanas = Array.from(barra.querySelectorAll('[data-pestana]'));
    const paneles = pestanas.map(pestana => document.getElementById(pestana.getAttribute('aria-controls')));
    if (pestanas.length === 0 || paneles.some(panel => !panel)) return;

    function seleccionar(nombre, actualizarDireccion = false) {
        let indice = pestanas.findIndex(pestana => pestana.dataset.pestana === nombre);
        if (indice < 0) indice = 0;

        pestanas.forEach((pestana, posicion) => {
            const activa = posicion === indice;
            pestana.setAttribute('aria-selected', String(activa));
            pestana.tabIndex = activa ? 0 : -1;
            paneles[posicion].hidden = !activa;
        });

        if (actualizarDireccion) {
            history.replaceState(null, '', '#' + pestanas[indice].dataset.pestana);
        }
    }

    pestanas.forEach((pestana, indice) => {
        pestana.addEventListener('click', () => seleccionar(pestana.dataset.pestana, true));
        pestana.addEventListener('keydown', evento => {
            let siguiente;
            switch (evento.key) {
                case 'ArrowRight': siguiente = (indice + 1) % pestanas.length; break;
                case 'ArrowLeft': siguiente = (indice - 1 + pestanas.length) % pestanas.length; break;
                case 'Home': siguiente = 0; break;
                case 'End': siguiente = pestanas.length - 1; break;
                default: return;
            }
            evento.preventDefault();
            seleccionar(pestanas[siguiente].dataset.pestana, true);
            pestanas[siguiente].focus();
        });
    });

    window.addEventListener('hashchange', () => seleccionar(location.hash.slice(1)));
    seleccionar(location.hash.slice(1));
    barra.hidden = false;
})();
