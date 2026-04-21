package alex;

public class ALexOperations {
	
	private AnalizadorLexico alex;

	public ALexOperations(AnalizadorLexico alex) {
		this.alex = alex;
	}

	public UnidadLexica unidad(int clase) {
		return new UnidadLexica(alex.fila(), alex.columna(), clase);
	}

	public UnidadLexica unidad(int clase, String lexema) {
		return new UnidadLexica(alex.fila(), alex.columna(), clase, lexema);
	}
	
}
