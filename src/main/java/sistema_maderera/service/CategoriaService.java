package sistema_maderera.service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sistema_maderera.dto.CategoriaForm;
import sistema_maderera.entity.Categoria;
import sistema_maderera.exception.RecursoNoEncontradoException;
import sistema_maderera.exception.ReglaNegocioException;
import sistema_maderera.repository.CategoriaRepository;

@Service
@Transactional(readOnly = true)
public class CategoriaService {
    private final CategoriaRepository categorias;
    private final ValidacionService validacion;

    public CategoriaService(CategoriaRepository categorias, ValidacionService validacion) {
        this.categorias = categorias;
        this.validacion = validacion;
    }

    public List<Categoria> listarTodas() { return categorias.findAllByOrderByNombreAsc(); }
    public List<Categoria> listarActivas() { return categorias.findByEstadoTrueOrderByNombreAsc(); }

    public Categoria buscar(Long id) {
        return categorias.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Categoría no encontrada."));
    }

    public CategoriaForm formulario(Long id) {
        Categoria categoria = buscar(id);
        CategoriaForm form = new CategoriaForm();
        form.setId(categoria.getId());
        form.setCodigo(categoria.getCodigo());
        form.setNombre(categoria.getNombre());
        form.setDescripcion(categoria.getDescripcion());
        form.setEstado(categoria.getEstado());
        return form;
    }

    @Transactional
    public Categoria guardar(CategoriaForm form) {
        validacion.validar(form);
        String nombre = form.getNombre().trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        if (categorias.findByNombreIgnoreCase(nombre)
                .filter(c -> !Objects.equals(c.getId(), form.getId())).isPresent()) {
            throw new ReglaNegocioException("Ya existe una categoría con ese nombre.");
        }
        Categoria categoria = form.getId() == null ? new Categoria() : buscar(form.getId());
        if (categoria.getId() == null) categoria.setCodigo(generarCodigo(nombre));
        categoria.setNombre(nombre);
        categoria.setDescripcion(form.getDescripcion() == null ? "" : form.getDescripcion().trim());
        categoria.setEstado(form.getEstado());
        return categorias.save(categoria);
    }

    private String generarCodigo(String nombre) {
        String letras = Normalizer.normalize(nombre, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replaceAll("[^A-Z0-9]", "");
        if (letras.isEmpty()) throw new ReglaNegocioException("El nombre debe contener letras o números.");
        String base = "CAT-" + letras.substring(0, Math.min(3, letras.length()));
        String codigo = base;
        int numero = 2;
        while (categorias.findByCodigoIgnoreCase(codigo).isPresent()) codigo = base + "-" + numero++;
        return codigo;
    }

    @Transactional
    public void cambiarEstado(Long id) {
        Categoria categoria = buscar(id);
        categoria.setEstado(!categoria.getEstado());
        categorias.save(categoria);
    }
}
