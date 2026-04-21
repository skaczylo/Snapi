package ast;

public class Parametro implements ASTNode {
  private T tipo;
  private String nombre;
  private boolean referencia;

  public Parametro(T tipo, String nombre, boolean referencia) {
    this.tipo = tipo;
    this.nombre = nombre;
    this.referencia = referencia;
  }

  public T tipo() {
    return tipo;
  }

  public String nombre() {
    return nombre;
  }

  public boolean esReferencia() {
    return referencia;
  }

  public NodeKind nodeKind() {
    return NodeKind.PARAMETRO;
  }

  public String toString() {
    if (referencia) {
      return "Parametro(" + tipo + ", &" + nombre + ")";
    }
    return "Parametro(" + tipo + ", " + nombre + ")";
  }
}
