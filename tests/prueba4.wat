(module
  (import "runtime" "print"     (func $print     (param i32)))
  (import "runtime" "read"      (func $read      (result i32)))
  (import "runtime" "printReal" (func $printReal (param f32)))
  (import "runtime" "readReal"  (func $readReal  (result f32)))

  (memory 1)
  (export "memory" (memory 0))

  (global $SP (mut i32) (i32.const 8))
  (global $MP (mut i32) (i32.const 8))
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
    i32.const 7
    i32.store
    i32.const 0
    i32.load
    i32.const 10
    i32.gt_s
    if
    i32.const 2
    call $print
    else
    i32.const 0
    i32.load
    i32.const 5
    i32.gt_s
    if
    i32.const 1
    call $print
    else
    i32.const 0
    call $print
    end
    end
    i32.const 4
    i32.const 0
    i32.load
    i32.const 0
    i32.gt_s
    i32.store
    i32.const 4
    i32.load
    if
    i32.const 1
    call $print
    end
  )

  (start $_main)
)
