(module
  (import "runtime" "print"     (func $print     (param i32)))
  (import "runtime" "read"      (func $read      (result i32)))
  (import "runtime" "printReal" (func $printReal (param f32)))
  (import "runtime" "readReal"  (func $readReal  (result f32)))

  (memory 1)
  (export "memory" (memory 0))

  (global $SP (mut i32) (i32.const 20))
  (global $MP (mut i32) (i32.const 20))
  (global $NP (mut i32) (i32.const 65532))

  ;; Reserva un marco de tamano $size sobre la pila.
  ;; Guarda el MP actual como DL en *(SP) y actualiza MP, SP.
  (func $reserveStack (param $size i32)
    global.get $SP
    global.get $MP
    i32.store        ;; *(SP) = MP   (DL del nuevo marco)
    global.get $SP
    global.set $MP   ;; MP = SP
    global.get $SP
    local.get $size
    i32.add
    global.set $SP   ;; SP = SP + size
    global.get $SP
    global.get $NP
    i32.gt_u
    if unreachable end
  )

  ;; Libera el marco actual: SP = MP; MP = *(MP)
  (func $releaseStack
    global.get $MP
    global.set $SP   ;; SP = MP
    global.get $SP
    i32.load
    global.set $MP   ;; MP = DL almacenado al principio del marco
  )

  (func $_main
    i32.const 0
    i32.const 5
    i32.store
    i32.const 4
    i32.const 0
    i32.const 0
    i32.load
    i32.sub
    i32.store
    i32.const 8
    i32.const 0
    i32.const 0
    i32.load
    i32.sub
    i32.const 10
    i32.add
    i32.store
    i32.const 12
    f32.const 3.14
    f32.store
    i32.const 16
    i32.const 12
    f32.load
    f32.neg
    f32.store
    i32.const 4
    i32.load
    call $print
    i32.const 8
    i32.load
    call $print
    i32.const 16
    f32.load
    call $printReal
  )

  (start $_main)
)
