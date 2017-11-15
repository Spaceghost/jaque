package net.frodwith.jaque.truffle.jet.def.crypt;

import com.oracle.truffle.api.CallTarget;

import net.frodwith.jaque.truffle.Context;
import net.frodwith.jaque.truffle.jet.AesCbcNode;
import net.frodwith.jaque.truffle.jet.Definition;
import net.frodwith.jaque.truffle.jet.ImplementationNode;
import net.frodwith.jaque.truffle.jet.ops.crypt.AesCbccEnNodeGen;

public final class AesCbccEn extends Definition {
  @Override
  public ImplementationNode createNode(Context context, CallTarget fallback) {
    return new AesCbcNode(AesCbccEnNodeGen.create());
  }
}
