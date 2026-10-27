package sistema_maderera.service;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import sistema_maderera.entity.*;
import sistema_maderera.exception.ReglaNegocioException;
import sistema_maderera.repository.*;

@Service
public class NumeracionDocumentoService {
    private final CorrelativoDocumentoRepository correlativos;
    private final SedeRepository sedes;

    public NumeracionDocumentoService(CorrelativoDocumentoRepository correlativos, SedeRepository sedes) {
        this.correlativos = correlativos;
        this.sedes = sedes;
    }

    // La futura confirmación debe asignar número y guardar la venta en la misma transacción.
    @Transactional(propagation = Propagation.MANDATORY)
    public Numero siguiente(Long sedeId, TipoDocumentoVenta tipo) {
        if (tipo == null) throw new ReglaNegocioException("Selecciona el tipo de documento.");
        Sede sede = sedes.bloquearParaNumeracion(sedeId)
                .orElseThrow(() -> new ReglaNegocioException("Sede no encontrada."));
        if (!sede.getEstado()) throw new ReglaNegocioException("La sede está inactiva.");
        CorrelativoDocumento registro = correlativos.findBySedeIdAndTipo(sedeId, tipo).orElse(null);
        if (registro == null) {
            registro = new CorrelativoDocumento();
            registro.setSede(sede);
            registro.setTipo(tipo);
        }
        if (registro.getUltimo() == Long.MAX_VALUE) throw new ReglaNegocioException("Numeración agotada.");
        registro.setUltimo(registro.getUltimo() + 1);
        correlativos.save(registro);
        return new Numero(tipo.serie(sede.getCodigoDocumentos()), registro.getUltimo());
    }

    @Transactional(readOnly = true)
    public Map<TipoDocumentoVenta, Numero> proximos(Long sedeId) {
        Sede sede = sedes.findById(sedeId).orElseThrow(() -> new ReglaNegocioException("Sede no encontrada."));
        Map<TipoDocumentoVenta, Numero> numeros = new EnumMap<>(TipoDocumentoVenta.class);
        for (TipoDocumentoVenta tipo : TipoDocumentoVenta.values()) {
            long ultimo = correlativos.findBySedeIdAndTipo(sedeId, tipo).map(c -> c.getUltimo()).orElse(0L);
            if (ultimo == Long.MAX_VALUE) throw new ReglaNegocioException("Numeración agotada.");
            numeros.put(tipo, new Numero(tipo.serie(sede.getCodigoDocumentos()), ultimo + 1));
        }
        return numeros;
    }

    public record Numero(String serie, long correlativo) {
        public String getNumero() { return String.format(Locale.ROOT, "%04d", correlativo); }
        public String getCodigo() { return serie + "-" + getNumero(); }
    }
}
