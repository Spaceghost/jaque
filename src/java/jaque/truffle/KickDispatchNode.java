package jaque.truffle;

import jaque.noun.*;

import jaque.interpreter.Jet;

import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.dsl.Specialization;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.dsl.Cached;

public abstract class KickDispatchNode extends NockNode {
  public abstract Object executeKick(VirtualFrame frame, Cell core, Atom axis);
  
  /* TODO: integrate builtin nodes esp. for arithmetic, e.g. dec/add/sub/mul/div */

  @Specialization(limit  = "1",
                  guards = { "!(jet == null)", 
                             "core.getHead() == cachedBattery",
                             "getContext(frame).fine(core)" })
  protected static Object doJet(VirtualFrame frame, Cell core, Atom axis,
    @Cached("core.getHead()") Object cachedBattery,
    @Cached("getContext(frame).find(core, axis)") Jet jet)
  {
    return getContext(frame).apply(jet, core);
  }

  @Specialization(limit    = "1",
                  replaces = "doJet",
                  guards   = {"core.getHead() == cachedBattery"})
  protected static Object doDirect(VirtualFrame frame, Cell core, Atom axis,
    @Cached("core.getHead()") Object cachedBattery,
    @Cached("create(getContext(frame).getKickTarget(core, axis))") DirectCallNode callNode)
  {
    throw new DirectJumpException(callNode, core);
  }

  //@Specialization(replaces = "doDirect")
  @Specialization(replaces = "doJet")
  protected static Object doIndirect(VirtualFrame frame, Cell core, Atom axis,
    @Cached("create()") IndirectCallNode callNode)
  {
    CallTarget target = getContext(frame).getKickTarget(core, axis);
    throw new IndirectJumpException(callNode, target, core);
  }
}
