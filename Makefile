# Configuración de variables
JAVAC = javac
JFLEX = jflex-1.9.1/bin/jflex
BIN_DIR = bin
SRC_DIR = src
LIB = cup.jar
CP = $(BIN_DIR):$(LIB)

# Lista de paquetes (carpetas con archivos .java)
PACKAGES = src/Main.java \
           src/alex/*.java \
           src/ast/*.java \
           src/constructorast/*.java \
           src/errors/*.java \
           src/generadorcod/*.java \
           src/semantico/*.java

# Regla principal
all: gen compile

# Regenerar léxico con JFlex
jflex-gen:
	$(JFLEX) -d src/alex src/alex/AnalizadorLexico.flex

# Regenerar parser con CUP (sin position tracking para compatibilidad con UnidadLexica)
cup-gen:
	cd src/constructorast && java -jar ../../cup.jar -parser ConstructorAST -symbols ClaseLexica -nopositions ConstructorAST.cup

# Meta-regla: regenerar ambos
gen: jflex-gen cup-gen

# Crear el directorio bin y compilar todo
compile: | $(BIN_DIR)
	$(JAVAC) -d $(BIN_DIR) -cp $(CP) $(PACKAGES)

$(BIN_DIR):
	mkdir -p $(BIN_DIR)

# Limpiar archivos compilados
clean:
	if exist $(BIN_DIR) rmdir /s /q $(BIN_DIR)

# Limpiar archivos generados (léxico y parser)
cleanGen:
	del /q src\alex\AnalizadorLexico.java src\constructorast\ConstructorAST.java src\constructorast\ClaseLexica.java 2>nul || true

# Directorio de pruebas y resultados
TEST_DIR = tests

# Archivo de entrada por defecto para 'make run'
FILE = $(TEST_DIR)/prueba1.txt

# Ejecutar y redirigir salida a .wat en la misma carpeta que el test
run:
	java -cp $(CP) constructorast.Main $(FILE) > $(basename $(FILE)).wat

# Ejecutar todas las pruebas (las que fallan mostrarán error, es esperado)

.PHONY: all gen jflex-gen cup-gen compile clean cleanGen run
