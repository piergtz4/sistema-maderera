package sistema_maderera.service;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sistema_maderera.dto.ClienteForm;
import sistema_maderera.entity.*;
import sistema_maderera.exception.*;
import sistema_maderera.repository.*;

@Service
@Transactional(readOnly = true)
public class ClienteService {
    private final ClienteRepository clientes;
    private final EmpresaRepository empresas;
    private final ValidacionService validacion;

    public ClienteService(ClienteRepository clientes, EmpresaRepository empresas, ValidacionService validacion) {
        this.clientes = clientes;
        this.empresas = empresas;
        this.validacion = validacion;
    }

    public List<Cliente> listar(Long empresaId) { return clientes.findByEmpresaIdOrderByNombreAsc(empresaId); }

    public Cliente buscar(Long empresaId, Long id) {
        return clientes.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cliente no encontrado en tu empresa."));
    }

    public ClienteForm formulario(Long empresaId, Long id) {
        Cliente cliente = buscar(empresaId, id);
        ClienteForm form = new ClienteForm();
        form.setId(cliente.getId());
        form.setVersion(cliente.getVersion());
        form.setTipoIdentificacion(cliente.getTipoIdentificacion());
        form.setNumeroDocumento(cliente.getNumeroDocumento());
        form.setNombre(cliente.getNombre());
        form.setDireccion(cliente.getDireccion());
        form.setTelefono(cliente.getTelefono());
        form.setEmail(cliente.getEmail());
        return form;
    }

    @Transactional
    public Cliente guardar(Long empresaId, ClienteForm form) {
        validacion.validar(form);
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
        if (!numero.isEmpty() && clientes.findByEmpresaIdAndTipoIdentificacionAndNumeroDocumento(
                empresaId, form.getTipoIdentificacion(), numero)
                .filter(c -> !Objects.equals(c.getId(), form.getId())).isPresent()) {
            throw new ReglaNegocioException("Ya existe un cliente con ese documento, incluso si está inactivo.");
        }
        Cliente cliente = form.getId() == null ? new Cliente() : buscar(empresaId, form.getId());
        if (!Objects.equals(cliente.getVersion(), form.getVersion())) {
            throw new ReglaNegocioException("El cliente cambió mientras editabas. Vuelve a abrirlo.");
        }
        if (cliente.getId() == null) cliente.setEmpresa(empresas.findById(empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada.")));
        cliente.setTipoIdentificacion(form.getTipoIdentificacion());
        cliente.setNumeroDocumento(numero.isEmpty() ? null : numero);
        cliente.setNombre(form.getNombre().trim().replaceAll("\\s+", " "));
        cliente.setDireccion(limpiar(form.getDireccion()));
        cliente.setTelefono(limpiar(form.getTelefono()));
        cliente.setEmail(limpiar(form.getEmail()));
        return clientes.save(cliente);
    }

    @Transactional
    public void cambiarEstado(Long empresaId, Long id) {
        Cliente cliente = buscar(empresaId, id);
        cliente.setEstado(!cliente.getEstado());
    }

    private String limpiar(String texto) { return texto == null ? "" : texto.trim(); }
}
