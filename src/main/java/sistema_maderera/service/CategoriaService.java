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
import sistema_maderera.repository.EmpresaRepository;

@Service
@Transactional(readOnly = true)
public class CategoriaService {
    private final CategoriaRepository categorias;
    private final EmpresaRepository empresas;
    private final ValidacionService validacion;

    public CategoriaService(CategoriaRepository categorias, EmpresaRepository empresas, ValidacionService validacion) {
        this.categorias = categorias;
        this.empresas = empresas;
        this.validacion = validacion;
    }

    public List<Categoria> listarTodas(Long empresaId) { return categorias.findByEmpresaIdOrderByNombreAsc(empresaId); }
    public List<Categoria> listarActivas(Long empresaId) { return categorias.findByEmpresaIdAndEstadoTrueOrderByNombreAsc(empresaId); }

    public Categoria buscar(Long empresaId, Long id) {
        return categorias.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Categoría no encontrada."));
    }

    public CategoriaForm formulario(Long empresaId, Long id) {
        Categoria categoria = buscar(empresaId, id);
        CategoriaForm form = new CategoriaForm();
        form.setId(categoria.getId());
        form.setCodigo(categoria.getCodigo());
        form.setNombre(categoria.getNombre());
        form.setDescripcion(categoria.getDescripcion());
        form.setEstado(categoria.getEstado());
        return form;
    }

    @Transactional
    public Categoria guardar(Long empresaId, CategoriaForm form) {
        validacion.validar(form);
        String nombre = form.getNombre().trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        if (categorias.findByEmpresaIdAndNombreIgnoreCase(empresaId, nombre)
                .filter(c -> !Objects.equals(c.getId(), form.getId())).isPresent()) {
            throw new ReglaNegocioException("Ya existe una categoría con ese nombre.");
        }
        Categoria categoria = form.getId() == null ? new Categoria() : buscar(empresaId, form.getId());
        if (categoria.getId() == null) {
            categoria.setEmpresa(empresas.findById(empresaId)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada.")));
            categoria.setCodigo(generarCodigo(empresaId, nombre));
        }
        categoria.setNombre(nombre);
        categoria.setDescripcion(form.getDescripcion() == null ? "" : form.getDescripcion().trim());
        categoria.setEstado(form.getEstado());
        return categorias.save(categoria);
    }

    private String generarCodigo(Long empresaId, String nombre) {
        String letras = Normalizer.normalize(nombre, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replaceAll("[^A-Z0-9]", "");
        if (letras.isEmpty()) throw new ReglaNegocioException("El nombre debe contener letras o números.");
        String base = "CAT-" + letras.substring(0, Math.min(3, letras.length()));
        String codigo = base;
        int numero = 2;
        while (categorias.findByEmpresaIdAndCodigoIgnoreCase(empresaId, codigo).isPresent()) codigo = base + "-" + numero++;
        return codigo;
    }

    @Transactional
    public void cambiarEstado(Long empresaId, Long id) {
        Categoria categoria = buscar(empresaId, id);
        categoria.setEstado(!categoria.getEstado());
        categorias.save(categoria);
    }
}
