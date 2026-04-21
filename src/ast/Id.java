package ast;

public class Id extends E {
  private String nombre;

  public Id(String nombre) {
    this.nombre = nombre;
  }

  public String nombre() {
    return nombre;
  }

  public String id() {
    return nombre;
  }

  public KindE kind() {
    return KindE.ID;
  }

  public String toString() {
    return nombre;
  }
}
