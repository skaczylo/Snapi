package constructorast;

import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import alex.AnalizadorLexico;
import ast.Programa;
import semantico.AnalizadorSemantico;
import generadorcod.GeneradorCodigo;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Uso: Main <fichero.snapi>");
            System.exit(1);
        }

        Reader input = new InputStreamReader(new FileInputStream(args[0]));
        AnalizadorLexico alex = new AnalizadorLexico(input);
        ConstructorAST constructorast = new ConstructorAST(alex);
        Programa programa = (Programa) constructorast.parse().value;

        System.out.println("=== AST ===");
        System.out.println(programa);
        System.out.println();

        System.out.println("=== ANALISIS SEMANTICO ===");
        AnalizadorSemantico semantico = new AnalizadorSemantico();
        semantico.analizar(programa);
        System.out.println("OK - sin errores semanticos");
        System.out.println();

        System.out.println("=== CODIGO TAC GENERADO ===");
        GeneradorCodigo generador = new GeneradorCodigo(semantico.getTipos());
        generador.genPrograma(programa);
        generador.imprimir();
    }
}
