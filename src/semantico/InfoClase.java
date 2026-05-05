package semantico;

import ast.DecVar;
import ast.DecFuncion;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Metadatos de una clase para la tabla de simbolos.
 * Incluye los campos (variables miembro) y metodos en orden de declaracion.
 */
public class InfoClase {
    private String nombre;
    private Map<String, InfoVariable> campos;
    private Map<String, InfoFuncion> metodos;

    public InfoClase(String nombre, List<DecVar> camposList, List<DecFuncion> metodosList) {
        this.nombre = nombre;
        this.campos = new LinkedHashMap<>();
        this.metodos = new LinkedHashMap<>();
        for (DecVar c : camposList) {
            this.campos.put(c.nombre(), new InfoVariable(c.tipo(), false, false));
        }
        for (DecFuncion m : metodosList) {
            this.metodos.put(m.nombre(), new InfoFuncion(m.tipoRetorno(), m.parametros()));
        }
    }

    public String nombre() { return nombre; }
    public Map<String, InfoVariable> campos() { return campos; }
    public Map<String, InfoFuncion> metodos() { return metodos; }
}
