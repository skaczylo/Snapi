package semantico;

import java.util.*;

public class TablaSimbolos {
    private Deque<Map<String, Object>> pilaAmbitos;

    public TablaSimbolos() {
        pilaAmbitos = new ArrayDeque<>();
        abrirAmbito(); // ambito global
    }

    public void abrirAmbito() {
        pilaAmbitos.push(new LinkedHashMap<>());
    }

    public void cerrarAmbito() {
        pilaAmbitos.pop();
    }

    /** True si nombre ya existe en el ambito mas interno */
    public boolean estaDeclaradoLocal(String nombre) {
        return pilaAmbitos.peek().containsKey(nombre);
    }

    /** Declara nombre en el ambito actual. Llamar solo tras comprobar que no existe. */
    public void declarar(String nombre, Object info) {
        pilaAmbitos.peek().put(nombre, info);
    }

    /** Busca nombre de dentro hacia fuera; null si no se encuentra. */
    public Object buscar(String nombre) {
        for (Map<String, Object> ambito : pilaAmbitos) {
            if (ambito.containsKey(nombre)) return ambito.get(nombre);
        }
        return null;
    }
}
