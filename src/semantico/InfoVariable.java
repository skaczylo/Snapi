package semantico;

import ast.T;

/**
 * Metadatos de una variable o parametro.
 */
public class InfoVariable {
    private T tipo;
    private boolean esParametro;
    private boolean esReferencia; // True si es por referencia (&)

    public InfoVariable(T tipo, boolean esParametro, boolean esReferencia) {
        this.tipo = tipo;
        this.esParametro = esParametro;
        this.esReferencia = esReferencia;
    }

    public T tipo() { return tipo; }
    public boolean esParametro() { return esParametro; }
    public boolean esReferencia() { return esReferencia; }
}
