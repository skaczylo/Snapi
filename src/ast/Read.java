package ast;

public class Read extends Stmt {
  private String nombre;

  public Read(String nombre) {
    this.nombre = nombre;
  }

  public String nombre() {
    return nombre;
  }

  public NodeKind nodeKind() {
    return NodeKind.READ;
  }

  public String toString() {
    return "Read(" + nombre + ")";
  }
}
