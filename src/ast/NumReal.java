package ast;

public class NumReal extends E {
  private String v;
  public NumReal(String v) {
   this.v = v;
  }
  public String num() {return v;}
  public KindE kind() {return KindE.NUM_REAL;}
  public String toString() {return v;}
}
