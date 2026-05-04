================================================================================
  SNAPI - Compilador de un lenguaje imperativo simple
  Procesadores de Lenguaje
================================================================================

DESCRIPCION DEL LENGUAJE
--------------------------------------------------------------------------------
Snapi es un lenguaje imperativo de tipado estatico con las siguientes
caracteristicas:

  - Tipos basicos: int, bool, void
  - Arrays multidimensionales: array[n1][n2]...of tipo
  - Funciones con parametros por valor y por referencia (&)
  - Estructuras de control: if/else, while
  - Entrada/salida: read(), print()
  - Comentarios de linea (#) y de bloque (/* */)

Ejemplo de programa Snapi:

    var int : x;
    func int : doble(var int : n) {
        return n + n;
    }
    x = doble(5);
    print(x);


ESTRUCTURA DEL PROYECTO
--------------------------------------------------------------------------------

src/
  alex/
    AnalizadorLexico.flex    Especificacion del analizador lexico (JFlex).
                             Define los tokens: palabras clave, operadores,
                             literales, identificadores y comentarios.
    AnalizadorLexico.java    Codigo Java generado automaticamente por JFlex.
                             NO editar a mano.
    ALexOperations.java      Clase auxiliar que construye UnidadLexica con
                             informacion de fila y columna.
    TokenValue.java          Contenedor del lexema y la posicion de un token.
    UnidadLexica.java        Extiende Symbol (CUP) con el lexema, fila y columna
                             del token. Es la unidad que intercambian el lexico
                             y el parser.

  constructorast/
    ConstructorAST.cup       Especificacion de la gramatica en formato CUP.
                             Define las reglas sintacticas y las acciones
                             semanticas que construyen el AST.
    ConstructorAST.java      Parser LR generado automaticamente por CUP.
                             NO editar a mano.
    ClaseLexica.java         Constantes de los tipos de token, generada por CUP.
                             NO editar a mano.
    Main.java                Punto de entrada del compilador. Encadena las
                             tres fases: analisis lexico + sintactico,
                             analisis semantico y generacion de codigo TAC.

  ast/
    ASTNode.java             Interfaz base de todos los nodos del AST.
    NodeKind.java            Enumeracion con los tipos de nodo posibles.
    I.java                   Clase abstracta para instrucciones (nivel superior).
    Stmt.java                Clase abstracta para sentencias.
    E.java                   Clase abstracta para expresiones.
    T.java                   Clase abstracta para tipos.
    KindE.java               Enumeracion con los tipos de expresion (operadores,
                             literales, identificadores, etc.).
    Programa.java            Nodo raiz: lista de instrucciones de nivel superior.
    Bloque.java              Bloque de sentencias entre llaves { }.
    DecVar.java              Declaracion de variable con tipo e inicializacion
                             opcional.
    DecFuncion.java          Declaracion de funcion con tipo de retorno,
                             nombre, lista de parametros y cuerpo.
    Parametro.java           Parametro formal de una funcion (tipo, nombre,
                             y flag de paso por referencia).
    TipoBasico.java          Tipo primitivo: int, bool o void.
    TipoArray.java           Tipo array con lista de dimensiones y tipo base.
    Asignacion.java          Sentencia de asignacion: lhs = rhs.
    If.java                  Sentencia if con rama else opcional.
    While.java               Sentencia while.
    Read.java                Sentencia read(id): lee un valor del teclado.
    Print.java               Sentencia print(expr): imprime una expresion.
    Return.java              Sentencia return con expresion opcional.
    Num.java                 Literal entero.
    Bool.java                Literal booleano (true / false).
    Id.java                  Referencia a una variable.
    ExpBinaria.java          Expresion binaria: aritmetica, relacional o logica.
    ExpUnaria.java           Expresion unaria: negacion numerica (-expr).
    AccesoArray.java         Acceso a elemento de array: expr[expr].
    LlamadaFuncion.java      Llamada a funcion con lista de argumentos.

  semantico/
    TablaSimbolos.java       Tabla de simbolos con pila de ambitos. Permite
                             abrir/cerrar ambitos y buscar/declarar simbolos
                             de dentro hacia fuera.
    InfoVariable.java        Entrada de la tabla para una variable: tipo,
                             si es parametro y si es por referencia.
    InfoFuncion.java         Entrada de la tabla para una funcion: tipo de
                             retorno y lista de parametros formales.
    AnalizadorSemantico.java Recorre el AST comprobando:
                               - Variables y funciones declaradas antes de usarse
                               - Compatibilidad de tipos en expresiones
                               - Tipos correctos en operadores (int para
                                 aritmetica, bool para logica, etc.)
                               - Numero y tipos de argumentos en llamadas
                               - Tipo de la expresion de retorno
                               - Condiciones de if/while de tipo bool
                             Anota cada nodo expresion con su tipo en un Map
                             que se pasa al generador de codigo.

  generadorcod/
    GeneradorCodigo.java     Recorre el AST (ya validado) y emite codigo de
                             tres direcciones (TAC). Instrucciones generadas:
                               x = y op z      operacion binaria
                               x = -y          negacion unaria
                               x = arr[idx]    lectura de array
                               arr[idx] = x    escritura de array
                               ifFalse x goto L  salto condicional
                               goto L          salto incondicional
                               L:              etiqueta
                               param x         argumento por valor
                               param &x        argumento por referencia
                               x = call f, n   llamada con resultado
                               call f, n       llamada void
                               return x/return retorno
                               read x          lectura de teclado
                               print x         impresion
                               halt            fin de programa
                             Los arrays multidimensionales se resuelven
                             calculando el indice plano con strides.

  errors/
    GestionErrores.java      Gestor de errores. Imprime el mensaje y termina
                             la ejecucion con codigo 1. Metodos:
                               errorLexico(fila, columna, lexema)
                               errorSintactico(unidadLexica)
                               errorSemantico(mensaje)


FASES DEL COMPILADOR
--------------------------------------------------------------------------------

  1. ANALISIS LEXICO   (AnalizadorLexico)
     Lee el fichero fuente caracter a caracter y produce una secuencia de
     tokens (UnidadLexica) que el parser consume.

  2. ANALISIS SINTACTICO  (ConstructorAST)
     Aplica la gramatica LR para verificar la estructura del programa y
     construye el AST mediante las acciones semanticas del fichero .cup.

  3. ANALISIS SEMANTICO   (AnalizadorSemantico)
     Recorre el AST, gestiona la tabla de simbolos y comprueba la
     correccion de tipos. Si detecta un error llama a errorSemantico() y
     detiene la compilacion.

  4. GENERACION DE CODIGO  (GeneradorCodigo)
     Recorre el AST ya anotado y emite instrucciones TAC (codigo de tres
     direcciones), una representacion intermedia legible e independiente
     de la arquitectura destino.


FICHEROS DE PRUEBA
--------------------------------------------------------------------------------

  prueba1.txt   Variables enteras y expresiones aritmeticas con print.
  prueba2.txt   Condicional if/else con expresiones booleanas y relacionales.
  prueba3.txt   Bucle while con contador decreciente.
  prueba4.txt   Array unidimensional, funcion con parametros y llamada.


COMPILACION Y EJECUCION
--------------------------------------------------------------------------------

Requisitos: JDK, JFlex, CUP (java_cup).

  # 1. Generar el lexico
  jflex src/alex/AnalizadorLexico.flex

  # 2. Generar el parser
  cup src/constructorast/ConstructorAST.cup

  # 3. Compilar (ajustar classpath segun la ubicacion de los jars)
  javac -cp .;java_cup.jar -d bin src/**/*.java

  # 4. Ejecutar
  java -cp bin;java_cup.jar constructorast.Main prueba1.txt

El programa imprime el AST, confirma el analisis semantico y muestra
el codigo TAC generado.
