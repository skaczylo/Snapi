package semantico;

import ast.*;
import errors.GestionErrores;
import java.util.*;

/**
 * Comprobación de tipos y gestión de ámbitos sobre el AST.
 */
public class AnalizadorSemantico {
    private TablaSimbolos tabla;
    private GestionErrores errores;
    private T tipoRetornoActual; // Para validar sentencias return
    private Map<E, T> tipos;     // Anotaciones de tipos para el generador

    public AnalizadorSemantico() {
        this.tabla = new TablaSimbolos();
        this.errores = new GestionErrores();
        this.tipos = new IdentityHashMap<>();
    }

    public Map<E, T> getTipos() { return tipos; }

    /**
     * Inicia el análisis del programa.
     */
    public void analizar(Programa p) {
        for (I inst : p.instrucciones()) {
            analizarInstruccion(inst);
        }
    }

    private void analizarInstruccion(I inst) {
        if (inst instanceof DecFuncion) {
            analizarDecFuncion((DecFuncion) inst);
        } else {
            analizarStmt((Stmt) inst);
        }
    }

    /**
     * Valida declaración de funciones, parámetros y cuerpo.
     */
    private void analizarDecFuncion(DecFuncion dec) {
        String nombre = dec.nombre();
        if (tabla.estaDeclaradoLocal(nombre)) {
            errores.errorSemantico("La funcion '" + nombre + "' ya ha sido declarada");
        }
        tabla.declarar(nombre, new InfoFuncion(dec.tipoRetorno(), dec.parametros()));

        tabla.abrirAmbito();
        T tipoRetAnterior = tipoRetornoActual;
        tipoRetornoActual = dec.tipoRetorno();

        for (Parametro param : dec.parametros()) {
            if (tabla.estaDeclaradoLocal(param.nombre())) {
                errores.errorSemantico("Parametro duplicado: " + param.nombre());
            }
            tabla.declarar(param.nombre(), new InfoVariable(param.tipo(), true, param.esReferencia()));
        }

        for (Stmt stmt : dec.cuerpo().instrucciones()) {
            analizarStmt(stmt);
        }

        tipoRetornoActual = tipoRetAnterior;
        tabla.cerrarAmbito();
    }

    private void analizarStmt(Stmt stmt) {
        switch (stmt.nodeKind()) {
            case DEC_VAR:    analizarDecVar((DecVar) stmt);          break;
            case ASIGNACION: analizarAsignacion((Asignacion) stmt);  break;
            case BLOQUE:     analizarBloque((Bloque) stmt);          break;
            case IF:         analizarIf((If) stmt);                  break;
            case WHILE:      analizarWhile((While) stmt);            break;
            case READ:       analizarRead((Read) stmt);              break;
            case PRINT:      analizarPrint((Print) stmt);            break;
            case RETURN:     analizarReturn((Return) stmt);          break;
        }
    }

    private void analizarDecVar(DecVar dec) {
        if (tabla.estaDeclaradoLocal(dec.nombre())) {
            errores.errorSemantico("Variable ya declarada: " + dec.nombre());
        }
        if (dec.tieneInit()) {
            T tipoInit = analizarExpr(dec.init());
            if (!tiposCompatibles(dec.tipo(), tipoInit)) {
                errores.errorSemantico("Tipo incompatible en init de " + dec.nombre());
            }
        }
        tabla.declarar(dec.nombre(), new InfoVariable(dec.tipo(), false, false));
    }

    private void analizarAsignacion(Asignacion asig) {
        T tipoLhs = analizarLhs(asig.lhs());
        T tipoRhs = analizarExpr(asig.rhs());
        if (!tiposCompatibles(tipoLhs, tipoRhs)) {
            errores.errorSemantico("Tipos incompatibles en asignacion");
        }
    }

    /**
     * Analiza el lado izquierdo (lvalue) y anota su tipo.
     */
    private T analizarLhs(E lhs) {
        T tipo;
        if (lhs instanceof Id) {
            String nombre = ((Id) lhs).nombre();
            Object info = tabla.buscar(nombre);
            if (info == null) {
                errores.errorSemantico("Variable no declarada: " + nombre);
                return null;
            }
            if (!(info instanceof InfoVariable)) {
                errores.errorSemantico(nombre + " no es una variable");
                return null;
            }
            tipo = ((InfoVariable) info).tipo();
        } else if (lhs instanceof AccesoArray) {
            AccesoArray acc = (AccesoArray) lhs;
            T tipoBase = analizarLhs(acc.array());
            T tipoIdx  = analizarExpr(acc.indice());
            if (!esInt(tipoIdx)) {
                errores.errorSemantico("Indice de array debe ser int");
            }
            if (!(tipoBase instanceof TipoArray)) {
                errores.errorSemantico("No es un array: " + tipoBase);
                return null;
            }
            tipo = tipoElementoArray((TipoArray) tipoBase);
        } else {
            errores.errorSemantico("LHS invalido");
            return null;
        }
        tipos.put(lhs, tipo);
        return tipo;
    }

    /**
     * Analiza un bloque de sentencias.
     *
     * Un bloque abre un nuevo ámbito para las variables declaradas dentro,
     * implementando el scoping lexical anidado de Snapi.
     * Las variables locales del bloque no son accesibles fuera de él.
     *
     * @param bloque  el nodo Bloque del AST
     */
    private void analizarBloque(Bloque bloque) {
        tabla.abrirAmbito();
        for (Stmt stmt : bloque.instrucciones()) {
            analizarStmt(stmt);
        }
        tabla.cerrarAmbito();
    }

    /**
     * Analiza una sentencia condicional if/else.
     *
     * Validaciones:
     *   - La condición debe ser de tipo bool
     *   - El bloque then es obligatorio
     *   - El bloque else es opcional
     *
     * Cada bloque abre su propio ámbito para variables locales.
     *
     * @param ifStmt  el nodo If del AST
     */
    private void analizarIf(If ifStmt) {
        T tipoCond = analizarExpr(ifStmt.condicion());
        if (!esBool(tipoCond)) {
            errores.errorSemantico("La condicion del if debe ser bool, pero es " + tipoCond);
        }
        analizarBloque(ifStmt.thenBloque());
        if (ifStmt.tieneElse()) {
            analizarBloque(ifStmt.elseBloque());
        }
    }

    /**
     * Analiza un bucle while.
     *
     * Validaciones:
     *   - La condición debe ser de tipo bool
     *   - El cuerpo puede contener cualquier sentencia válida
     *
     * El cuerpo abre su propio ámbito para variables locales.
     *
     * @param w  el nodo While del AST
     */
    private void analizarWhile(While w) {
        T tipoCond = analizarExpr(w.condicion());
        if (!esBool(tipoCond)) {
            errores.errorSemantico("La condicion del while debe ser bool, pero es " + tipoCond);
        }
        analizarBloque(w.cuerpo());
    }

    /**
     * Analiza una operación de lectura (read).
     *
     * read(x) lee un valor del flujo de entrada (implementado por el runtime)
     * y lo almacena en la variable x.
     *
     * Validaciones:
     *   - La variable debe existir
     *   - La variable debe ser de tipo int o bool (los únicos tipos que se pueden leer)
     *
     * @param read  el nodo Read del AST
     */
    private void analizarRead(Read read) {
        Object info = tabla.buscar(read.nombre());
        if (info == null) {
            errores.errorSemantico("Variable no declarada: '" + read.nombre() + "'");
            return;
        }
        if (!(info instanceof InfoVariable)) {
            errores.errorSemantico("'" + read.nombre() + "' no es una variable");
            return;
        }
        T tipo = ((InfoVariable) info).tipo();
        if (!esInt(tipo) && !esBool(tipo)) {
            errores.errorSemantico("read() solo puede leer variables int o bool, no " + tipo);
        }
    }

    /**
     * Analiza una operación de escritura (print).
     *
     * print(expr) evalúa la expresión y la escribe en el flujo de salida
     * (implementado por el runtime). La expresión puede ser int o bool.
     *
     * @param print  el nodo Print del AST
     */
    private void analizarPrint(Print print) {
        analizarExpr(print.expr());
    }

    /**
     * Analiza una sentencia de retorno.
     *
     * Validaciones:
     *   - Solo es válido dentro de una función (tipoRetornoActual no null)
     *   - Para funciones void: no puede haber expresión de retorno
     *   - Para funciones no-void: debe haber expresión de retorno con tipo compatible
     *
     * tipoRetornoActual se establece en analizarDecFuncion y se restaura tras
     * analizar el cuerpo, permitiendo validar retornos correctos en cada función.
     *
     * @param ret  el nodo Return del AST
     */
    private void analizarReturn(Return ret) {
        if (tipoRetornoActual == null) {
            errores.errorSemantico("Sentencia return fuera de una funcion");
        }
        if (esVoid(tipoRetornoActual)) {
            if (ret.tieneExpr()) {
                errores.errorSemantico("Una funcion void no puede retornar un valor");
            }
        } else {
            if (!ret.tieneExpr()) {
                errores.errorSemantico("La funcion debe retornar un valor de tipo "
                    + tipoRetornoActual);
            } else {
                T tipoExpr = analizarExpr(ret.expr());
                if (!tiposCompatibles(tipoRetornoActual, tipoExpr)) {
                    errores.errorSemantico("Tipo de retorno incorrecto: se esperaba "
                        + tipoRetornoActual + " pero se obtuvo " + tipoExpr);
                }
            }
        }
    }

    // ---------------------------------------------------------------
    // Análisis de expresiones: inferencia de tipos y anotación
    // ---------------------------------------------------------------

    /**
     * Punto de entrada para análisis de expresiones.
     *
     * Operaciones:
     *   1. Calcula el tipo de la expresión mediante calcularTipo
     *   2. Anota la expresión en el mapa de tipos para posterior consulta
     *      (crucial para el generador de código)
     *   3. Retorna el tipo para que el llamador pueda hacer validaciones
     *
     * La anotación es especialmente importante para:
     *   - Expresiones de acceso a array: necesita conocer el tipo base
     *     para calcular strides en el generador
     *   - Expresiones de parámetros por referencia: necesita saber si son
     *     referencias para generar código de desreferenciación
     *
     * @param expr  la expresión a analizar
     * @return      el tipo inferido de la expresión
     */
    public T analizarExpr(E expr) {
        T tipo = calcularTipo(expr);
        tipos.put(expr, tipo); // anotación: crucial para el generador
        return tipo;
    }

    /**
     * Calcula el tipo de una expresión mediante análisis recursivo y validación.
     *
     * Maneja todos los tipos de expresiones:
     *   - Literales (NUM, BOOL): tipos base inmediatos
     *   - Identificadores (ID): búsqueda en tabla de símbolos
     *   - Operaciones binarias aritméticas (SUMA, RESTA, MUL, DIV): int × int → int
     *   - Comparaciones (MENOR, MAYOR): int × int → bool
     *   - Igualdad (IGUALDAD): T × T → bool (para cualquier tipo compatible)
     *   - Lógicas (AND, OR): bool × bool → bool
     *   - Unarias (MENOS_UNARIO): int → int
     *   - Array access (ACCESO_ARRAY): array[n][m]... × int → tipo del elemento
     *   - Llamadas a función (LLAMADA_FUNCION): verifica argumentos y retorna tipo
     *
     * Estrategia: valida cada expresión antes de retornar su tipo, fallando
     * inmediatamente si hay incompatibilidad.
     *
     * @param expr  la expresión a analizar
     * @return      el tipo inferido, o null si hay error
     */
    private T calcularTipo(E expr) {
        switch (expr.kind()) {

            case NUM:
                // Literales numéricos siempre son int
                return new TipoBasico("int");

            case BOOL:
                // Literales booleanos (true/false) siempre son bool
                return new TipoBasico("bool");

            case ID: {
                // Identificador: buscar variable en tabla de símbolos
                String nombre = ((Id) expr).nombre();
                Object info = tabla.buscar(nombre);
                if (info == null) {
                    errores.errorSemantico("Variable no declarada: '" + nombre + "'");
                    return null;
                }
                if (!(info instanceof InfoVariable)) {
                    errores.errorSemantico("'" + nombre + "' es una funcion, no una variable");
                    return null;
                }
                return ((InfoVariable) info).tipo();
            }

            case SUMA: case RESTA: case MUL: case DIV: {
                // Operaciones aritméticas: int ⊕ int → int
                T t1 = analizarExpr(expr.opnd1());
                T t2 = analizarExpr(expr.opnd2());
                if (!esInt(t1) || !esInt(t2)) {
                    errores.errorSemantico("Los operandos de operaciones aritmeticas deben ser int");
                }
                return new TipoBasico("int");
            }

            case MENOR: case MAYOR: {
                // Comparaciones: int < int → bool, int > int → bool
                T t1 = analizarExpr(expr.opnd1());
                T t2 = analizarExpr(expr.opnd2());
                if (!esInt(t1) || !esInt(t2)) {
                    errores.errorSemantico("Los operandos de < y > deben ser int");
                }
                return new TipoBasico("bool");
            }

            case IGUALDAD: {
                // Igualdad: T == T → bool (ambos operandos deben tener el mismo tipo)
                T t1 = analizarExpr(expr.opnd1());
                T t2 = analizarExpr(expr.opnd2());
                if (!tiposCompatibles(t1, t2)) {
                    errores.errorSemantico("Los operandos de == deben ser del mismo tipo");
                }
                return new TipoBasico("bool");
            }

            case AND: case OR: {
                // Conectivas lógicas: bool ∧ bool → bool, bool ∨ bool → bool
                T t1 = analizarExpr(expr.opnd1());
                T t2 = analizarExpr(expr.opnd2());
                if (!esBool(t1) || !esBool(t2)) {
                    errores.errorSemantico("Los operandos de and/or deben ser bool");
                }
                return new TipoBasico("bool");
            }

            case MENOS_UNARIO: {
                // Negación unaria: -int → int
                T t = analizarExpr(expr.opnd1());
                if (!esInt(t)) {
                    errores.errorSemantico("El operando de la negacion unaria debe ser int");
                }
                return new TipoBasico("int");
            }

            case ACCESO_ARRAY: {
                // Indexación: array[i] recorre la jerarquía de tipos hasta obtener
                // el tipo del elemento. Para arrays multidimensionales, la recursión
                // en analizarLhs proporciona el tipo "un nivel más profundo".
                AccesoArray acc = (AccesoArray) expr;
                T tipoBase = analizarExpr(acc.array());
                T tipoIdx  = analizarExpr(acc.indice());
                if (!esInt(tipoIdx)) {
                    errores.errorSemantico("El indice de un array debe ser de tipo int");
                }
                if (!(tipoBase instanceof TipoArray)) {
                    errores.errorSemantico("No se puede indexar un tipo no-array: " + tipoBase);
                    return null;
                }
                return tipoElementoArray((TipoArray) tipoBase);
            }

            case LLAMADA_FUNCION: {
                // Llamada a función: valida argumento s y retorna tipo de retorno
                LlamadaFuncion lf = (LlamadaFuncion) expr;
                Object info = tabla.buscar(lf.nombre());
                if (info == null || !(info instanceof InfoFuncion)) {
                    errores.errorSemantico("Funcion no declarada: '" + lf.nombre() + "'");
                    return null;
                }
                InfoFuncion inf = (InfoFuncion) info;
                List<Parametro> params = inf.parametros();
                List<E> args = lf.args();

                // Validar número de argumentos
                if (args.size() != params.size()) {
                    errores.errorSemantico("Numero de argumentos incorrecto en llamada a '"
                        + lf.nombre() + "': se esperaban " + params.size()
                        + " pero se pasaron " + args.size());
                }

                // Validar tipo de cada argumento
                int n = Math.min(args.size(), params.size());
                for (int i = 0; i < n; i++) {
                    T tipoArg = analizarExpr(args.get(i));
                    if (!tiposCompatibles(params.get(i).tipo(), tipoArg)) {
                        errores.errorSemantico("Tipo incorrecto en el argumento " + (i + 1)
                            + " de la llamada a '" + lf.nombre()
                            + "': se esperaba " + params.get(i).tipo()
                            + " pero se obtuvo " + tipoArg);
                    }
                }
                return inf.tipoRetorno();
            }
        }
        errores.errorSemantico("Expresion de tipo desconocido");
        return null;
    }

    // ---------------------------------------------------------------
    // Utilidades de tipos
    // ---------------------------------------------------------------

    /** Tipo del elemento al indexar un TipoArray una dimension. */
    private T tipoElementoArray(TipoArray arr) {
        List<Integer> dims = arr.dimensiones();
        if (dims.size() == 1) {
            return arr.tipoBase();
        }
        return new TipoArray(new ArrayList<>(dims.subList(1, dims.size())), arr.tipoBase());
    }

    private boolean esInt(T t) {
        return t instanceof TipoBasico && ((TipoBasico) t).nombre().equals("int");
    }

    private boolean esBool(T t) {
        return t instanceof TipoBasico && ((TipoBasico) t).nombre().equals("bool");
    }

    private boolean esVoid(T t) {
        return t instanceof TipoBasico && ((TipoBasico) t).nombre().equals("void");
    }

    private boolean tiposCompatibles(T t1, T t2) {
        if (t1 == null || t2 == null) return false;
        if (t1 instanceof TipoBasico && t2 instanceof TipoBasico) {
            return ((TipoBasico) t1).nombre().equals(((TipoBasico) t2).nombre());
        }
        if (t1 instanceof TipoArray && t2 instanceof TipoArray) {
            TipoArray a1 = (TipoArray) t1;
            TipoArray a2 = (TipoArray) t2;
            return a1.dimensiones().equals(a2.dimensiones())
                && tiposCompatibles(a1.tipoBase(), a2.tipoBase());
        }
        return false;
    }
}
