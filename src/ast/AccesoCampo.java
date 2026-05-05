package ast;

public class AccesoCampo extends E {
  private E objeto;
  private String campo;

  public AccesoCampo(E objeto, String campo) {
    this.objeto = objeto;
    this.campo = campo;
  }

  public E objeto() {
    return objeto;
  }

  public String campo() {
    return campo;
  }

  public KindE kind() {
    return KindE.ACCESO_CAMPO;
  }

  public String toString() {
    return "AccesoCampo(" + objeto + ", " + campo + ")";
  }
}
