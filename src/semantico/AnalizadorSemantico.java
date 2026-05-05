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
    private Map<String, InfoClase> clases; // Clases declaradas en el programa
    private InfoClase claseActual; // Clase del metodo en curso (null si fuera de metodo)

    public AnalizadorSemantico() {
        this.tabla = new TablaSimbolos();
        this.errores = new GestionErrores();
        this.tipos = new IdentityHashMap<>();
        this.clases = new LinkedHashMap<>();
    }

    public Map<E, T> getTipos() { return tipos; }
    public Map<String, InfoClase> getClases() { return clases; }

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
        } else if (inst instanceof DecClase) {
            analizarDecClase((DecClase) inst);
        } else {
            analizarStmt((Stmt) inst);
        }
    }

    /**
     * Analiza una declaracion de clase: registra la clase con sus campos y
     * metodos, y luego analiza cada metodo abriendo un ambito que expone los
     * campos como variables (this implicito) y los parametros del metodo.
     */
    private void analizarDecClase(DecClase dc) {
        if (clases.containsKey(dc.nombre()) || tabla.estaDeclaradoLocal(dc.nombre())) {
            errores.errorSemantico("La clase '" + dc.nombre() + "' ya ha sido declarada");
        }
        InfoClase info = new InfoClase(dc.nombre(), dc.campos(), dc.metodos());
        clases.put(dc.nombre(), info);

        // Comprobar duplicados en campos
        java.util.Set<String> nombresCampos = new java.util.HashSet<>();
        for (DecVar c : dc.campos()) {
            if (!nombresCampos.add(c.nombre())) {
                errores.errorSemantico("Campo duplicado '" + c.nombre()
                    + "' en la clase '" + dc.nombre() + "'");
            }
            // Los campos no admiten inicializador (la inicializacion va en init)
            if (c.tieneInit()) {
                errores.errorSemantico("Los campos no pueden tener inicializador: '"
                    + c.nombre() + "'");
            }
        }

        // Comprobar duplicados en metodos y analizarlos
        java.util.Set<String> nombresMetodos = new java.util.HashSet<>();
        for (DecFuncion m : dc.metodos()) {
            if (!nombresMetodos.add(m.nombre())) {
                errores.errorSemantico("Metodo duplicado '" + m.nombre()
                    + "' en la clase '" + dc.nombre() + "'");
            }
            analizarMetodo(info, m);
        }
    }

    /**
     * Analiza un metodo dentro del contexto de una clase: abre un ambito que
     * expone primero los campos (this implicito) y despues los parametros y
     * el cuerpo, igual que en analizarDecFuncion.
     */
    private void analizarMetodo(InfoClase clase, DecFuncion m) {
        InfoClase claseAnt = claseActual;
        claseActual = clase;
        T tipoRetAnt = tipoRetornoActual;
        tipoRetornoActual = m.tipoRetorno();

        // Ambito para campos (this implicito)
        tabla.abrirAmbito();
        for (Map.Entry<String, InfoVariable> e : clase.campos().entrySet()) {
            tabla.declarar(e.getKey(), e.getValue());
        }

        // Ambito para parametros y cuerpo
        tabla.abrirAmbito();
        for (Parametro param : m.parametros()) {
            if (tabla.estaDeclaradoLocal(param.nombre())) {
                errores.errorSemantico("Parametro duplicado: " + param.nombre());
            }
            tabla.declarar(param.nombre(),
                new InfoVariable(param.tipo(), true, param.esReferencia()));
        }
        for (Stmt stmt : m.cuerpo().instrucciones()) {
            analizarStmt(stmt);
        }
        tabla.cerrarAmbito();
        tabla.cerrarAmbito();

        tipoRetornoActual = tipoRetAnt;
        claseActual = claseAnt;
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
            case DEC_VAR:      analizarDecVar((DecVar) stmt);              break;
            case ASIGNACION:   analizarAsignacion((Asignacion) stmt);      break;
            case BLOQUE:       analizarBloque((Bloque) stmt);              break;
            case IF:           analizarIf((If) stmt);                      break;
            case WHILE:        analizarWhile((While) stmt);                break;
            case READ:         analizarRead((Read) stmt);                  break;
            case PRINT:        analizarPrint((Print) stmt);                break;
            case RETURN:       analizarReturn((Return) stmt);              break;
            case LLAMADA_STMT: analizarExpr(((LlamadaStmt) stmt).llamada()); break;
            default: break;
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
        } else if (lhs instanceof AccesoCampo) {
            AccesoCampo acc = (AccesoCampo) lhs;
            T tipoObj = analizarLhs(acc.objeto());
            if (!(tipoObj instanceof TipoClase)) {
                errores.errorSemantico("Solo los objetos tienen campos, no " + tipoObj);
                return null;
            }
            InfoClase clase = clases.get(((TipoClase) tipoObj).nombre());
            if (clase == null) {
                errores.errorSemantico("Clase desconocida: " + tipoObj);
                return null;
            }
            InfoVariable infoCampo = clase.campos().get(acc.campo());
            if (infoCampo == null) {
                errores.errorSemantico("La clase '" + clase.nombre()
                    + "' no tiene el campo '" + acc.campo() + "'");
                return null;
            }
            tipo = infoCampo.tipo();
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
        if (!esInt(tipo) && !esBool(tipo) && !esReal(tipo)) {
            errores.errorSemantico("read() solo puede leer variables int, real o bool, no " + tipo);
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
                // Literales numéricos enteros siempre son int
                return new TipoBasico("int");

            case NUM_REAL:
                // Literales numéricos con punto decimal siempre son real
                return new TipoBasico("real");

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
                // Operaciones aritméticas: int ⊕ int → int  ó  real ⊕ real → real
                T t1 = analizarExpr(expr.opnd1());
                T t2 = analizarExpr(expr.opnd2());
                if (esInt(t1) && esInt(t2)) {
                    return new TipoBasico("int");
                }
                if (esReal(t1) && esReal(t2)) {
                    return new TipoBasico("real");
                }
                errores.errorSemantico("Los operandos de operaciones aritmeticas deben ser ambos int o ambos real");
                return new TipoBasico("int");
            }

            case MENOR: case MAYOR: {
                // Comparaciones: int<int → bool  ó  real<real → bool
                T t1 = analizarExpr(expr.opnd1());
                T t2 = analizarExpr(expr.opnd2());
                if (!((esInt(t1) && esInt(t2)) || (esReal(t1) && esReal(t2)))) {
                    errores.errorSemantico("Los operandos de < y > deben ser ambos int o ambos real");
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
                // Negación unaria: -int → int  ó  -real → real
                T t = analizarExpr(expr.opnd1());
                if (esInt(t)) return new TipoBasico("int");
                if (esReal(t)) return new TipoBasico("real");
                errores.errorSemantico("El operando de la negacion unaria debe ser int o real");
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

            case ACCESO_CAMPO: {
                AccesoCampo ac = (AccesoCampo) expr;
                T tipoObj = analizarExpr(ac.objeto());
                if (!(tipoObj instanceof TipoClase)) {
                    errores.errorSemantico("Solo los objetos tienen campos, no " + tipoObj);
                    return null;
                }
                InfoClase clase = clases.get(((TipoClase) tipoObj).nombre());
                if (clase == null) {
                    errores.errorSemantico("Clase desconocida: " + tipoObj);
                    return null;
                }
                InfoVariable infoCampo = clase.campos().get(ac.campo());
                if (infoCampo == null) {
                    errores.errorSemantico("La clase '" + clase.nombre()
                        + "' no tiene el campo '" + ac.campo() + "'");
                    return null;
                }
                return infoCampo.tipo();
            }

            case LLAMADA_METODO: {
                LlamadaMetodo lm = (LlamadaMetodo) expr;
                T tipoObj = analizarExpr(lm.objeto());
                if (!(tipoObj instanceof TipoClase)) {
                    errores.errorSemantico("Solo los objetos tienen metodos, no " + tipoObj);
                    return null;
                }
                InfoClase clase = clases.get(((TipoClase) tipoObj).nombre());
                if (clase == null) {
                    errores.errorSemantico("Clase desconocida: " + tipoObj);
                    return null;
                }
                InfoFuncion infoMet = clase.metodos().get(lm.metodo());
                if (infoMet == null) {
                    errores.errorSemantico("La clase '" + clase.nombre()
                        + "' no tiene el metodo '" + lm.metodo() + "'");
                    return null;
                }
                List<Parametro> ps = infoMet.parametros();
                List<E> as = lm.args();
                if (as.size() != ps.size()) {
                    errores.errorSemantico("Numero de argumentos incorrecto en '"
                        + clase.nombre() + "." + lm.metodo() + "': se esperaban "
                        + ps.size() + " pero se pasaron " + as.size());
                }
                int n = Math.min(as.size(), ps.size());
                for (int i = 0; i < n; i++) {
                    T tipoArg = analizarExpr(as.get(i));
                    if (!tiposCompatibles(ps.get(i).tipo(), tipoArg)) {
                        errores.errorSemantico("Tipo incorrecto en argumento " + (i + 1)
                            + " de '" + clase.nombre() + "." + lm.metodo()
                            + "': se esperaba " + ps.get(i).tipo()
                            + " pero se obtuvo " + tipoArg);
                    }
                }
                return infoMet.tipoRetorno();
            }

            case NEW_INSTANCIA: {
                NewInstancia ni = (NewInstancia) expr;
                InfoClase clase = clases.get(ni.nombreClase());
                if (clase == null) {
                    errores.errorSemantico("Clase no declarada: " + ni.nombreClase());
                    return null;
                }
                InfoFuncion init = clase.metodos().get("init");
                if (init != null) {
                    List<Parametro> ps = init.parametros();
                    List<E> as = ni.args();
                    if (as.size() != ps.size()) {
                        errores.errorSemantico("Numero de argumentos incorrecto en new "
                            + ni.nombreClase() + "(): se esperaban " + ps.size()
                            + " pero se pasaron " + as.size());
                    }
                    int n = Math.min(as.size(), ps.size());
                    for (int i = 0; i < n; i++) {
                        T tipoArg = analizarExpr(as.get(i));
                        if (!tiposCompatibles(ps.get(i).tipo(), tipoArg)) {
                            errores.errorSemantico("Tipo incorrecto en argumento " + (i + 1)
                                + " de new " + ni.nombreClase() + "(): se esperaba "
                                + ps.get(i).tipo() + " pero se obtuvo " + tipoArg);
                        }
                    }
                } else if (!ni.args().isEmpty()) {
                    errores.errorSemantico("La clase '" + ni.nombreClase()
                        + "' no tiene metodo init pero se pasaron argumentos a new");
                }
                return new TipoClase(ni.nombreClase());
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

    private boolean esReal(T t) {
        return t instanceof TipoBasico && ((TipoBasico) t).nombre().equals("real");
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
        if (t1 instanceof TipoClase && t2 instanceof TipoClase) {
            return ((TipoClase) t1).nombre().equals(((TipoClase) t2).nombre());
        }
        return false;
    }
}
