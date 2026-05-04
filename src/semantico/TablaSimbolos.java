package semantico;

import java.util.*;

/**
 * Gestión de ámbitos anidados mediante una pila de mapas.
 */
public class TablaSimbolos {
    private Deque<Map<String, Object>> pilaAmbitos;

    public TablaSimbolos() {
        pilaAmbitos = new ArrayDeque<>();
        abrirAmbito(); // Ámbito global
    }

    public void abrirAmbito() {
        pilaAmbitos.push(new LinkedHashMap<>());
    }

    public void cerrarAmbito() {
        pilaAmbitos.pop();
    }

    public boolean estaDeclaradoLocal(String nombre) {
        return pilaAmbitos.peek().containsKey(nombre);
    }

    public void declarar(String nombre, Object info) {
        pilaAmbitos.peek().put(nombre, info);
    }

    /**
     * Busca un símbolo desde el ámbito actual hacia el global.
     */
    public Object buscar(String nombre) {
        for (Map<String, Object> ambito : pilaAmbitos) {
            if (ambito.containsKey(nombre)) {
                return ambito.get(nombre);
            }
        }
        return null;
    }
}
