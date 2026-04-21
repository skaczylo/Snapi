package ast;

public class Print extends Stmt {
  private E expr;

  public Print(E expr) {
    this.expr = expr;
  }

  public E expr() {
    return expr;
  }

  public NodeKind nodeKind() {
    return NodeKind.PRINT;
  }

  public String toString() {
    return "Print(" + expr + ")";
  }
}
