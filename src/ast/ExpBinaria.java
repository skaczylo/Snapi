package ast;

public class ExpBinaria extends E {
  private KindE kind;
  private E opnd1;
  private E opnd2;

  public ExpBinaria(KindE kind, E opnd1, E opnd2) {
    this.kind = kind;
    this.opnd1 = opnd1;
    this.opnd2 = opnd2;
  }

  public KindE kind() {
    return kind;
  }

  public E opnd1() {
    return opnd1;
  }

  public E opnd2() {
    return opnd2;
  }

  public String toString() {
    return kind + "(" + opnd1 + ", " + opnd2 + ")";
  }
}
