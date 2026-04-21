package ast;

public class While extends Stmt {
  private E condicion;
  private Bloque cuerpo;

  public While(E condicion, Bloque cuerpo) {
    this.condicion = condicion;
    this.cuerpo = cuerpo;
  }

  public E condicion() {
    return condicion;
  }

  public Bloque cuerpo() {
    return cuerpo;
  }

  public NodeKind nodeKind() {
    return NodeKind.WHILE;
  }

  public String toString() {
    return "While(" + condicion + ", " + cuerpo + ")";
  }
}
