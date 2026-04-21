package ast;

import java.util.List;

public class LlamadaFuncion extends E {
  private String nombre;
  private List<E> args;

  public LlamadaFuncion(String nombre, List<E> args) {
    this.nombre = nombre;
    this.args = args;
  }

  public String nombre() {
    return nombre;
  }

  public List<E> args() {
    return args;
  }

  public KindE kind() {
    return KindE.LLAMADA_FUNCION;
  }

  public String toString() {
    return "LlamadaFuncion(" + nombre + ", " + args + ")";
  }
}
