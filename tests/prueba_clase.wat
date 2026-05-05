(module
  (import "runtime" "print"     (func $print     (param i32)))
  (import "runtime" "read"      (func $read      (result i32)))
  (import "runtime" "printReal" (func $printReal (param f32)))
  (import "runtime" "readReal"  (func $readReal  (result f32)))

  (memory 1)
  (export "memory" (memory 0))

  (global $SP (mut i32) (i32.const 4))
  (global $MP (mut i32) (i32.const 4))
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

  (func $Point$init (param $this i32) (param $px i32) (param $py i32)
    (local $newptr i32)
    i32.const 16
    call $reserveStack
    global.get $MP
    i32.const 4
    i32.add
    local.get $this
    i32.store
    global.get $MP
    i32.const 8
    i32.add
    local.get $px
    i32.store
    global.get $MP
    i32.const 12
    i32.add
    local.get $py
    i32.store
    global.get $MP
    i32.const 4
    i32.add
    i32.load
    i32.const 0
    i32.add
    global.get $MP
    i32.const 8
    i32.add
    i32.load
    i32.store
    global.get $MP
    i32.const 4
    i32.add
    i32.load
    i32.const 4
    i32.add
    global.get $MP
    i32.const 12
    i32.add
    i32.load
    i32.store
    call $releaseStack
  )

  (func $Point$getX (param $this i32) (result i32)
    (local $newptr i32)
    i32.const 8
    call $reserveStack
    global.get $MP
    i32.const 4
    i32.add
    local.get $this
    i32.store
    global.get $MP
    i32.const 4
    i32.add
    i32.load
    i32.const 0
    i32.add
    i32.load
    call $releaseStack
    return
    unreachable
  )

  (func $Point$getY (param $this i32) (result i32)
    (local $newptr i32)
    i32.const 8
    call $reserveStack
    global.get $MP
    i32.const 4
    i32.add
    local.get $this
    i32.store
    global.get $MP
    i32.const 4
    i32.add
    i32.load
    i32.const 4
    i32.add
    i32.load
    call $releaseStack
    return
    unreachable
  )

  (func $Point$move (param $this i32) (param $dx i32) (param $dy i32)
    (local $newptr i32)
    i32.const 16
    call $reserveStack
    global.get $MP
    i32.const 4
    i32.add
    local.get $this
    i32.store
    global.get $MP
    i32.const 8
    i32.add
    local.get $dx
    i32.store
    global.get $MP
    i32.const 12
    i32.add
    local.get $dy
    i32.store
    global.get $MP
    i32.const 4
    i32.add
    i32.load
    i32.const 0
    i32.add
    global.get $MP
    i32.const 4
    i32.add
    i32.load
    i32.const 0
    i32.add
    i32.load
    global.get $MP
    i32.const 8
    i32.add
    i32.load
    i32.add
    i32.store
    global.get $MP
    i32.const 4
    i32.add
    i32.load
    i32.const 4
    i32.add
    global.get $MP
    i32.const 4
    i32.add
    i32.load
    i32.const 4
    i32.add
    i32.load
    global.get $MP
    i32.const 12
    i32.add
    i32.load
    i32.add
    i32.store
    call $releaseStack
  )

  (func $_main
    (local $newptr i32)
    i32.const 0
    global.get $NP
    i32.const 8
    i32.sub
    global.set $NP
    global.get $NP
    local.set $newptr
    local.get $newptr
    i32.const 3
    i32.const 4
    call $Point$init
    local.get $newptr
    i32.store
    i32.const 0
    i32.load
    call $Point$getX
    call $print
    i32.const 0
    i32.load
    call $Point$getY
    call $print
    i32.const 0
    i32.load
    i32.const 10
    i32.const 20
    call $Point$move
    i32.const 0
    i32.load
    call $Point$getX
    call $print
    i32.const 0
    i32.load
    call $Point$getY
    call $print
    i32.const 0
    i32.load
    i32.const 0
    i32.add
    i32.const 100
    i32.store
    i32.const 0
    i32.load
    call $Point$getX
    call $print
  )

  (start $_main)
)
