# SNAPI - Compilador de un lenguaje imperativo simple
**Procesadores de Lenguaje**

## Descripción del Lenguaje

Snapi es un lenguaje imperativo de tipado estático con las siguientes características:

- **Tipos básicos:** `int`, `bool`, `void`
- **Arrays multidimensionales:** `array[n1][n2]...of tipo`
- **Funciones:** con parámetros por valor y por referencia (`&`)
- **Estructuras de control:** `if`/`else`, `while`
- **Entrada/salida:** `read()`, `print()`
- **Comentarios:** de línea (`#`) y de bloque (`/* */`)

**Ejemplo de programa Snapi:**

```java
var int : x;
func int : doble(var int : n) {
    return n + n;
}
x = doble(5);
print(x);
```

## Estructura del Proyecto

- **`src/alex/`** (Análisis Léxico)
  - `AnalizadorLexico.flex`: Especificación del analizador léxico (JFlex). Define los tokens (palabras clave, operadores, literales, etc.).
  - `AnalizadorLexico.java`: Código Java generado automáticamente. **NO editar a mano**.
  - `ALexOperations.java`: Clase auxiliar que construye `UnidadLexica` con información de fila y columna.
  - `TokenValue.java`: Contenedor del lexema y la posición de un token.
  - `UnidadLexica.java`: Extiende `Symbol` (CUP) con información de ubicación del token.

- **`src/constructorast/`** (Análisis Sintáctico y AST)
  - `ConstructorAST.cup`: Especificación de la gramática en formato CUP. Define reglas sintácticas y semánticas.
  - `ConstructorAST.java` / `ClaseLexica.java`: Código generado automáticamente por CUP. **NO editar a mano**.
  - `Main.java`: Punto de entrada del compilador. Encadena todas las fases.

- **`src/ast/`** (Árbol de Sintaxis Abstracta)
  - Nodos e interfaces base: `ASTNode.java`, `I.java`, `Stmt.java`, `E.java`, `T.java`.
  - Enumeraciones: `NodeKind.java`, `KindE.java`.
  - Clases representativas: `Programa.java`, `Bloque.java`, `DecVar.java`, `DecFuncion.java`, expresiones unarias/binarias, etc.

- **`src/semantico/`** (Análisis Semántico)
  - `TablaSimbolos.java`: Tabla de símbolos con pila de ámbitos.
  - `InfoVariable.java` / `InfoFuncion.java`: Estructuras de la tabla para guardar información.
  - `AnalizadorSemantico.java`: Recorre el AST comprobando compatibilidad de tipos, ámbitos, parámetros de llamadas y sentencias condicionales.

- **`src/generadorcod/`** (Generación de Código)
  - `GeneradorCodigo.java`: Genera código de tres direcciones (TAC) como representación intermedia a partir del AST. Resuelve arrays multidimensionales con cálculo de zancada (strides).

- **`src/errors/`** (Gestión de Errores)
  - `GestionErrores.java`: Centraliza el reporte de errores por consola y finaliza la compilación si hay fallos.

## Fases del Compilador

1. **Análisis Léxico (`AnalizadorLexico`)**: Lee el fichero fuente carácter a carácter y produce una secuencia de tokens (`UnidadLexica`).
2. **Análisis Sintáctico (`ConstructorAST`)**: Aplica la gramática LR para verificar la estructura del programa y construye el AST.
3. **Análisis Semántico (`AnalizadorSemantico`)**: Recorre el AST, gestiona la tabla de símbolos y comprueba la corrección de tipos. Detiene la compilación ante fallos.
4. **Generación de Código (`GeneradorCodigo`)**: Recorre el AST ya validado y emite instrucciones TAC, independientes de la arquitectura de destino.

## Ficheros de Prueba

- **`prueba1.txt`**: Variables enteras y expresiones aritméticas con print.
- **`prueba2.txt`**: Condicional if/else con expresiones booleanas y relacionales.
- **`prueba3.txt`**: Bucle while con contador decreciente.
- **`prueba4.txt`**: Array unidimensional, función con parámetros y llamada.

## Compilación y Ejecución

**Requisitos:** JDK, JFlex, CUP (`java_cup.jar`).

```bash
# 1. Generar el léxico
jflex src/alex/AnalizadorLexico.flex

# 2. Generar el parser
cup src/constructorast/ConstructorAST.cup

# 3. Compilar (ajustar classpath según la ubicación de los jars)
javac -cp ".;java_cup.jar" -d bin src/**/*.java

# 4. Ejecutar
java -cp "bin;java_cup.jar" constructorast.Main prueba1.txt
```
*Nota: El programa imprimirá el AST, confirmará el análisis semántico y mostrará el código de 3 direcciones (TAC) generado.*
