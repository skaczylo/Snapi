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
  - `GeneradorCodigo.java`: Genera **código WebAssembly textual (`.wat`)** a partir del AST, siguiendo la teoría del tema (máquina virtual de pila):
    - Funciones recursivas `codeE` (expresiones), `codeD` (direcciones de designadores) y `codeI` (instrucciones).
    - Marcos de activación en memoria lineal con los registros virtuales `$MP` (mark pointer), `$SP` (stack pointer) y `$NP` (new pointer).
    - Auxiliares `$reserveStack` y `$releaseStack` para entrar y salir de cada llamada.
    - **Estructura del marco** (32 bits por celda): `[DL | parámetros | variables locales]`. El DL (enlace dinámico) almacena el `MP` del llamante.
    - **Globales** colocadas al principio de la memoria lineal (offset `0..`).
    - **Arrays multidimensionales** resueltos con cálculo de índice plano y *strides*.
    - **Imports** de `$print` y `$read` desde el módulo `runtime`, que debe proporcionar el host (p. ej. JavaScript) en tiempo de carga.

- **`src/errors/`** (Gestión de Errores)
  - `GestionErrores.java`: Centraliza el reporte de errores por consola y finaliza la compilación si hay fallos.

## Fases del Compilador

1. **Análisis Léxico (`AnalizadorLexico`)**: Lee el fichero fuente carácter a carácter y produce una secuencia de tokens (`UnidadLexica`).
2. **Análisis Sintáctico (`ConstructorAST`)**: Aplica la gramática LR para verificar la estructura del programa y construye el AST.
3. **Análisis Semántico (`AnalizadorSemantico`)**: Recorre el AST, gestiona la tabla de símbolos y comprueba la corrección de tipos. Detiene la compilación ante fallos.
4. **Generación de Código (`GeneradorCodigo`)**: Recorre el AST ya validado y emite código **WebAssembly textual (`.wat`)** para una máquina virtual de pila. El `.wat` resultante puede ensamblarse a binario con `wat2wasm` (del *WebAssembly Binary Toolkit*) y ejecutarse en cualquier navegador moderno o entorno Node.js que provea las funciones `runtime.print` y `runtime.read`.

## Ficheros de Prueba

- **`prueba1.txt`**: Variables enteras y expresiones aritméticas con print.
- **`prueba2.txt`**: Condicional if/else con expresiones booleanas y relacionales.
- **`prueba3.txt`**: Bucle while con contador decreciente.
- **`prueba4.txt`**: Array unidimensional, función con parámetros y llamada.

