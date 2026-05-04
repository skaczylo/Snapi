package generadorcod;

import ast.*;
import java.util.*;

/**
 * Genera codigo de tres direcciones (TAC) a partir del AST de Snapi.
 *
 * Formato de instrucciones:
 *   x = n                   literal entero/booleano
 *   x = y                   copia
 *   x = y op z              operacion binaria
 *   x = -y                  negacion unaria
 *   x = arr[idx]            lectura de array
 *   arr[idx] = x            escritura de array
 *   ifFalse x goto L        salto condicional
 *   goto L                  salto incondicional
 *   L:                      etiqueta
 *   param x                 paso de argumento por valor
 *   param &x                paso de argumento por referencia
 *   x = call f, n           llamada con resultado
 *   call f, n               llamada sin resultado (void)
 *   return x / return       retorno
 *   read x                  lectura de teclado
 *   print x                 impresion
 *   halt                    fin de programa
 */
public class GeneradorCodigo {

    private final List<String> codigo = new ArrayList<>();
    private int contadorTemp = 0;
    private int contadorEtiqueta = 0;

    /** Tipos anotados por el analizador semantico. */
    private final Map<E, T> tipos;

    /** Parametros de cada funcion (para saber cuales son por referencia). */
    private final Map<String, List<Parametro>> infoFunciones = new HashMap<>();

    public GeneradorCodigo(Map<E, T> tipos) {
        this.tipos = tipos;
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private void emit(String inst) { codigo.add(inst); }

    private String nuevaTemp() { return "_t" + contadorTemp++; }

    private String nuevaEtiqueta() { return "_L" + contadorEtiqueta++; }

    public List<String> getCodigo() { return codigo; }

    public void imprimir() { codigo.forEach(System.out::println); }

    // ---------------------------------------------------------------
    // Programa
    // ---------------------------------------------------------------

    public void genPrograma(Programa p) {
        emit("# Codigo TAC - Snapi");
        emit("goto _main");
        emit("");

        // Primera pasada: funciones
        for (I inst : p.instrucciones()) {
            if (inst instanceof DecFuncion) {
                genDecFuncion((DecFuncion) inst);
                emit("");
            }
        }

        // Segunda pasada: codigo principal
        emit("_main:");
        for (I inst : p.instrucciones()) {
            if (!(inst instanceof DecFuncion)) {
                genStmt((Stmt) inst);
            }
        }
        emit("halt");
    }

    // ---------------------------------------------------------------
    // Funciones
    // ---------------------------------------------------------------

    private void genDecFuncion(DecFuncion dec) {
        infoFunciones.put(dec.nombre(), dec.parametros());
        emit("func " + dec.nombre() + ":");
        for (Stmt stmt : dec.cuerpo().instrucciones()) {
            genStmt(stmt);
        }
        emit("endfunc");
    }

    // ---------------------------------------------------------------
    // Sentencias
    // ---------------------------------------------------------------

    private void genStmt(Stmt stmt) {
        switch (stmt.nodeKind()) {
            case DEC_VAR:    genDecVar((DecVar) stmt);         break;
            case ASIGNACION: genAsignacion((Asignacion) stmt); break;
            case BLOQUE:     genBloque((Bloque) stmt);         break;
            case IF:         genIf((If) stmt);                 break;
            case WHILE:      genWhile((While) stmt);           break;
            case READ:       genRead((Read) stmt);             break;
            case PRINT:      genPrint((Print) stmt);           break;
            case RETURN:     genReturn((Return) stmt);         break;
            default: break;
        }
    }

    private void genDecVar(DecVar dec) {
        if (dec.tieneInit()) {
            String val = genExpr(dec.init());
            emit(dec.nombre() + " = " + val);
        } else {
            T tipo = dec.tipo();
            if (tipo instanceof TipoBasico) {
                String base = ((TipoBasico) tipo).nombre();
                if (base.equals("int"))       emit(dec.nombre() + " = 0");
                else if (base.equals("bool")) emit(dec.nombre() + " = false");
            } else if (tipo instanceof TipoArray) {
                int total = calcularTamano((TipoArray) tipo);
                emit("# array " + dec.nombre() + " size=" + total);
            }
        }
    }

    private void genAsignacion(Asignacion asig) {
        String rhs = genExpr(asig.rhs());
        String lhs = genLhsStr(asig.lhs());
        emit(lhs + " = " + rhs);
    }

    private void genBloque(Bloque bloque) {
        for (Stmt s : bloque.instrucciones()) genStmt(s);
    }

    private void genIf(If ifStmt) {
        String cond   = genExpr(ifStmt.condicion());
        String etFalso = nuevaEtiqueta();
        emit("ifFalse " + cond + " goto " + etFalso);
        genBloque(ifStmt.thenBloque());
        if (ifStmt.tieneElse()) {
            String etFin = nuevaEtiqueta();
            emit("goto " + etFin);
            emit(etFalso + ":");
            genBloque(ifStmt.elseBloque());
            emit(etFin + ":");
        } else {
            emit(etFalso + ":");
        }
    }

    private void genWhile(While w) {
        String etInicio = nuevaEtiqueta();
        String etFin    = nuevaEtiqueta();
        emit(etInicio + ":");
        String cond = genExpr(w.condicion());
        emit("ifFalse " + cond + " goto " + etFin);
        genBloque(w.cuerpo());
        emit("goto " + etInicio);
        emit(etFin + ":");
    }

    private void genRead(Read read) {
        emit("read " + read.nombre());
    }

    private void genPrint(Print print) {
        String val = genExpr(print.expr());
        emit("print " + val);
    }

    private void genReturn(Return ret) {
        if (ret.tieneExpr()) {
            String val = genExpr(ret.expr());
            emit("return " + val);
        } else {
            emit("return");
        }
    }

    // ---------------------------------------------------------------
    // Expresiones -> retorna el nombre de la temporal con el resultado
    // ---------------------------------------------------------------

    public String genExpr(E expr) {
        switch (expr.kind()) {

            case NUM:
                return ((Num) expr).num();

            case BOOL:
                return String.valueOf(((Bool) expr).valor());

            case ID:
                return ((Id) expr).nombre();

            case SUMA: case RESTA: case MUL: case DIV:
            case MENOR: case MAYOR: case IGUALDAD:
            case AND: case OR: {
                String op1  = genExpr(expr.opnd1());
                String op2  = genExpr(expr.opnd2());
                String temp = nuevaTemp();
                emit(temp + " = " + op1 + " " + kindToOp(expr.kind()) + " " + op2);
                return temp;
            }

            case MENOS_UNARIO: {
                String op   = genExpr(expr.opnd1());
                String temp = nuevaTemp();
                emit(temp + " = -" + op);
                return temp;
            }

            case ACCESO_ARRAY: {
                AccesoArray acc = (AccesoArray) expr;
                String[] ref  = resolverAcceso(acc);
                String temp   = nuevaTemp();
                emit(temp + " = " + ref[0] + "[" + ref[1] + "]");
                return temp;
            }

            case LLAMADA_FUNCION: {
                LlamadaFuncion lf     = (LlamadaFuncion) expr;
                List<Parametro> params = infoFunciones.get(lf.nombre());
                for (int i = 0; i < lf.args().size(); i++) {
                    E arg = lf.args().get(i);
                    boolean esPorRef = params != null
                        && i < params.size()
                        && params.get(i).esReferencia();
                    if (esPorRef) {
                        // Pasamos la direccion del lvalue directamente
                        emit("param &" + genLhsStr(arg));
                    } else {
                        emit("param " + genExpr(arg));
                    }
                }
                T tipoRet = tipos.get(expr);
                boolean esVoid = tipoRet instanceof TipoBasico
                    && ((TipoBasico) tipoRet).nombre().equals("void");
                if (esVoid) {
                    emit("call " + lf.nombre() + ", " + lf.args().size());
                    return "_void";
                } else {
                    String temp = nuevaTemp();
                    emit(temp + " = call " + lf.nombre() + ", " + lf.args().size());
                    return temp;
                }
            }
        }
        return "?";
    }

    // ---------------------------------------------------------------
    // Resolucion de acceso a array -> [nombreBase, indiceFlat]
    // Maneja arrays multidimensionales calculando el indice plano.
    // ---------------------------------------------------------------

    private String[] resolverAcceso(AccesoArray acc) {
        T tipoBaseExpr = tipos.get(acc.array()); // tipo de la expresion base
        String idxStr  = genExpr(acc.indice());
        int stride     = calcularStride(tipoBaseExpr);

        String idxEscalado;
        if (stride == 1) {
            idxEscalado = idxStr;
        } else {
            idxEscalado = nuevaTemp();
            emit(idxEscalado + " = " + idxStr + " * " + stride);
        }

        E base = acc.array();
        if (base instanceof Id) {
            return new String[]{ ((Id) base).nombre(), idxEscalado };
        } else if (base instanceof AccesoArray) {
            // Dimension interna: acumular offset
            String[] inner = resolverAcceso((AccesoArray) base);
            String total   = nuevaTemp();
            emit(total + " = " + inner[1] + " + " + idxEscalado);
            return new String[]{ inner[0], total };
        }
        return new String[]{ "?", "?" };
    }

    /** Stride: numero de elementos por cada incremento en la dimension actual. */
    private int calcularStride(T tipo) {
        if (tipo instanceof TipoArray) {
            List<Integer> dims = ((TipoArray) tipo).dimensiones();
            int s = 1;
            for (int i = 1; i < dims.size(); i++) s *= dims.get(i);
            return s;
        }
        return 1;
    }

    /** Tamano total de un TipoArray (producto de todas las dimensiones). */
    private int calcularTamano(TipoArray arr) {
        int s = 1;
        for (int d : arr.dimensiones()) s *= d;
        return s;
    }

    // ---------------------------------------------------------------
    // LHS como string (para asignaciones y param por referencia)
    // ---------------------------------------------------------------

    private String genLhsStr(E lhs) {
        if (lhs instanceof Id) {
            return ((Id) lhs).nombre();
        } else if (lhs instanceof AccesoArray) {
            String[] ref = resolverAcceso((AccesoArray) lhs);
            return ref[0] + "[" + ref[1] + "]";
        }
        return "?";
    }

    // ---------------------------------------------------------------
    // Mapeo de KindE a operador TAC
    // ---------------------------------------------------------------

    private String kindToOp(KindE kind) {
        switch (kind) {
            case SUMA:     return "+";
            case RESTA:    return "-";
            case MUL:      return "*";
            case DIV:      return "/";
            case MENOR:    return "<";
            case MAYOR:    return ">";
            case IGUALDAD: return "==";
            case AND:      return "and";
            case OR:       return "or";
            default:       return "?";
        }
    }
}
