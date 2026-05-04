package semantico;

import ast.T;
import ast.Parametro;
import java.util.List;

/**
 * Metadatos de una función para la tabla de símbolos.
 */
public class InfoFuncion {
    private T tipoRetorno;
    private List<Parametro> parametros;

    public InfoFuncion(T tipoRetorno, List<Parametro> parametros) {
        this.tipoRetorno = tipoRetorno;
        this.parametros = parametros;
    }

    public T tipoRetorno() { return tipoRetorno; }
    public List<Parametro> parametros() { return parametros; }
}
