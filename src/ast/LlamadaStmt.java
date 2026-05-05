package ast;

/**
 * Sentencia que envuelve una llamada (a funcion o a metodo) descartando su
 * resultado. Permite usar metodos void como sentencias (p.move(1,2);).
 */
public class LlamadaStmt extends Stmt {
  private E llamada;

  public LlamadaStmt(E llamada) {
    this.llamada = llamada;
  }

  public E llamada() {
    return llamada;
  }

  public NodeKind nodeKind() {
    return NodeKind.LLAMADA_STMT;
  }

  public String toString() {
    return "LlamadaStmt(" + llamada + ")";
  }
}
