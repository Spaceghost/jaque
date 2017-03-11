package net.frodwith.jaque.truffle.nodes.formula;

import com.oracle.truffle.api.frame.VirtualFrame;

import net.frodwith.jaque.data.Cell;

public class LiteralCellNode extends SafeFormula {
  private Cell value;

  public LiteralCellNode(Cell value) {
    this.value = value;
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    return value;
  }

  @Override
  public Object executeSubject(VirtualFrame frame, Object subject) {
    return value;
  }
  
  public Cell executeCell(VirtualFrame frame, Object subject) {
    return value;
  }

}
