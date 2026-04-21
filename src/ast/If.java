package ast;

public class If extends Stmt {
  private E condicion;
  private Bloque thenBloque;
  private Bloque elseBloque;

  public If(E condicion, Bloque thenBloque, Bloque elseBloque) {
    this.condicion = condicion;
    this.thenBloque = thenBloque;
    this.elseBloque = elseBloque;
  }

  public E condicion() {
    return condicion;
  }

  public Bloque thenBloque() {
    return thenBloque;
  }

  public Bloque elseBloque() {
    return elseBloque;
  }

  public boolean tieneElse() {
    return elseBloque != null;
  }

  public NodeKind nodeKind() {
    return NodeKind.IF;
  }

  public String toString() {
    if (elseBloque == null) {
      return "If(" + condicion + ", " + thenBloque + ")";
    }
    return "If(" + condicion + ", " + thenBloque + ", " + elseBloque + ")";
  }
}
