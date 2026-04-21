package ast;

import java.util.List;

public class Programa implements ASTNode {
  private List<I> instrucciones;

  public Programa(List<I> instrucciones) {
    this.instrucciones = instrucciones;
  }

  public List<I> instrucciones() {
    return instrucciones;
  }

  public NodeKind nodeKind() {
    return NodeKind.PROGRAMA;
  }

  public String toString() {
    return "Programa(" + instrucciones + ")";
  }
}
