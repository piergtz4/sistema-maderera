package sistema_maderera.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sistema_maderera.entity.Empresa;
import sistema_maderera.repository.EmpresaRepository;

@Service
@Transactional(readOnly = true)
public class PlataformaEmpresaService {

    private final EmpresaRepository empresas;

    public PlataformaEmpresaService(EmpresaRepository empresas) {
        this.empresas = empresas;
    }

    public List<Empresa> listar() {
        return empresas.findAllByOrderByNombreComercialAsc();
    }
}