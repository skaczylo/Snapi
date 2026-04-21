package ast;

import java.util.List;

public class TipoArray extends T {
  private List<Integer> dimensiones;
  private T tipoBase;

  public TipoArray(List<Integer> dimensiones, T tipoBase) {
    this.dimensiones = dimensiones;
    this.tipoBase = tipoBase;
  }

  public List<Integer> dimensiones() {
    return dimensiones;
  }

  public T tipoBase() {
    return tipoBase;
  }

  public NodeKind nodeKind() {
    return NodeKind.TIPO_ARRAY;
  }

  public String toString() {
    return "TipoArray(" + dimensiones + ", " + tipoBase + ")";
  }
}
