package ast;

import java.util.List;

public class LlamadaMetodo extends E {
  private E objeto;
  private String metodo;
  private List<E> args;

  public LlamadaMetodo(E objeto, String metodo, List<E> args) {
    this.objeto = objeto;
    this.metodo = metodo;
    this.args = args;
  }

  public E objeto() {
    return objeto;
  }

  public String metodo() {
    return metodo;
  }

  public List<E> args() {
    return args;
  }

  public KindE kind() {
    return KindE.LLAMADA_METODO;
  }

  public String toString() {
    return "LlamadaMetodo(" + objeto + ", " + metodo + ", " + args + ")";
  }
}
