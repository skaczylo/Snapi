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

  ;; Reserva marco y actualiza MP, SP
  (func $reserveStack (param $size i32)
    global.get $SP
    global.get $MP
    i32.store        ;; *(SP) = MP
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

  ;; Libera marco
  (func $releaseStack
    global.get $MP
    global.set $SP   ;; SP = MP
    global.get $SP
    i32.load
    global.set $MP   ;; MP = *(MP)
  )

  (func $cuadrado (param $n i32) (result i32)
    i32.const 8
    call $reserveStack
    global.get $MP
    i32.const 4
    i32.add
    local.get $n
    i32.store
    global.get $MP
    i32.const 4
    i32.add
    i32.load
    global.get $MP
    i32.const 4
    i32.add
    i32.load
    i32.mul
    call $releaseStack
    return
    unreachable
  )

  (func $area_circulo (param $radio f32) (result f32)
    i32.const 12
    call $reserveStack
    global.get $MP
    i32.const 4
    i32.add
    local.get $radio
    f32.store
    global.get $MP
    i32.const 8
    i32.add
    f32.const 3.1415
    f32.store
    global.get $MP
    i32.const 8
    i32.add
    f32.load
    global.get $MP
    i32.const 4
    i32.add
    f32.load
    f32.mul
    global.get $MP
    i32.const 4
    i32.add
    f32.load
    f32.mul
    call $releaseStack
    return
    unreachable
  )

  (func $_main
    i32.const 0
    i32.const 5
    call $cuadrado
    i32.store
    i32.const 4
    f32.const 2.0
    call $area_circulo
    f32.store
    i32.const 0
    i32.load
    call $print
    i32.const 4
    f32.load
    call $printReal
  )

  (start $_main)
)
