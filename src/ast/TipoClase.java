package ast;

public class TipoClase extends T {
  private String nombre;

  public TipoClase(String nombre) {
    this.nombre = nombre;
  }

  public String nombre() {
    return nombre;
  }

  public NodeKind nodeKind() {
    return NodeKind.TIPO_CLASE;
  }

  public String toString() {
    return nombre;
  }
}
