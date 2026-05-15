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

  (func $potencia (param $base f32) (param $exp i32) (result f32)
    i32.const 20
    call $reserveStack
    global.get $MP
    i32.const 4
    i32.add
    local.get $base
    f32.store
    global.get $MP
    i32.const 8
    i32.add
    local.get $exp
    i32.store
    global.get $MP
    i32.const 12
    i32.add
    f32.const 1.0
    f32.store
    global.get $MP
    i32.const 16
    i32.add
    i32.const 0
    i32.store
    block
    loop
    global.get $MP
    i32.const 16
    i32.add
    i32.load
    global.get $MP
    i32.const 8
    i32.add
    i32.load
    i32.lt_s
    i32.eqz
    br_if 1
    global.get $MP
    i32.const 12
    i32.add
    global.get $MP
    i32.const 12
    i32.add
    f32.load
    global.get $MP
    i32.const 4
    i32.add
    f32.load
    f32.mul
    f32.store
    global.get $MP
    i32.const 16
    i32.add
    global.get $MP
    i32.const 16
    i32.add
    i32.load
    i32.const 1
    i32.add
    i32.store
    br 0
    end
    end
    global.get $MP
    i32.const 12
    i32.add
    f32.load
    call $releaseStack
    return
    unreachable
  )

  (func $valor_absoluto (param $x i32) (result f32)
    i32.const 8
    call $reserveStack
    global.get $MP
    i32.const 4
    i32.add
    local.get $x
    i32.store
    global.get $MP
    i32.const 4
    i32.add
    i32.load
    f32.load
    f32.const 0.0
    f32.lt
    if
    global.get $MP
    i32.const 4
    i32.add
    i32.load
    global.get $MP
    i32.const 4
    i32.add
    i32.load
    f32.load
    f32.neg
    f32.store
    end
    global.get $MP
    i32.const 4
    i32.add
    i32.load
    f32.load
    call $releaseStack
    return
    unreachable
  )

  (func $_main
    i32.const 0
    f32.const 2.0
    f32.store
    i32.const 4
    i32.const 4
    i32.store
    i32.const 8
    i32.const 0
    f32.load
    i32.const 4
    i32.load
    call $potencia
    f32.store
    i32.const 8
    f32.load
    call $printReal
    i32.const 12
    f32.const 3.5
    f32.neg
    f32.store
    i32.const 16
    i32.const 12
    call $valor_absoluto
    f32.store
    i32.const 16
    f32.load
    call $printReal
    i32.const 12
    f32.load
    call $printReal
  )

  (start $_main)
)
