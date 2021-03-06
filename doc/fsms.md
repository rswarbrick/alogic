# Finite State Machines

### FSMs as fundamental building blocks

Finite State Machines are the fundamental abstraction Alogic uses to describe
the sequential behaviour of digital logic. Every Alogic entity describing
sequential logic (anything which requires a flip-flop for its implementation)
is eventually emitted as some Verilog modules implementing FSMs.

FSMs are introduced with the `fsm` keyword, followed by the name of the FSM,
followed by the description of the FSM in curly brackets. The behaviour of an
FSM is described using code with sequential semantics, portions of which
executes on every clock cycle.

### Using functions for encapsulation

FSM code is partitioned into functions. After reset, execution starts in the
`main` function. The description of an FSM that reads a byte from an input port
on every clock cycle, increments its value by 2 and writes the result to an
output port would look as follows:

```
fsm add2 {
  in sync u8 p_in;
  out sync u8 p_out;

  void main() {
    u8 x = p_in.read();
    p_out.wire(x + 8'd2);
    fence;
  }
}
```

Note that functions do not return without an explicit `return` statement. If the
execution reaches the end of the body, control is transferred to the beginning
of the function, and conceptually proceeds in an infinite loop. This behaviour
is distinctly different from common programming languages. The body of the
example FSM above takes 1 cycle to execute, and hence repeats on every clock
cycle.

An FSM that depending on the state of an input port would do 2 different kinds of
processing could follow the pattern:

```
fsm one_or_the_other {
  in bool p_which;
  ...

  void main() {
    if (p_which) {
      do_true();
    } else {
      do_false();
    }
  }

  void do_true() {
    // Do something
    ...
    return;
  }

  void do_false() {
    // Do something else
    ...
    return;
  }
}
```

### Statements

The body of functions is composed of a list of imperative statements.
Statements execute sequentially, according to their execution semantics, which
are analogous to similar statements in common imperative programming languages.
Statements can be classed either as combinatorial statements, or control
statements. For a comprehensive list of statements, see the
[statements](statements.md) section of the documentation.

### Execution model and Control units

The statements in function bodies can be partitioned into control units. On
every clock cycle (assuming no stalls due to synchronized ports), the FSM
executes one control unit. In this section, we will not go into the details of
where the precise control unit boundaries are, but we will mention the `fence`
statement to aid with the examples. For now, let it suffice to say that the
`fence` statement is used to delimit control unit boundaries in straight line
code, so any simple statement between 2 `fence` statements executes in one
clock cycle. For the details of FSM execution, see the section on
[control units](control.md).

### Function call model

Function calls within the FSM are tracked using a small hardware call stack.
This ensures that any function can be called from an arbitrary number of call
sites, and the compiler will take care of resuming execution of the correct code
when a function returns. The required size of the call stack is determined
automatically by the compiler, unless the FSM is recursive, as described below.

### Recursive FSMs and variable allocations

Recursive functions in FSMs are permitted. Describing a directly or
indirectly recursive FSM requires that the user defines a `const` value with
name `CALL_STACK_SIZE` to specify the maximum number of active functions at any
time, including invocations of non-recursive functions:

```
fsm rec {
  // Worst case call stack:
  //   main
  //   foo (i == 0)
  //   foo (i == 1)
  //   foo (i == 2)
  //   foo (i == 3)
  const u32 CALL_STACK_SIZE = 5;

  u8 i;

  void main() {
    i = 0;
    foo();
  }

  void foo() {
    ...
    if (i < 3) {
      i += 1;
      foo();
    }
    ...
    return;
  }
}
```

It the responsibility of the designer to ensure that the actual program logic of
the FSM does not violate the specified maximum call stack size.

It is very important to note that all variables declared in a function body use
statically allocated storage. This means that there is only 1 instance of every
variable name declared in the function, even if a function is invoked multiple
times in a recursive call stack. For example, on return to the `main` function,
the following will have `b == 3`, and not `b == 0` as it would be using stack
allocated variables:

```
fsm static_storage {
  u8 i;
  u8 b;

  void main() {
    i = 0;
    foo();
    // At this point b == 3
  }

  void foo() {
    u8 a = i;
    // The C equivalent of the declaration above would be a combination of
    // a static declaration and an assignment statement:
    //   static uint8_t a;
    //   a = i;
    if (i < 3) {
      i += 1;
      foo();
    }
    b = a;
    return;
  }
}
```

### The `fence` function

The function with name `fence` has special meaning. It may only contain
combinatorial statements, and is executed at the beginning of every execution
step, before any other statement executes. For example:

```
fsm fencefunc {
  u3 s_l2 = 1;
  u8 s;

  void fence () {
    s = 8'd1 << s_l2;
  }

  void main () {
    ...
    s_l2 = 2;
    fence;

    ...
    s_l2 = 3;
    fence;

    ...
    s_l2 = 4;
    fence;
  }
}
```

Is equivalent to:

```
fsm nofencefunc {
  u3 s_l2 = 1;
  u8 s;

  void main () {
    s = 8'd1 << s_l2;
    ...
    s_l2 = 2;
    fence;

    s = 8'd1 << s_l2;
    ...
    s_l2 = 3;
    fence;

    s = 8'd1 << s_l2;
    ...
    s_l2 = 1;
    fence;
  }
}
```

### `verilog` functions

Verbatim Verilog code can be included in the module generated by the compiler
using a function with name `verilog`:

```
fsm v {
  void verilog() {
    always @(posedge clk) {
      $display("tick");
    }
  }

  void main() {
    ...
  }
}
