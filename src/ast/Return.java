package ast;

public class Return extends Stmt {
  private E expr;

  public Return(E expr) {
    this.expr = expr;
  }

  public E expr() {
    return expr;
  }

  public boolean tieneExpr() {
    return expr != null;
  }

  public NodeKind nodeKind() {
    return NodeKind.RETURN;
  }

  public String toString() {
    if (expr == null) {
      return "Return()";
    }
    return "Return(" + expr + ")";
  }
}
