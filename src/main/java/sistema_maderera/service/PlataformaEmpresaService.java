package sistema_maderera.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sistema_maderera.entity.Empresa;
import sistema_maderera.dto.PlataformaEmpresaForm;
import sistema_maderera.dto.SedeForm;
import sistema_maderera.dto.UsuarioForm;
import sistema_maderera.exception.ReglaNegocioException;
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

    @Transactional
    public Empresa crear(PlataformaEmpresaForm form) {
        validacion.validar(form);
        var datos = form.getEmpresa();
        if (datos.getRuc() == null || datos.getRuc().isBlank()) {
            throw new ReglaNegocioException("RUC: Campo obligatorio para registrar una empresa.");
        }
        if (empresas.existsByRuc(datos.getRuc())) {
            throw new ReglaNegocioException("Ese RUC ya pertenece a una empresa registrada.");
        }

        Empresa empresa = new Empresa();
        empresa.setNombreComercial(limpiar(datos.getNombreComercial()));
        empresa.setRazonSocial(limpiar(datos.getRazonSocial()));
        empresa.setRuc(datos.getRuc());
        empresa.setEmail(limpiar(datos.getEmail()));
        empresa.setTelefono(limpiar(datos.getTelefono()));
        empresa.setDireccion(limpiar(datos.getDireccion()));
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

    private String limpiar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim().replaceAll("\\s+", " ");
    }
}
