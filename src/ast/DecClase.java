package ast;

import java.util.List;

public class DecClase extends I {
  private String nombre;
  private List<DecVar> campos;
  private List<DecFuncion> metodos;

  public DecClase(String nombre, List<DecVar> campos, List<DecFuncion> metodos) {
    this.nombre = nombre;
    this.campos = campos;
    this.metodos = metodos;
  }

  public String nombre() {
    return nombre;
  }

  public List<DecVar> campos() {
    return campos;
  }

  public List<DecFuncion> metodos() {
    return metodos;
  }

  public NodeKind nodeKind() {
    return NodeKind.DEC_CLASE;
  }

  public String toString() {
    return "DecClase(" + nombre + ", " + campos + ", " + metodos + ")";
  }
}
