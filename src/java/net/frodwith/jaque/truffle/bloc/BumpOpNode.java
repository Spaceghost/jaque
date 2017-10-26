package net.frodwith.jaque.truffle.bloc;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

import net.frodwith.jaque.data.Atom;
import net.frodwith.jaque.truffle.nodes.JaqueNode;

public abstract class BumpOpNode extends JaqueNode {
  public abstract Object executeBump(VirtualFrame frame, Object o);

  @Specialization(rewriteOn = ArithmeticException.class)
  protected long bump(long atom) throws ArithmeticException {
    return Atom.increment(atom);
  }
  
  @Specialization
  protected int[] bump(int[] atom) {
    return Atom.increment(atom);
  }

}
