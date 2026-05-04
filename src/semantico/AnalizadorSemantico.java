package semantico;

import ast.*;
import errors.GestionErrores;
import java.util.*;

public class AnalizadorSemantico {
    private TablaSimbolos tabla;
    private GestionErrores errores;
    private T tipoRetornoActual; // tipo de retorno de la funcion actualmente analizada
    private Map<E, T> tipos;    // anotaciones de tipo para cada nodo expresion

    public AnalizadorSemantico() {
        this.tabla = new TablaSimbolos();
        this.errores = new GestionErrores();
        this.tipos = new IdentityHashMap<>();
    }

    public Map<E, T> getTipos() { return tipos; }

    // ---------------------------------------------------------------
    // Punto de entrada
    // ---------------------------------------------------------------

    public void analizar(Programa p) {
        for (I inst : p.instrucciones()) {
            analizarInstruccion(inst);
        }
    }

    // ---------------------------------------------------------------
    // Instrucciones de nivel superior
    // ---------------------------------------------------------------

    private void analizarInstruccion(I inst) {
        if (inst instanceof DecFuncion) {
            analizarDecFuncion((DecFuncion) inst);
        } else {
            analizarStmt((Stmt) inst);
        }
    }

    private void analizarDecFuncion(DecFuncion dec) {
        String nombre = dec.nombre();
        if (tabla.estaDeclaradoLocal(nombre)) {
            errores.errorSemantico("La funcion '" + nombre + "' ya ha sido declarada en este ambito");
        }
        tabla.declarar(nombre, new InfoFuncion(dec.tipoRetorno(), dec.parametros()));

        tabla.abrirAmbito();
        T tipoRetAnterior = tipoRetornoActual;
        tipoRetornoActual = dec.tipoRetorno();

        for (Parametro param : dec.parametros()) {
            if (tabla.estaDeclaradoLocal(param.nombre())) {
                errores.errorSemantico("Parametro duplicado '" + param.nombre()
                    + "' en la funcion '" + nombre + "'");
            }
            tabla.declarar(param.nombre(), new InfoVariable(param.tipo(), true, param.esReferencia()));
        }

        // El cuerpo de la funcion comparte el ambito de los parametros
        for (Stmt stmt : dec.cuerpo().instrucciones()) {
            analizarStmt(stmt);
        }

        tipoRetornoActual = tipoRetAnterior;
        tabla.cerrarAmbito();
    }

    // ---------------------------------------------------------------
    // Sentencias
    // ---------------------------------------------------------------

    private void analizarStmt(Stmt stmt) {
        switch (stmt.nodeKind()) {
            case DEC_VAR:    analizarDecVar((DecVar) stmt);      break;
            case ASIGNACION: analizarAsignacion((Asignacion) stmt); break;
            case BLOQUE:     analizarBloque((Bloque) stmt);      break;
            case IF:         analizarIf((If) stmt);              break;
            case WHILE:      analizarWhile((While) stmt);        break;
            case READ:       analizarRead((Read) stmt);          break;
            case PRINT:      analizarPrint((Print) stmt);        break;
            case RETURN:     analizarReturn((Return) stmt);      break;
        }
    }

    private void analizarDecVar(DecVar dec) {
        if (tabla.estaDeclaradoLocal(dec.nombre())) {
            errores.errorSemantico("La variable '" + dec.nombre()
                + "' ya ha sido declarada en este ambito");
        }
        if (dec.tieneInit()) {
            T tipoInit = analizarExpr(dec.init());
            if (!tiposCompatibles(dec.tipo(), tipoInit)) {
                errores.errorSemantico("Tipo incompatible en la inicializacion de '"
                    + dec.nombre() + "': se esperaba " + dec.tipo()
                    + " pero se obtuvo " + tipoInit);
            }
        }
        tabla.declarar(dec.nombre(), new InfoVariable(dec.tipo(), false, false));
    }

    private void analizarAsignacion(Asignacion asig) {
        T tipoLhs = analizarLhs(asig.lhs());
        T tipoRhs = analizarExpr(asig.rhs());
        if (!tiposCompatibles(tipoLhs, tipoRhs)) {
            errores.errorSemantico("Tipos incompatibles en asignacion: "
                + tipoLhs + " = " + tipoRhs);
        }
    }

    private T analizarLhs(E lhs) {
        T tipo;
        if (lhs instanceof Id) {
            String nombre = ((Id) lhs).nombre();
            Object info = tabla.buscar(nombre);
            if (info == null) {
                errores.errorSemantico("Variable no declarada: '" + nombre + "'");
                return null;
            }
            if (!(info instanceof InfoVariable)) {
                errores.errorSemantico("'" + nombre + "' es una funcion, no una variable");
                return null;
            }
            tipo = ((InfoVariable) info).tipo();
        } else if (lhs instanceof AccesoArray) {
            AccesoArray acc = (AccesoArray) lhs;
            T tipoBase = analizarLhs(acc.array());
            T tipoIdx  = analizarExpr(acc.indice());
            if (!esInt(tipoIdx)) {
                errores.errorSemantico("El indice de un array debe ser de tipo int");
            }
            if (!(tipoBase instanceof TipoArray)) {
                errores.errorSemantico("No se puede indexar un tipo no-array: " + tipoBase);
                return null;
            }
            tipo = tipoElementoArray((TipoArray) tipoBase);
        } else {
            errores.errorSemantico("LHS invalido en asignacion");
            return null;
        }
        tipos.put(lhs, tipo);
        return tipo;
    }

    private void analizarBloque(Bloque bloque) {
        tabla.abrirAmbito();
        for (Stmt stmt : bloque.instrucciones()) {
            analizarStmt(stmt);
        }
        tabla.cerrarAmbito();
    }

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

    private void analizarWhile(While w) {
        T tipoCond = analizarExpr(w.condicion());
        if (!esBool(tipoCond)) {
            errores.errorSemantico("La condicion del while debe ser bool, pero es " + tipoCond);
        }
        analizarBloque(w.cuerpo());
    }

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

    private void analizarPrint(Print print) {
        analizarExpr(print.expr());
    }

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
    // Expresiones
    // ---------------------------------------------------------------

    public T analizarExpr(E expr) {
        T tipo = calcularTipo(expr);
        tipos.put(expr, tipo);
        return tipo;
    }

    private T calcularTipo(E expr) {
        switch (expr.kind()) {

            case NUM:
                return new TipoBasico("int");

            case BOOL:
                return new TipoBasico("bool");

            case ID: {
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
                T t1 = analizarExpr(expr.opnd1());
                T t2 = analizarExpr(expr.opnd2());
                if (!esInt(t1) || !esInt(t2)) {
                    errores.errorSemantico("Los operandos de operaciones aritmeticas deben ser int");
                }
                return new TipoBasico("int");
            }

            case MENOR: case MAYOR: {
                T t1 = analizarExpr(expr.opnd1());
                T t2 = analizarExpr(expr.opnd2());
                if (!esInt(t1) || !esInt(t2)) {
                    errores.errorSemantico("Los operandos de < y > deben ser int");
                }
                return new TipoBasico("bool");
            }

            case IGUALDAD: {
                T t1 = analizarExpr(expr.opnd1());
                T t2 = analizarExpr(expr.opnd2());
                if (!tiposCompatibles(t1, t2)) {
                    errores.errorSemantico("Los operandos de == deben ser del mismo tipo");
                }
                return new TipoBasico("bool");
            }

            case AND: case OR: {
                T t1 = analizarExpr(expr.opnd1());
                T t2 = analizarExpr(expr.opnd2());
                if (!esBool(t1) || !esBool(t2)) {
                    errores.errorSemantico("Los operandos de and/or deben ser bool");
                }
                return new TipoBasico("bool");
            }

            case MENOS_UNARIO: {
                T t = analizarExpr(expr.opnd1());
                if (!esInt(t)) {
                    errores.errorSemantico("El operando de la negacion unaria debe ser int");
                }
                return new TipoBasico("int");
            }

            case ACCESO_ARRAY: {
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
                LlamadaFuncion lf = (LlamadaFuncion) expr;
                Object info = tabla.buscar(lf.nombre());
                if (info == null || !(info instanceof InfoFuncion)) {
                    errores.errorSemantico("Funcion no declarada: '" + lf.nombre() + "'");
                    return null;
                }
                InfoFuncion inf = (InfoFuncion) info;
                List<Parametro> params = inf.parametros();
                List<E> args = lf.args();
                if (args.size() != params.size()) {
                    errores.errorSemantico("Numero de argumentos incorrecto en llamada a '"
                        + lf.nombre() + "': se esperaban " + params.size()
                        + " pero se pasaron " + args.size());
                }
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
