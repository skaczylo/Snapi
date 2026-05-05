package alex;

import errors.GestionErrores;
import constructorast.ClaseLexica;

%%
%cup
%line
%column
%class AnalizadorLexico
%unicode
%public

%state COMMENT

%{
  private ALexOperations ops;
  private GestionErrores errores;
  public String lexema() {return yytext();}
  public int fila() {return yyline+1;}
  public int columna() {return yycolumn+1;}
  public void fijaGestionErrores(GestionErrores errores) {
   this.errores = errores;
  }
%}

%eofval{
  return ops.unidad(ClaseLexica.EOF);
%eofval}

%init{
  ops = new ALexOperations(this);
%init}

separador = [ \t\r\b\n]
comentario = #[^\n]*

var = var
dosPuntos = :

letra = [a-zA-Z]
digito = [0-9]
identificador = ({letra}|_)({letra}|{digito}|_)*

puntoYComa = ;
igual = \=

array = array
corcheteApertura = \[
corcheteCierre = \]
of = of

llaveApertura = \{
llaveCierre = \}

func = func
parentesisApertura = \(
parentesisCierre = \)
coma = ,
ampersand = &
void = void
return = return

int = int
numeroEntero = {digito}+
real = real
numeroReal = {digito}+\.{digito}+
bool = bool
true = true
false = false
operadorSuma = \+
operadorResta = -
operadorMultiplicacion = \*
operadorDivision = \/
operadorMenor = <
operadorMayor = >
operadorIgualdad = ==
and = and
or = or

if = if
else = else
while = while
read = read
print = print

%%
{separador}               {}
{comentario}              {}

"/*"                      { yybegin(COMMENT); }
<COMMENT> "*/"            { yybegin(YYINITIAL); }
<COMMENT> [^\n]           { }
<COMMENT> \n              { }

{var}                     { return ops.unidad(ClaseLexica.VAR); }
{dosPuntos}               { return ops.unidad(ClaseLexica.DOS_PUNTOS); }
{puntoYComa}              { return ops.unidad(ClaseLexica.PUNTO_Y_COMA); }
{igual}                   { return ops.unidad(ClaseLexica.IGUAL); }
{array}                   { return ops.unidad(ClaseLexica.ARRAY); }
{corcheteApertura}        { return ops.unidad(ClaseLexica.CORCHETE_APERTURA); }
{corcheteCierre}          { return ops.unidad(ClaseLexica.CORCHETE_CIERRE); }
{of}                      { return ops.unidad(ClaseLexica.OF); }
{llaveApertura}           { return ops.unidad(ClaseLexica.LLAVE_APERTURA); }
{llaveCierre}             { return ops.unidad(ClaseLexica.LLAVE_CIERRE); }
{func}                    { return ops.unidad(ClaseLexica.FUNC); }
{parentesisApertura}      { return ops.unidad(ClaseLexica.PARENTESIS_APERTURA); }
{parentesisCierre}        { return ops.unidad(ClaseLexica.PARENTESIS_CIERRE); }
{coma}                    { return ops.unidad(ClaseLexica.COMA); }
{ampersand}               { return ops.unidad(ClaseLexica.AMPERSAND); }
{void}                    { return ops.unidad(ClaseLexica.VOID); }
{int}                     { return ops.unidad(ClaseLexica.INT); }
{real}                    { return ops.unidad(ClaseLexica.REAL); }
{bool}                    { return ops.unidad(ClaseLexica.BOOL); }
{true}                    { return ops.unidad(ClaseLexica.TRUE); }
{false}                   { return ops.unidad(ClaseLexica.FALSE); }
{operadorSuma}            { return ops.unidad(ClaseLexica.SUMA); }
{operadorResta}           { return ops.unidad(ClaseLexica.RESTA); }
{operadorMultiplicacion}  { return ops.unidad(ClaseLexica.MULTIPLICACION); }
{operadorDivision}        { return ops.unidad(ClaseLexica.DIVISION); }
{operadorMenor}           { return ops.unidad(ClaseLexica.MENOR); }
{operadorMayor}           { return ops.unidad(ClaseLexica.MAYOR); }
{operadorIgualdad}        { return ops.unidad(ClaseLexica.IGUALDAD); }
{and}                     { return ops.unidad(ClaseLexica.AND); }
{or}                      { return ops.unidad(ClaseLexica.OR); }
{if}                      { return ops.unidad(ClaseLexica.IF); }
{else}                    { return ops.unidad(ClaseLexica.ELSE); }
{while}                   { return ops.unidad(ClaseLexica.WHILE); }
{read}                    { return ops.unidad(ClaseLexica.READ); }
{print}                   { return ops.unidad(ClaseLexica.PRINT); }
{return}                  { return ops.unidad(ClaseLexica.RETURN); }

{numeroReal}              { return ops.unidad(ClaseLexica.NUM_REAL, lexema()); }
{numeroEntero}            { return ops.unidad(ClaseLexica.NUM, lexema()); }
{identificador}           { return ops.unidad(ClaseLexica.ID, lexema()); }

[^]                       { errores.errorLexico(fila(),columna(),lexema()); }  
