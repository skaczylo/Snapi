package ast;

public class AccesoArray extends E {
  private E array;
  private E indice;

  public AccesoArray(E array, E indice) {
    this.array = array;
    this.indice = indice;
  }

  public E array() {
    return array;
  }

  public E indice() {
    return indice;
  }

  public KindE kind() {
    return KindE.ACCESO_ARRAY;
  }

  public String toString() {
    return "AccesoArray(" + array + ", " + indice + ")";
  }
}
