package ast;

public class Asignacion extends Stmt {
  private E lhs;
  private E rhs;

  public Asignacion(E lhs, E rhs) {
    this.lhs = lhs;
    this.rhs = rhs;
  }

  public E lhs() {
    return lhs;
  }

  public E rhs() {
    return rhs;
  }

  public NodeKind nodeKind() {
    return NodeKind.ASIGNACION;
  }

  public String toString() {
    return "Asignacion(" + lhs + ", " + rhs + ")";
  }
}
