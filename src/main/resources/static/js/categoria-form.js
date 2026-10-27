const nombreCategoria = document.getElementById('nombreCategoria');
const codigoCategoria = document.getElementById('codigoCategoria');
const codigos = new Set((codigoCategoria.dataset.codigos || '').split(',').filter(Boolean));

function actualizarCategoria() {
    const inicio = nombreCategoria.selectionStart;
    const fin = nombreCategoria.selectionEnd;
    nombreCategoria.value = nombreCategoria.value.toLocaleUpperCase('es-PE');
    nombreCategoria.setSelectionRange(inicio, fin);
    if (codigoCategoria.dataset.nuevo !== 'true') return;
    const letras = nombreCategoria.value.normalize('NFD').replace(/[\u0300-\u036f]/g, '')
        .replace(/[^A-Z0-9]/g, '');
    if (!letras) {
        codigoCategoria.value = '';
        return;
    }
    const base = 'CAT-' + letras.slice(0, 3);
    let codigo = base;
    let numero = 2;
    while (codigos.has(codigo)) codigo = base + '-' + numero++;
    codigoCategoria.value = codigo;
}

nombreCategoria.addEventListener('input', actualizarCategoria);
actualizarCategoria();
