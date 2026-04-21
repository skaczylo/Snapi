package ast;

import java.util.List;

public class DecFuncion extends I {
  private T tipoRetorno;
  private String nombre;
  private List<Parametro> parametros;
  private Bloque cuerpo;

  public DecFuncion(T tipoRetorno, String nombre, List<Parametro> parametros, Bloque cuerpo) {
    this.tipoRetorno = tipoRetorno;
    this.nombre = nombre;
    this.parametros = parametros;
    this.cuerpo = cuerpo;
  }

  public T tipoRetorno() {
    return tipoRetorno;
  }

  public String nombre() {
    return nombre;
  }

  public List<Parametro> parametros() {
    return parametros;
  }

  public Bloque cuerpo() {
    return cuerpo;
  }

  public NodeKind nodeKind() {
    return NodeKind.DEC_FUNCION;
  }

  public String toString() {
    return "DecFuncion(" + tipoRetorno + ", " + nombre + ", " + parametros + ", " + cuerpo + ")";
  }
}
