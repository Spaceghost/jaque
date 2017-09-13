package net.frodwith.jaque.truffle.nodes.jet.ut;

import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;

import net.frodwith.jaque.Bail;
import net.frodwith.jaque.data.Atom;
import net.frodwith.jaque.data.Cell;
import net.frodwith.jaque.data.Noun;
import net.frodwith.jaque.truffle.Context;
import net.frodwith.jaque.truffle.FragmentationException;
import net.frodwith.jaque.truffle.nodes.DispatchNode;
import net.frodwith.jaque.truffle.nodes.DispatchNodeGen;
import net.frodwith.jaque.truffle.nodes.FragmentationNode;
import net.frodwith.jaque.truffle.nodes.FunctionNode;
import net.frodwith.jaque.truffle.nodes.NockDispatchNodeGen;
import net.frodwith.jaque.truffle.nodes.formula.FormulaNode;
import net.frodwith.jaque.truffle.nodes.jet.ImplementationNode;

public abstract class PartialMemoNode extends ImplementationNode {
  public abstract Cell executeMemoKey(Cell core);
  private static final Map<Cell,Object> memo = new HashMap<Cell, Object>();
  protected static final FragmentationNode vanVet = new FragmentationNode(118L);
  protected static final FragmentationNode vanVrf = new FragmentationNode(59L);
  private boolean adopted = false;
  
  protected static long tip(String mote, Object van) {
    try {
      long tip = 144 + Atom.mote(mote);
      if ( vanVet.executeFragment(van).equals(Atom.NO) ) {
        tip += 256;
      }
    return tip;
    }
    catch ( FragmentationException e ) {
      throw new Bail();
    }
  }
  
  @TruffleBoundary
  private Object recall(Cell key) {
    return memo.get(key);
  }
  
  @TruffleBoundary
  private void store(Cell key, Object value) {
    memo.put(key, value);
  }

  @Override
  public Object doJet(VirtualFrame frame, Object core) {
    Cell key = executeMemoKey(Cell.expect(core));
    //String wat = getClass().getSimpleName().substring(0, 4).toLowerCase();
    Object mem = recall(key);
    if ( null == mem ) {
      Object old = FunctionNode.getSubject(frame);
      FunctionNode.setSubject(frame, core);
      FormulaNode fallback = getFallback();
      if ( !adopted ) {
        this.insert(fallback);
      }
      mem = fallback.executeGeneric(frame);
      FunctionNode.setSubject(frame, old);
      store(key, mem);
    }
    return mem;
  }
}
