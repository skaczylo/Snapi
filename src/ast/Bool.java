package ast;

public class Bool extends E {
  private boolean valor;

  public Bool(boolean valor) {
    this.valor = valor;
  }

  public boolean valor() {
    return valor;
  }

  public KindE kind() {
    return KindE.BOOL;
  }

  public String toString() {
    return String.valueOf(valor);
  }
}
