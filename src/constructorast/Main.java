package constructorast;

import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import alex.AnalizadorLexico;

public class Main {
   public static void main(String[] args) throws Exception {
     Reader input = new InputStreamReader(new FileInputStream(args[0]));
	 AnalizadorLexico alex = new AnalizadorLexico(input);
	 ConstructorAST constructorast = new ConstructorAST(alex);
	 System.out.println(constructorast.parse().value);
 }
}   
   
