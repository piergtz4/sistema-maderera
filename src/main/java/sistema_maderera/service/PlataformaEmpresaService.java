package sistema_maderera.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sistema_maderera.entity.Empresa;
import sistema_maderera.dto.EmpresaForm;
import sistema_maderera.dto.PlataformaEmpresaForm;
import sistema_maderera.dto.SedeForm;
import sistema_maderera.dto.UsuarioForm;
import sistema_maderera.exception.ReglaNegocioException;
import sistema_maderera.exception.RecursoNoEncontradoException;
import sistema_maderera.repository.EmpresaRepository;

@Service
@Transactional(readOnly = true)
public class PlataformaEmpresaService {

    private final EmpresaRepository empresas;
    private final SedeService sedes;
    private final UsuarioGestionService usuarios;
    private final ValidacionService validacion;

    public PlataformaEmpresaService(EmpresaRepository empresas, SedeService sedes,
            UsuarioGestionService usuarios, ValidacionService validacion) {
        this.empresas = empresas;
        this.sedes = sedes;
        this.usuarios = usuarios;
        this.validacion = validacion;
    }

    public List<Empresa> listar() {
        return empresas.findAllByOrderByNombreComercialAsc();
    }

    public List<Empresa> listar(Boolean activas) {
        return activas == null ? listar() : empresas.findByEstadoOrderByNombreComercialAsc(activas);
    }

    public EmpresaForm formulario(Long id) {
        Empresa empresa = buscar(id);
        EmpresaForm form = new EmpresaForm();
        form.setNombreComercial(empresa.getNombreComercial());
        form.setRazonSocial(empresa.getRazonSocial());
        form.setRuc(empresa.getRuc());
        form.setEmail(empresa.getEmail());
        form.setTelefono(empresa.getTelefono());
        form.setDireccion(empresa.getDireccion());
        return form;
    }

    @Transactional
    public Empresa editar(Long id, EmpresaForm form) {
        validacion.validar(form);
        Empresa empresa = bloquear(id);
        validarRuc(form.getRuc(), id);
        aplicarDatos(empresa, form);
        return empresas.saveAndFlush(empresa);
    }

    @Transactional
    public void cambiarEstado(Long id, boolean activo) {
        Empresa empresa = bloquear(id);
        if (Boolean.TRUE.equals(empresa.getEstado()) != activo) {
            empresa.setEstado(activo);
            empresa.setVersionAcceso(empresa.getVersionAcceso() + 1);
        }
    }

    @Transactional
    public Empresa crear(PlataformaEmpresaForm form) {
        validacion.validar(form);
        var datos = form.getEmpresa();
        validarRuc(datos.getRuc(), null);

        Empresa empresa = new Empresa();
        aplicarDatos(empresa, datos);
        empresas.saveAndFlush(empresa);

        SedeForm sede = new SedeForm();
        sede.setNombre(form.getSede().getNombre());
        sede.setDepartamento(form.getSede().getDepartamento());
        sede.setProvincia(form.getSede().getProvincia());
        sede.setDistrito(form.getSede().getDistrito());
        sede.setDireccion(form.getSede().getDireccion());
        sede.setTelefono(form.getSede().getTelefono());
        sede.setCodigoDocumentos(1);
        sedes.guardar(empresa.getId(), sede);

        UsuarioForm administrador = new UsuarioForm();
        administrador.setNombre(form.getNombreAdministrador());
        administrador.setUsuario(form.getUsuario());
        administrador.setPassword(form.getPassword());
        administrador.setConfirmarPassword(form.getConfirmarPassword());
        administrador.setRol("ADMINISTRADOR_GENERAL");
        usuarios.guardar(empresa.getId(), null, administrador);
        return empresa;
    }

    private Empresa buscar(Long id) {
        return empresas.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada."));
    }

    private Empresa bloquear(Long id) {
        return empresas.bloquearParaUsuarios(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada."));
    }

    private void validarRuc(String ruc, Long id) {
        if (ruc == null || ruc.isBlank()) {
            throw new ReglaNegocioException("RUC: Campo obligatorio.");
        }
        if (id == null ? empresas.existsByRuc(ruc) : empresas.existsByRucAndIdNot(ruc, id)) {
            throw new ReglaNegocioException("Ese RUC ya pertenece a una empresa registrada.");
        }
    }

    private void aplicarDatos(Empresa empresa, EmpresaForm form) {
        empresa.setNombreComercial(limpiar(form.getNombreComercial()));
        empresa.setRazonSocial(limpiar(form.getRazonSocial()));
        empresa.setRuc(form.getRuc());
        empresa.setEmail(limpiar(form.getEmail()));
        empresa.setTelefono(limpiar(form.getTelefono()));
        empresa.setDireccion(limpiar(form.getDireccion()));
    }

    private String limpiar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim().replaceAll("\\s+", " ");
    }
}
