package net.frodwith.jaque.truffle.jet.def.crypt;

import com.oracle.truffle.api.CallTarget;

import net.frodwith.jaque.truffle.Context;
import net.frodwith.jaque.truffle.jet.ImplementationNode;
import net.frodwith.jaque.truffle.jet.AesEcbNode;
import net.frodwith.jaque.truffle.jet.Definition;
import net.frodwith.jaque.truffle.jet.ops.crypt.AesEcbaDeNodeGen;

public final class AesEcbaDe extends Definition {
  @Override
  public ImplementationNode createNode(Context context, CallTarget fallback) {
    return new AesEcbNode(AesEcbaDeNodeGen.create());
  }
}
