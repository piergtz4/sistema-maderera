package sistema_maderera.service;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sistema_maderera.dto.ProveedorForm;
import sistema_maderera.entity.*;
import sistema_maderera.exception.*;
import sistema_maderera.repository.*;

@Service
@Transactional(readOnly = true)
public class ProveedorService {
    private final ProveedorRepository proveedores;
    private final EmpresaRepository empresas;
    private final ValidacionService validacion;

    public ProveedorService(ProveedorRepository proveedores, EmpresaRepository empresas, ValidacionService validacion) {
        this.proveedores = proveedores;
        this.empresas = empresas;
        this.validacion = validacion;
    }

    public List<Proveedor> listar(Long empresaId) { return proveedores.findByEmpresaIdOrderByNombreAsc(empresaId); }

    public Proveedor buscar(Long empresaId, Long id) {
        return proveedores.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Proveedor no encontrado en tu empresa."));
    }

    public ProveedorForm formulario(Long empresaId, Long id) {
        Proveedor proveedor = buscar(empresaId, id);
        ProveedorForm form = new ProveedorForm();
        form.setId(proveedor.getId());
        form.setVersion(proveedor.getVersion());
        form.setTipoIdentificacion(proveedor.getTipoIdentificacion());
        form.setNumeroDocumento(proveedor.getNumeroDocumento());
        form.setNombre(proveedor.getNombre());
        form.setDireccion(proveedor.getDireccion());
        form.setTelefono(proveedor.getTelefono());
        form.setEmail(proveedor.getEmail());
        return form;
    }

    @Transactional
    public Proveedor guardar(Long empresaId, ProveedorForm form) {
        validacion.validar(form);
        empresas.bloquearParaUsuarios(empresaId).orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada."));
        String numero = limpiar(form.getNumeroDocumento()).toUpperCase(Locale.ROOT);
        switch (form.getTipoIdentificacion()) {
            case SIN_DOCUMENTO -> {
                if (!numero.isEmpty()) throw new ReglaNegocioException("Selecciona el tipo del documento ingresado.");
            }
            case DNI -> {
                if (!numero.matches("[0-9]{8}")) throw new ReglaNegocioException("El DNI debe tener 8 dígitos.");
            }
            case RUC -> {
                if (!numero.matches("[0-9]{11}")) throw new ReglaNegocioException("El RUC debe tener 11 dígitos.");
            }
            case OTRO -> {
                if (numero.isEmpty()) throw new ReglaNegocioException("Ingresa el número de documento.");
            }
        }
        if (!numero.isEmpty() && proveedores.findByEmpresaIdAndTipoIdentificacionAndNumeroDocumento(
                empresaId, form.getTipoIdentificacion(), numero)
                .filter(c -> !Objects.equals(c.getId(), form.getId())).isPresent()) {
            throw new ReglaNegocioException("Ya existe un proveedor con ese documento, incluso si está inactivo.");
        }
        Proveedor proveedor = form.getId() == null ? new Proveedor() : buscar(empresaId, form.getId());
        if (!Objects.equals(proveedor.getVersion(), form.getVersion())) {
            throw new ReglaNegocioException("El proveedor cambió mientras editabas. Vuelve a abrirlo.");
        }
        if (proveedor.getId() == null) proveedor.setEmpresa(empresas.findById(empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada.")));
        proveedor.setTipoIdentificacion(form.getTipoIdentificacion());
        proveedor.setNumeroDocumento(numero.isEmpty() ? null : numero);
        proveedor.setNombre(form.getNombre().trim().replaceAll("\\s+", " "));
        proveedor.setDireccion(limpiar(form.getDireccion()));
        proveedor.setTelefono(limpiar(form.getTelefono()));
        proveedor.setEmail(limpiar(form.getEmail()));
        return proveedores.save(proveedor);
    }

    @Transactional
    public void cambiarEstado(Long empresaId, Long id) {
        empresas.bloquearParaUsuarios(empresaId).orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada."));
        Proveedor proveedor = buscar(empresaId, id);
        proveedor.setEstado(!proveedor.getEstado());
    }

    private String limpiar(String texto) { return texto == null ? "" : texto.trim(); }
}
