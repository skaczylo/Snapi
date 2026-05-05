package ast;

import java.util.List;

public class NewInstancia extends E {
  private String nombreClase;
  private List<E> args;

  public NewInstancia(String nombreClase, List<E> args) {
    this.nombreClase = nombreClase;
    this.args = args;
  }

  public String nombreClase() {
    return nombreClase;
  }

  public List<E> args() {
    return args;
  }

  public KindE kind() {
    return KindE.NEW_INSTANCIA;
  }

  public String toString() {
    return "NewInstancia(" + nombreClase + ", " + args + ")";
  }
}
