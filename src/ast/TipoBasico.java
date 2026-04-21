package ast;

public class TipoBasico extends T {
  private String nombre;

  public TipoBasico(String nombre) {
    this.nombre = nombre;
  }

  public String nombre() {
    return nombre;
  }

  public NodeKind nodeKind() {
    return NodeKind.TIPO_BASICO;
  }

  public String toString() {
    return nombre;
  }
}
