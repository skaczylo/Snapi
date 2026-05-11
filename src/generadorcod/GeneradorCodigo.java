package generadorcod;

import ast.*;
import java.util.*;

/**
 * Generacion de codigo WebAssembly (.wat).
 */
public class GeneradorCodigo {

    private static final int CELL_BYTES   = 4;   // Celda i32
    private static final int FRAME_HEADER = 4;   // Dynamic Link (DL)

    // ---------------------------------------------------------------
    // Estado
    // ---------------------------------------------------------------

    private final StringBuilder sb = new StringBuilder();
    private final Map<E, T> tipos;

    private final Map<String, VarInfo> globales = new LinkedHashMap<>();
    private int sigOffsetGlobal = 0;

    private final Map<String, FuncInfo> funciones = new LinkedHashMap<>();

    private boolean enFuncion = false;
    private final Deque<Map<String, VarInfo>> pilaAmbitos = new ArrayDeque<>();
    private int sigOffsetLocal;

    public GeneradorCodigo(Map<E, T> tipos) {
        this.tipos = tipos;
    }

    private static class VarInfo {
        int offset;
        int sizeBytes;
        boolean referencia;
        T tipo;
    }

    private static class FuncInfo {
        List<Parametro> parametros;
        int frameSize;
    }

    public void imprimir() { System.out.print(sb); }

    public String getCodigo() { return sb.toString(); }

    // ---------------------------------------------------------------
    // Recoleccion
    // ---------------------------------------------------------------

    private void pasoRecoleccion(Programa p) {
        for (I inst : p.instrucciones()) {
            if (inst instanceof DecFuncion) {
                DecFuncion fn = (DecFuncion) inst;
                FuncInfo fi = new FuncInfo();
                fi.parametros = fn.parametros();
                fi.frameSize  = calcularFrameSize(fn);
                funciones.put(fn.nombre(), fi);
            } else {
                recolectarGlobales((Stmt) inst);
            }
        }
    }

    private void recolectarGlobales(Stmt s) {
        if (s instanceof DecVar) {
            DecVar dec = (DecVar) s;
            VarInfo info = new VarInfo();
            info.offset    = sigOffsetGlobal;
            info.sizeBytes = tamanoEnBytes(dec.tipo());
            info.tipo      = dec.tipo();
            globales.put(dec.nombre(), info);
            sigOffsetGlobal += info.sizeBytes;
        } else if (s instanceof Bloque) {
            for (Stmt i : ((Bloque) s).instrucciones()) recolectarGlobales(i);
        } else if (s instanceof If) {
            If iff = (If) s;
            for (Stmt i : iff.thenBloque().instrucciones()) recolectarGlobales(i);
            if (iff.tieneElse()) {
                for (Stmt i : iff.elseBloque().instrucciones()) recolectarGlobales(i);
            }
        } else if (s instanceof While) {
            for (Stmt i : ((While) s).cuerpo().instrucciones()) recolectarGlobales(i);
        }
    }

    private int tamanoEnBytes(T tipo) {
        if (tipo instanceof TipoArray) {
            TipoArray arr = (TipoArray) tipo;
            int total = 1;
            for (int d : arr.dimensiones()) total *= d;
            return total * CELL_BYTES;
        }
        return CELL_BYTES;
    }

    /** Tamano del marco. */
    private int calcularFrameSize(DecFuncion fn) {
        int base = FRAME_HEADER;
        for (Parametro p : fn.parametros()) {
            base += p.esReferencia() ? CELL_BYTES : tamanoEnBytes(p.tipo());
        }
        return calcMaxLocales(fn.cuerpo(), base, base);
    }

    /** Calcula offset maximo. */
    private int calcMaxLocales(Bloque b, int curr, int max) {
        int local = curr;
        for (Stmt s : b.instrucciones()) {
            if (s instanceof DecVar) {
                local += tamanoEnBytes(((DecVar) s).tipo());
                if (local > max) max = local;
            } else if (s instanceof Bloque) {
                max = calcMaxLocales((Bloque) s, local, max);
            } else if (s instanceof If) {
                If iff = (If) s;
                max = calcMaxLocales(iff.thenBloque(), local, max);
                if (iff.tieneElse()) {
                    max = calcMaxLocales(iff.elseBloque(), local, max);
                }
            } else if (s instanceof While) {
                max = calcMaxLocales(((While) s).cuerpo(), local, max);
            }
        }
        return max;
    }

    // ---------------------------------------------------------------
    //  Generacion
    // ---------------------------------------------------------------

    public void genPrograma(Programa p) {
        pasoRecoleccion(p);

        sb.append("(module\n");

        // Imports
        sb.append("  (import \"runtime\" \"print\"     (func $print     (param i32)))\n");
        sb.append("  (import \"runtime\" \"read\"      (func $read      (result i32)))\n");
        sb.append("  (import \"runtime\" \"printReal\" (func $printReal (param f32)))\n");
        sb.append("  (import \"runtime\" \"readReal\"  (func $readReal  (result f32)))\n\n");

        sb.append("  (memory 1)\n");
        sb.append("  (export \"memory\" (memory 0))\n\n");

        // Registros
        sb.append("  (global $SP (mut i32) (i32.const ").append(sigOffsetGlobal).append("))\n");
        sb.append("  (global $MP (mut i32) (i32.const ").append(sigOffsetGlobal).append("))\n");
        sb.append("  (global $NP (mut i32) (i32.const 65532))\n\n");

        generarReserveStack();
        generarReleaseStack();

        for (I inst : p.instrucciones()) {
            if (inst instanceof DecFuncion) {
                generarFuncion((DecFuncion) inst);
            }
        }

        generarMain(p);

        sb.append("  (start $_main)\n");
        sb.append(")\n");
    }

    // ---------------------------------------------------------------
    // Gestion de pila
    // ---------------------------------------------------------------

    private void generarReserveStack() {
        sb.append("  ;; Reserva marco y actualiza MP, SP\n");
        sb.append("  (func $reserveStack (param $size i32)\n");
        sb.append("    global.get $SP\n");
        sb.append("    global.get $MP\n");
        sb.append("    i32.store        ;; *(SP) = MP\n");
        sb.append("    global.get $SP\n");
        sb.append("    global.set $MP   ;; MP = SP\n");
        sb.append("    global.get $SP\n");
        sb.append("    local.get $size\n");
        sb.append("    i32.add\n");
        sb.append("    global.set $SP   ;; SP = SP + size\n");
        sb.append("    global.get $SP\n");
        sb.append("    global.get $NP\n");
        sb.append("    i32.gt_u\n");
        sb.append("    if unreachable end\n");
        sb.append("  )\n\n");
    }

    private void generarReleaseStack() {
        sb.append("  ;; Libera marco\n");
        sb.append("  (func $releaseStack\n");
        sb.append("    global.get $MP\n");
        sb.append("    global.set $SP   ;; SP = MP\n");
        sb.append("    global.get $SP\n");
        sb.append("    i32.load\n");
        sb.append("    global.set $MP   ;; MP = *(MP)\n");
        sb.append("  )\n\n");
    }

    private void generarMain(Programa p) {
        sb.append("  (func $_main\n");
        enFuncion = false;
        for (I inst : p.instrucciones()) {
            if (!(inst instanceof DecFuncion)) {
                genStmt((Stmt) inst);
            }
        }
        sb.append("  )\n\n");
    }

    private void generarFuncion(DecFuncion fn) {
        FuncInfo fi = funciones.get(fn.nombre());

        sb.append("  (func $").append(fn.nombre());
        for (Parametro p : fn.parametros()) {
            String wasmTy = (!p.esReferencia() && esReal(p.tipo())) ? "f32" : "i32";
            sb.append(" (param $").append(p.nombre()).append(" ").append(wasmTy).append(")");
        }
        if (!esVoid(fn.tipoRetorno())) {
            sb.append(esReal(fn.tipoRetorno()) ? " (result f32)" : " (result i32)");
        }
        sb.append("\n");

        sb.append("    i32.const ").append(fi.frameSize).append("\n");
        sb.append("    call $reserveStack\n");

        enFuncion = true;
        pilaAmbitos.clear();
        pilaAmbitos.push(new LinkedHashMap<>());
        sigOffsetLocal = FRAME_HEADER;

        // Copiar parametros a memoria
        for (Parametro p : fn.parametros()) {
            VarInfo info = new VarInfo();
            info.offset     = sigOffsetLocal;
            info.sizeBytes  = p.esReferencia() ? CELL_BYTES : tamanoEnBytes(p.tipo());
            info.referencia = p.esReferencia();
            info.tipo       = p.tipo();
            pilaAmbitos.peek().put(p.nombre(), info);
            sigOffsetLocal += info.sizeBytes;

            sb.append("    global.get $MP\n");
            sb.append("    i32.const ").append(info.offset).append("\n");
            sb.append("    i32.add\n");
            sb.append("    local.get $").append(p.nombre()).append("\n");
            if (!p.esReferencia() && esReal(p.tipo())) {
                sb.append("    f32.store\n");
            } else {
                sb.append("    i32.store\n");
            }
        }

        for (Stmt s : fn.cuerpo().instrucciones()) {
            genStmt(s);
        }

        if (esVoid(fn.tipoRetorno())) {
            sb.append("    call $releaseStack\n");
        } else {
            sb.append("    unreachable\n");
        }

        sb.append("  )\n\n");
        enFuncion = false;
        pilaAmbitos.clear();
    }

    // ---------------------------------------------------------------
    // codeI
    // ---------------------------------------------------------------

    private void genStmt(Stmt s) {
        switch (s.nodeKind()) {
            case DEC_VAR:    genDecVar((DecVar) s);          break;
            case ASIGNACION: genAsig((Asignacion) s);        break;
            case BLOQUE:     genBloque((Bloque) s);          break;
            case IF:         genIf((If) s);                  break;
            case WHILE:      genWhile((While) s);            break;
            case READ:       genRead((Read) s);              break;
            case PRINT:      genPrint((Print) s);            break;
            case RETURN:     genReturn((Return) s);          break;
            default: break;
        }
    }

    private void genDecVar(DecVar dec) {
        if (enFuncion) {
            VarInfo info = new VarInfo();
            info.offset    = sigOffsetLocal;
            info.sizeBytes = tamanoEnBytes(dec.tipo());
            info.tipo      = dec.tipo();
            pilaAmbitos.peek().put(dec.nombre(), info);
            sigOffsetLocal += info.sizeBytes;
        }
        if (dec.tieneInit()) {
            emitDireccionId(dec.nombre());
            genExpr(dec.init());
            sb.append(esReal(dec.tipo()) ? "    f32.store\n" : "    i32.store\n");
        }
    }

    private void genAsig(Asignacion a) {
        genDireccion(a.lhs());
        genExpr(a.rhs());
        sb.append(esRealExpr(a.lhs()) ? "    f32.store\n" : "    i32.store\n");
    }

    private void genBloque(Bloque b) {
        if (enFuncion) {
            pilaAmbitos.push(new LinkedHashMap<>());
            int offsetGuardado = sigOffsetLocal;
            for (Stmt s : b.instrucciones()) genStmt(s);
            sigOffsetLocal = offsetGuardado;
            pilaAmbitos.pop();
        } else {
            for (Stmt s : b.instrucciones()) genStmt(s);
        }
    }

    private void genIf(If iff) {
        genExpr(iff.condicion());
        sb.append("    if\n");
        genBloque(iff.thenBloque());
        if (iff.tieneElse()) {
            sb.append("    else\n");
            genBloque(iff.elseBloque());
        }
        sb.append("    end\n");
    }

    private void genWhile(While w) {
        sb.append("    block\n");
        sb.append("    loop\n");
        genExpr(w.condicion());
        sb.append("    i32.eqz\n");
        sb.append("    br_if 1\n");
        genBloque(w.cuerpo());
        sb.append("    br 0\n");
        sb.append("    end\n");
        sb.append("    end\n");
    }

    private void genRead(Read r) {
        emitDireccionId(r.nombre());
        VarInfo info = buscarLocal(r.nombre());
        if (info == null) info = globales.get(r.nombre());
        if (esReal(info.tipo)) {
            sb.append("    call $readReal\n");
            sb.append("    f32.store\n");
        } else {
            sb.append("    call $read\n");
            sb.append("    i32.store\n");
        }
    }

    private void genPrint(Print p) {
        genExpr(p.expr());
        sb.append(esRealExpr(p.expr()) ? "    call $printReal\n" : "    call $print\n");
    }

    private void genReturn(Return r) {
        if (r.tieneExpr()) {
            genExpr(r.expr());
        }
        sb.append("    call $releaseStack\n");
        sb.append("    return\n");
    }

    // ---------------------------------------------------------------
    // codeE
    // ---------------------------------------------------------------

    private void genExpr(E e) {
        switch (e.kind()) {
            case NUM:
                sb.append("    i32.const ").append(((Num) e).num()).append("\n");
                break;
            case NUM_REAL:
                sb.append("    f32.const ").append(((NumReal) e).num()).append("\n");
                break;
            case BOOL:
                sb.append("    i32.const ").append(((Bool) e).valor() ? 1 : 0).append("\n");
                break;
            case ID:
            case ACCESO_ARRAY:
                genDireccion(e);
                sb.append(esRealExpr(e) ? "    f32.load\n" : "    i32.load\n");
                break;
            case SUMA:
                genExpr(e.opnd1()); genExpr(e.opnd2());
                sb.append(esRealExpr(e.opnd1()) ? "    f32.add\n" : "    i32.add\n");
                break;
            case RESTA:
                genExpr(e.opnd1()); genExpr(e.opnd2());
                sb.append(esRealExpr(e.opnd1()) ? "    f32.sub\n" : "    i32.sub\n");
                break;
            case MUL:
                genExpr(e.opnd1()); genExpr(e.opnd2());
                sb.append(esRealExpr(e.opnd1()) ? "    f32.mul\n" : "    i32.mul\n");
                break;
            case DIV:
                genExpr(e.opnd1()); genExpr(e.opnd2());
                sb.append(esRealExpr(e.opnd1()) ? "    f32.div\n" : "    i32.div_s\n");
                break;
            case MENOR:
                genExpr(e.opnd1()); genExpr(e.opnd2());
                sb.append(esRealExpr(e.opnd1()) ? "    f32.lt\n" : "    i32.lt_s\n");
                break;
            case MAYOR:
                genExpr(e.opnd1()); genExpr(e.opnd2());
                sb.append(esRealExpr(e.opnd1()) ? "    f32.gt\n" : "    i32.gt_s\n");
                break;
            case IGUALDAD:
                genExpr(e.opnd1()); genExpr(e.opnd2());
                sb.append(esRealExpr(e.opnd1()) ? "    f32.eq\n" : "    i32.eq\n");
                break;
            case AND:
                genExpr(e.opnd1()); genExpr(e.opnd2());
                sb.append("    i32.and\n");
                break;
            case OR:
                genExpr(e.opnd1()); genExpr(e.opnd2());
                sb.append("    i32.or\n");
                break;
            case MENOS_UNARIO:
                if (esRealExpr(e.opnd1())) {
                    genExpr(e.opnd1());
                    sb.append("    f32.neg\n");
                } else {
                    sb.append("    i32.const 0\n");
                    genExpr(e.opnd1());
                    sb.append("    i32.sub\n");
                }
                break;
            case LLAMADA_FUNCION:
                genLlamada((LlamadaFuncion) e);
                break;
            default: break;
        }
    }

    private void genLlamada(LlamadaFuncion lf) {
        FuncInfo fi = funciones.get(lf.nombre());
        List<Parametro> params = fi.parametros;
        for (int i = 0; i < lf.args().size(); i++) {
            E arg = lf.args().get(i);
            if (params.get(i).esReferencia()) {
                genDireccion(arg);
            } else {
                genExpr(arg);
            }
        }
        sb.append("    call $").append(lf.nombre()).append("\n");
    }

    // ---------------------------------------------------------------
    // codeD: direccion
    // ---------------------------------------------------------------

    private void genDireccion(E e) {
        if (e instanceof Id) {
            emitDireccionId(((Id) e).nombre());
        } else if (e instanceof AccesoArray) {
            genDireccionAcceso((AccesoArray) e);
        }
    }

    private void emitDireccionId(String nombre) {
        VarInfo info = buscarLocal(nombre);
        if (info != null) {
            sb.append("    global.get $MP\n");
            sb.append("    i32.const ").append(info.offset).append("\n");
            sb.append("    i32.add\n");
            if (info.referencia) {
                sb.append("    i32.load\n");
            }
        } else {
            VarInfo g = globales.get(nombre);
            sb.append("    i32.const ").append(g.offset).append("\n");
        }
    }

    private VarInfo buscarLocal(String nombre) {
        if (!enFuncion) return null;
        for (Map<String, VarInfo> ambito : pilaAmbitos) {
            VarInfo v = ambito.get(nombre);
            if (v != null) return v;
        }
        return null;
    }

    /** Direccion de acceso a array. */
    private void genDireccionAcceso(AccesoArray acc) {
        genDireccion(acc.array());

        T tipoBase = tipos.get(acc.array());
        int stride = strideEnBytes(tipoBase);

        genExpr(acc.indice());
        sb.append("    i32.const ").append(stride).append("\n");
        sb.append("    i32.mul\n");
        sb.append("    i32.add\n");
    }

    /** Stride en bytes. */
    private int strideEnBytes(T tipoBase) {
        if (tipoBase instanceof TipoArray) {
            TipoArray arr = (TipoArray) tipoBase;
            int s = 1;
            for (int i = 1; i < arr.dimensiones().size(); i++) {
                s *= arr.dimensiones().get(i);
            }
            return s * CELL_BYTES;
        }
        return CELL_BYTES;
    }

    // ---------------------------------------------------------------
    // Utilidades
    // ---------------------------------------------------------------

    private boolean esVoid(T t) {
        return t instanceof TipoBasico && ((TipoBasico) t).nombre().equals("void");
    }

    private boolean esReal(T t) {
        return t instanceof TipoBasico && ((TipoBasico) t).nombre().equals("real");
    }

    private boolean esRealExpr(E e) {
        return esReal(tipos.get(e));
    }
}
