package generadorcod;

import ast.*;
import java.util.*;

/**
 * Generación de código WebAssembly (.wat) para Snapi.
 * Implementa una máquina de pila con marcos de activación (DL, parámetros y locales).
 */
public class GeneradorCodigo {

    private static final int CELL_BYTES   = 4;   // Tamaño celda i32
    private static final int FRAME_HEADER = 4;   // Tamaño del Dynamic Link (DL)

    // ---------------------------------------------------------------
    // Estado
    // ---------------------------------------------------------------

    private final StringBuilder sb = new StringBuilder();
    private final Map<E, T> tipos;

    // Globales: nombre -> offset
    private final Map<String, VarInfo> globales = new LinkedHashMap<>();
    private int sigOffsetGlobal = 0;

    // Funciones: nombre -> metadatos
    private final Map<String, FuncInfo> funciones = new LinkedHashMap<>();

    // Estado local durante la generación de funciones
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

    // ---------------------------------------------------------------
    // API
    // ---------------------------------------------------------------

    public void imprimir() { System.out.print(sb); }

    public String getCodigo() { return sb.toString(); }

    // ---------------------------------------------------------------
    // Fase 1: Recolección de símbolos globales y firmas de funciones
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
        return CELL_BYTES; // int / bool / void (void no se almacena, pero da igual)
    }

    /** Tamano del marco = cabecera + parametros + maximo solapamiento de locales. */
    private int calcularFrameSize(DecFuncion fn) {
        int base = FRAME_HEADER;
        for (Parametro p : fn.parametros()) {
            base += p.esReferencia() ? CELL_BYTES : tamanoEnBytes(p.tipo());
        }
        return calcMaxLocales(fn.cuerpo(), base, base);
    }

    /** Recorre el cuerpo calculando el offset maximo alcanzado. */
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
    // Pase 2: generacion del modulo .wat
    // ---------------------------------------------------------------

    public void genPrograma(Programa p) {
        pasoRecoleccion(p);

        sb.append("(module\n");

        // Importaciones del runtime (debe proporcionarlas el host JS)
        sb.append("  (import \"runtime\" \"print\"     (func $print     (param i32)))\n");
        sb.append("  (import \"runtime\" \"read\"      (func $read      (result i32)))\n");
        sb.append("  (import \"runtime\" \"printReal\" (func $printReal (param f32)))\n");
        sb.append("  (import \"runtime\" \"readReal\"  (func $readReal  (result f32)))\n\n");

        // Memoria lineal y exportacion (util para depuracion)
        sb.append("  (memory 1)\n");
        sb.append("  (export \"memory\" (memory 0))\n\n");

        // Registros virtuales
        sb.append("  (global $SP (mut i32) (i32.const ").append(sigOffsetGlobal).append("))\n");
        sb.append("  (global $MP (mut i32) (i32.const ").append(sigOffsetGlobal).append("))\n");
        sb.append("  (global $NP (mut i32) (i32.const 65532))\n\n");

        generarReserveStack();
        generarReleaseStack();

        // Funciones definidas por el usuario
        for (I inst : p.instrucciones()) {
            if (inst instanceof DecFuncion) {
                generarFuncion((DecFuncion) inst);
            }
        }

        // Programa principal (start)
        generarMain(p);

        sb.append("  (start $_main)\n");
        sb.append(")\n");
    }

    // ---------------------------------------------------------------
    // Auxiliares de pila (reserveStack / releaseStack)
    // ---------------------------------------------------------------

    private void generarReserveStack() {
        sb.append("  ;; Reserva un marco de tamano $size sobre la pila.\n");
        sb.append("  ;; Guarda el MP actual como DL en *(SP) y actualiza MP, SP.\n");
        sb.append("  (func $reserveStack (param $size i32)\n");
        sb.append("    global.get $SP\n");
        sb.append("    global.get $MP\n");
        sb.append("    i32.store        ;; *(SP) = MP   (DL del nuevo marco)\n");
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
        sb.append("  ;; Libera el marco actual: SP = MP; MP = *(MP)\n");
        sb.append("  (func $releaseStack\n");
        sb.append("    global.get $MP\n");
        sb.append("    global.set $SP   ;; SP = MP\n");
        sb.append("    global.get $SP\n");
        sb.append("    i32.load\n");
        sb.append("    global.set $MP   ;; MP = DL almacenado al principio del marco\n");
        sb.append("  )\n\n");
    }

    // ---------------------------------------------------------------
    // Funcion main (start)
    // ---------------------------------------------------------------

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

    // ---------------------------------------------------------------
    // Funciones definidas por el usuario
    // ---------------------------------------------------------------

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

        // 1. Reservar marco
        sb.append("    i32.const ").append(fi.frameSize).append("\n");
        sb.append("    call $reserveStack\n");

        // 2. Inicializar estado de generacion
        enFuncion = true;
        pilaAmbitos.clear();
        pilaAmbitos.push(new LinkedHashMap<>());
        sigOffsetLocal = FRAME_HEADER;

        // 3. Copiar parametros wasm a memoria (siguiendo la convencion uniforme:
        //    todas las variables se direccionan via memoria).
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
            // Las referencias siempre son punteros (i32). Para parámetros por valor
            // de tipo real, el wasm trae un f32 en el local y debe almacenarse f32.
            if (!p.esReferencia() && esReal(p.tipo())) {
                sb.append("    f32.store\n");
            } else {
                sb.append("    i32.store\n");
            }
        }

        // 4. Cuerpo
        for (Stmt s : fn.cuerpo().instrucciones()) {
            genStmt(s);
        }

        // 5. Salida implicita
        if (esVoid(fn.tipoRetorno())) {
            sb.append("    call $releaseStack\n");
        } else {
            // Una funcion no-void debe terminar siempre con return explicito;
            // si llegamos aqui es codigo muerto. unreachable satisface al
            // validador de wasm respecto al tipo de resultado.
            sb.append("    unreachable\n");
        }

        sb.append("  )\n\n");
        enFuncion = false;
        pilaAmbitos.clear();
    }

    // ---------------------------------------------------------------
    // codeI : instrucciones
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
            // codeD(x); codeE(init); store
            emitDireccionId(dec.nombre());
            genExpr(dec.init());
            sb.append(esReal(dec.tipo()) ? "    f32.store\n" : "    i32.store\n");
        }
    }

    private void genAsig(Asignacion a) {
        // codeD(lhs); codeE(rhs); store
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
        // codeE(cond); if codeI(then) [else codeI(else)] end
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
        // block loop codeE(cond) i32.eqz br_if 1 codeI(body) br 0 end end
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
        // codeD(x); call $read|$readReal; store
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
    // codeE : expresiones (deja el valor en la cima de la pila)
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
                // codeD(designador); load (f32 si es real, i32 en otro caso)
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
                    // 0 - x
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
                // pasar por referencia: empujar la direccion
                genDireccion(arg);
            } else {
                // pasar por valor
                genExpr(arg);
            }
        }
        sb.append("    call $").append(lf.nombre()).append("\n");
    }

    // ---------------------------------------------------------------
    // codeD : direccion de un designador (lvalue)
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
            // Variable en el marco actual
            sb.append("    global.get $MP\n");
            sb.append("    i32.const ").append(info.offset).append("\n");
            sb.append("    i32.add\n");
            if (info.referencia) {
                // El slot contiene la direccion de la variable original
                sb.append("    i32.load\n");
            }
        } else {
            // Variable global (offset absoluto desde 0)
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

    /**
     * codeD(d[e]) = codeD(d) + codeE(e) * stride
     * donde stride es el tamano en bytes de UN elemento del tipo de d.
     * Para arrays multidimensionales esta recursion produce el indice plano
     * acumulando offsets segun las dimensiones internas.
     */
    private void genDireccionAcceso(AccesoArray acc) {
        genDireccion(acc.array());

        T tipoBase = tipos.get(acc.array());
        int stride = strideEnBytes(tipoBase);

        genExpr(acc.indice());
        sb.append("    i32.const ").append(stride).append("\n");
        sb.append("    i32.mul\n");
        sb.append("    i32.add\n");
    }

    /**
     * Stride en bytes: cuanto ocupa un elemento al indexar la primera
     * dimension. Para array[d1][d2]...[dn] of T es d2*..*dn*sizeof(T).
     */
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

    /** ¿La expresión tiene tipo real (segun la anotacion semantica)? */
    private boolean esRealExpr(E e) {
        return esReal(tipos.get(e));
    }
}
