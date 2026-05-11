# SNAPI - Compilador de un lenguaje imperativo simple
**Procesadores de Lenguaje**

## Descripción del Lenguaje

Snapi es un lenguaje imperativo de tipado estático con las siguientes características:

- **Tipos básicos:** `int`, `bool`, `real`, `void`
- **Arrays multidimensionales:** `array[n1][n2]...of tipo`
- **Funciones:** con parámetros por valor y por referencia (`&`)
- **Estructuras de control:** `if`/`else`, `while`
- **Operadores:** aritméticos (`+`, `-`, `*`, `/`), relacionales (`<`, `>`, `==`), lógicos (`and`, `or`) y negación unaria (`-`)
- **Entrada/salida:** `read()`, `print()` para tipos `int`, `bool` y `real`
- **Comentarios:** de línea (`#`) y de bloque (`/* */`)

**Ejemplo de programa Snapi:**

```
func int : factorial(var int : n) {
    var int : res = 1;
    while (n > 1) {
        res = res * n;
        n = n + -1;
    }
    return res;
}

var int : x = factorial(5);
print(x);
```

## Estructura del Proyecto

- **`src/alex/`** — Análisis léxico (JFlex). Reconoce tokens, literales enteros y reales, operadores y palabras reservadas.

- **`src/constructorast/`** — Análisis sintáctico (CUP). Gramática LR que construye el AST. Los ficheros `.java` son generados automáticamente, no editar a mano.

- **`src/ast/`** — Nodos del AST: expresiones (`E`, `KindE`), instrucciones (`Stmt`, `NodeKind`), tipos (`T`, `TipoBasico`, `TipoArray`) y declaraciones.

- **`src/semantico/`** — Análisis semántico. Comprueba tipos, ámbitos y validez de llamadas a funciones. Produce un mapa de anotaciones `E → T` que consume el generador.

- **`src/generadorcod/`** — Generación de código WebAssembly textual (`.wat`). Implementa `codeE`, `codeD` y `codeI` sobre una máquina de pila con marcos de activación (`$MP`, `$SP`, `$NP`). Soporta `i32` para `int`/`bool` y `f32` para `real`.

- **`src/errors/`** — Gestión centralizada de errores léxicos, sintácticos y semánticos.

## Fases del Compilador

1. **Léxico** — Produce tokens a partir del fichero fuente.
2. **Sintáctico** — Verifica la gramática y construye el AST.
3. **Semántico** — Comprueba tipos y ámbitos; anota el AST.
4. **Generación de código** — Emite `.wat` ejecutable con `wat2wasm` en cualquier entorno WebAssembly que provea `runtime.print`, `runtime.read`, `runtime.printReal` y `runtime.readReal`.

## Ficheros de Prueba

| Fichero | Contenido |
|---------|-----------|
| `prueba1.txt` | Variables de los tres tipos básicos y `print` |
| `prueba2.txt` | Operaciones y comparaciones con `real` |
| `prueba3.txt` | Operador de negación unaria (`-x`) sobre `int` y `real` |
| `prueba4.txt` | Condicionales `if`/`else` anidados |
| `prueba5.txt` | Bucle `while` |
| `prueba6.txt` | Array unidimensional |
| `prueba7.txt` | Array multidimensional `[3][3]` |
| `prueba8.txt` | Funciones con paso por valor |
| `prueba9.txt` | Funciones con paso por referencia |
| `prueba10.txt` | Combinación: funciones, `real`, negación unaria y `while` |
