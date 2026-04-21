package ast;

import java.util.List;

public class Bloque extends Stmt {
  private List<Stmt> instrucciones;

  public Bloque(List<Stmt> instrucciones) {
    this.instrucciones = instrucciones;
  }

  public List<Stmt> instrucciones() {
    return instrucciones;
  }

  public NodeKind nodeKind() {
    return NodeKind.BLOQUE;
  }

  public String toString() {
    return "Bloque(" + instrucciones + ")";
  }
}
