package sistema_maderera.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import sistema_maderera.dto.EmpresaForm;
import sistema_maderera.entity.Empresa;
import sistema_maderera.exception.RecursoNoEncontradoException;
import sistema_maderera.exception.ReglaNegocioException;
import sistema_maderera.repository.EmpresaRepository;

@Service
@Transactional(readOnly = true)
public class EmpresaService {
    private static final long MAX_LOGO = 2L * 1024 * 1024;
    private final EmpresaRepository empresas;
    private final ValidacionService validacion;

    public EmpresaService(EmpresaRepository empresas, ValidacionService validacion) {
        this.empresas = empresas;
        this.validacion = validacion;
    }

    public Empresa buscar(Long id) {
        return empresas.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada."));
    }

    public String nombre(Long id) {
        return empresas.buscarNombre(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada."));
    }

    public boolean tieneLogo(Long id) {
        return empresas.existsByIdAndLogoDatosIsNotNull(id);
    }

    public EmpresaForm formulario(Long id) {
        Empresa empresa = buscar(id);
        EmpresaForm form = new EmpresaForm();
        form.setNombreComercial(empresa.getNombreComercial());
        form.setRazonSocial(empresa.getRazonSocial());
        form.setRuc(empresa.getRuc());
        form.setTelefono(empresa.getTelefono());
        form.setEmail(empresa.getEmail());
        form.setDireccion(empresa.getDireccion());
        return form;
    }

    @Transactional
    public Empresa guardar(Long id, EmpresaForm form, MultipartFile logo) {
        validacion.validar(form);
        Empresa empresa = buscar(id);
        empresa.setNombreComercial(limpiar(form.getNombreComercial()));
        empresa.setRazonSocial(limpiarOpcional(form.getRazonSocial()));
        empresa.setRuc(limpiarOpcional(form.getRuc()));
        empresa.setTelefono(limpiarOpcional(form.getTelefono()));
        empresa.setEmail(limpiarOpcional(form.getEmail()));
        empresa.setDireccion(limpiarOpcional(form.getDireccion()));
        if (form.isEliminarLogo()) {
            empresa.setLogoDatos(null);
            empresa.setLogoTipo(null);
        }
        if (logo != null && !logo.isEmpty()) guardarLogo(empresa, logo);
        return empresas.save(empresa);
    }

    public LogoEmpresa logo(Long id) {
        Empresa empresa = buscar(id);
        if (empresa.getLogoDatos() == null) {
            throw new RecursoNoEncontradoException("La empresa no tiene un logo configurado.");
        }
        return new LogoEmpresa(empresa.getLogoDatos(), empresa.getLogoTipo());
    }

    private void guardarLogo(Empresa empresa, MultipartFile logo) {
        if (logo.getSize() > MAX_LOGO) throw new ReglaNegocioException("El logo no puede superar 2 MB.");
        try {
            byte[] datos = logo.getBytes();
            String tipo = detectarTipo(datos);
            if (tipo == null) throw new ReglaNegocioException("El logo debe ser una imagen PNG, JPG o WEBP válida.");
            empresa.setLogoDatos(datos);
            empresa.setLogoTipo(tipo);
        } catch (IOException e) {
            throw new ReglaNegocioException("No se pudo leer el archivo del logo.");
        }
    }

    private String detectarTipo(byte[] d) {
        if (d.length >= 8 && Arrays.equals(Arrays.copyOf(d, 8),
                new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10})) return "image/png";
        if (d.length >= 3 && d[0] == (byte) 255 && d[1] == (byte) 216 && d[2] == (byte) 255) return "image/jpeg";
        if (d.length >= 12 && new String(d, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                && new String(d, 8, 4, StandardCharsets.US_ASCII).equals("WEBP")) return "image/webp";
        return null;
    }

    private String limpiar(String valor) { return valor.trim().replaceAll("\\s+", " "); }
    private String limpiarOpcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim().replaceAll("\\s+", " ");
    }

    public record LogoEmpresa(byte[] datos, String tipo) {}
}
