package sistema_maderera.service;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sistema_maderera.dto.ProductoForm;
import sistema_maderera.entity.Producto;
import sistema_maderera.exception.RecursoNoEncontradoException;
import sistema_maderera.exception.ReglaNegocioException;
import sistema_maderera.repository.ProductoRepository;

@Service
@Transactional(readOnly = true)
public class ProductoService {
    private final ProductoRepository productos;
    private final CategoriaService categorias;
    private final ValidacionService validacion;

    public ProductoService(ProductoRepository productos, CategoriaService categorias, ValidacionService validacion) {
        this.productos = productos;
        this.categorias = categorias;
        this.validacion = validacion;
    }

    public List<Producto> listarTodos() { return productos.findAllByOrderByNombreAsc(); }

    public Producto buscar(Long id) {
        return productos.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Producto no encontrado."));
    }

    public ProductoForm formulario(Long id) {
        Producto producto = buscar(id);
        ProductoForm form = new ProductoForm();
        form.setId(producto.getId());
        form.setCodigo(producto.getCodigo());
        form.setNombre(producto.getNombre());
        form.setCategoriaId(producto.getCategoria().getId());
        form.setUnidadMedida(producto.getUnidadMedida());
        form.setPrecioCompra(producto.getPrecioCompra());
        form.setPrecioVenta(producto.getPrecioVenta());
        form.setEstado(producto.getEstado());
        return form;
    }

    @Transactional
    public Producto guardar(ProductoForm form) {
        validacion.validar(form);
        boolean nuevo = form.getId() == null;
        Producto producto = nuevo ? new Producto() : buscar(form.getId());
        var categoria = categorias.buscar(form.getCategoriaId());
        if (!categoria.getEstado() && (nuevo || !producto.getCategoria().getId().equals(categoria.getId()))) {
            throw new ReglaNegocioException("Seleccione una categoría activa.");
        }
        if (!nuevo && !producto.getUnidadMedida().equals(form.getUnidadMedida())) {
            throw new ReglaNegocioException("La unidad de un producto existente no puede cambiarse.");
        }
        producto.setNombre(form.getNombre().trim().replaceAll("\\s+", " "));
        producto.setCategoria(categoria);
        producto.setUnidadMedida(form.getUnidadMedida());
        producto.setPrecioCompra(form.getPrecioCompra());
        producto.setPrecioVenta(form.getPrecioVenta());
        producto.setEstado(form.getEstado());
        if (nuevo) {
            // El ID asignado por la base evita códigos repetidos entre categorías y usuarios.
            producto.setCodigo("TMP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
            productos.saveAndFlush(producto);
            producto.setCodigo(String.format("PRO-%06d", producto.getId()));
        }
        return productos.save(producto);
    }

    @Transactional
    public void cambiarEstado(Long id) {
        Producto producto = buscar(id);
        producto.setEstado(!producto.getEstado());
        productos.save(producto);
    }
}
