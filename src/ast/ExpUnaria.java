package ast;

public class ExpUnaria extends E {
  private KindE kind;
  private E opnd;

  public ExpUnaria(KindE kind, E opnd) {
    this.kind = kind;
    this.opnd = opnd;
  }

  public KindE kind() {
    return kind;
  }

  public E opnd1() {
    return opnd;
  }

  public String toString() {
    return kind + "(" + opnd + ")";
  }
}
