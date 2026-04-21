package ast;

public class DecVar extends Stmt {
  private T tipo;
  private String nombre;
  private E init;

  public DecVar(T tipo, String nombre, E init) {
    this.tipo = tipo;
    this.nombre = nombre;
    this.init = init;
  }

  public T tipo() {
    return tipo;
  }

  public String nombre() {
    return nombre;
  }

  public E init() {
    return init;
  }

  public boolean tieneInit() {
    return init != null;
  }

  public NodeKind nodeKind() {
    return NodeKind.DEC_VAR;
  }

  public String toString() {
    if (init == null) {
      return "DecVar(" + tipo + ", " + nombre + ")";
    }
    return "DecVar(" + tipo + ", " + nombre + ", " + init + ")";
  }
}
