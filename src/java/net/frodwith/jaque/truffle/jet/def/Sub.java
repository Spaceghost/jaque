package net.frodwith.jaque.truffle.jet.def;

import com.oracle.truffle.api.CallTarget;

import net.frodwith.jaque.truffle.Context;
import net.frodwith.jaque.truffle.jet.Definition;
import net.frodwith.jaque.truffle.jet.ImplementationNode;
import net.frodwith.jaque.truffle.jet.PairGateNode;
import net.frodwith.jaque.truffle.jet.ops.SubNodeGen;

public final class Sub extends Definition {
  @Override
  public ImplementationNode createNode(Context context, CallTarget fallback) {
    return new PairGateNode(SubNodeGen.create(context));
  }
}
