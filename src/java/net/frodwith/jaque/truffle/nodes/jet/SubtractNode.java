package net.frodwith.jaque.truffle.nodes.jet;

import com.oracle.truffle.api.dsl.Specialization;

import net.frodwith.jaque.Bail;
import net.frodwith.jaque.data.Atom;

public abstract class SubtractNode extends BinaryJetNode {
  @Specialization(rewriteOn = ArithmeticException.class)
  protected long sub(long a, long b) {
    try {
      return Atom.subtract(a, b);
    }
    catch (Bail e) {
      System.err.println("subtract underflow");
      throw e;
    }
  }

  @Specialization
  protected int[] sub(int[] a, int[] b) {
    try {
      return Atom.subtract(a, b);
    }
    catch (Bail e) {
      System.err.println("subtract underflow");
      throw e;
    }
  }
}