package net.frodwith.jaque.truffle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;

import net.frodwith.jaque.KickLabel;
import net.frodwith.jaque.Location;
import net.frodwith.jaque.data.Atom;
import net.frodwith.jaque.data.Cell;
import net.frodwith.jaque.data.Noun;
import net.frodwith.jaque.truffle.driver.Arm;
import net.frodwith.jaque.truffle.driver.AxisArm;
import net.frodwith.jaque.truffle.nodes.JaqueRootNode;
import net.frodwith.jaque.truffle.nodes.TopRootNode;
import net.frodwith.jaque.truffle.nodes.formula.BailNode;
import net.frodwith.jaque.truffle.nodes.formula.BumpNodeGen;
import net.frodwith.jaque.truffle.nodes.formula.ComposeNode;
import net.frodwith.jaque.truffle.nodes.formula.ConsNodeGen;
import net.frodwith.jaque.truffle.nodes.formula.DeepNodeGen;
import net.frodwith.jaque.truffle.nodes.formula.EscapeNodeGen;
import net.frodwith.jaque.truffle.nodes.formula.FormulaNode;
import net.frodwith.jaque.truffle.nodes.formula.FragmentNode;
import net.frodwith.jaque.truffle.nodes.formula.IdentityNode;
import net.frodwith.jaque.truffle.nodes.formula.IfNode;
import net.frodwith.jaque.truffle.nodes.formula.KickNodeGen;
import net.frodwith.jaque.truffle.nodes.formula.LiteralCellNode;
import net.frodwith.jaque.truffle.nodes.formula.LiteralIntArrayNode;
import net.frodwith.jaque.truffle.nodes.formula.LiteralLongNode;
import net.frodwith.jaque.truffle.nodes.formula.NockNode;
import net.frodwith.jaque.truffle.nodes.formula.PushNode;
import net.frodwith.jaque.truffle.nodes.formula.SameNodeGen;
import net.frodwith.jaque.truffle.nodes.formula.hint.DiscardHintNode;
import net.frodwith.jaque.truffle.nodes.formula.hint.FastHintNode;
import net.frodwith.jaque.truffle.nodes.formula.hint.MemoHintNode;
import net.frodwith.jaque.truffle.nodes.jet.AddNodeGen;
import net.frodwith.jaque.truffle.nodes.jet.BexNodeGen;
import net.frodwith.jaque.truffle.nodes.jet.CanNodeGen;
import net.frodwith.jaque.truffle.nodes.jet.CapNodeGen;
import net.frodwith.jaque.truffle.nodes.jet.CatNodeGen;
import net.frodwith.jaque.truffle.nodes.jet.ConNodeGen;
import net.frodwith.jaque.truffle.nodes.jet.CutNodeGen;
import net.frodwith.jaque.truffle.nodes.jet.DecNodeGen;
import net.frodwith.jaque.truffle.nodes.jet.DisNodeGen;
import net.frodwith.jaque.truffle.nodes.jet.DivNodeGen;
import net.frodwith.jaque.truffle.nodes.jet.DvrNodeGen;
import net.frodwith.jaque.truffle.nodes.jet.EndNodeGen;
import net.frodwith.jaque.truffle.nodes.jet.GteNodeGen;
import net.frodwith.jaque.truffle.nodes.jet.GthNodeGen;
import net.frodwith.jaque.truffle.nodes.jet.LshNodeGen;
import net.frodwith.jaque.truffle.nodes.jet.LteNodeGen;
import net.frodwith.jaque.truffle.nodes.jet.LthNodeGen;
import net.frodwith.jaque.truffle.nodes.jet.MasNodeGen;
import net.frodwith.jaque.truffle.nodes.jet.MetNodeGen;
import net.frodwith.jaque.truffle.nodes.jet.MixNodeGen;
import net.frodwith.jaque.truffle.nodes.jet.ModNodeGen;
import net.frodwith.jaque.truffle.nodes.jet.MugNodeGen;
import net.frodwith.jaque.truffle.nodes.jet.MulNodeGen;
import net.frodwith.jaque.truffle.nodes.jet.PegNodeGen;

public class Context {
  
  public final Map<KickLabel, CallTarget> kicks;
  public final Map<Cell, CallTarget> nocks;
  public final Map<Cell, Location> locations;
  public final Map<String, Arm[]> drivers;
  
  public Context(Arm[] arms) {
    this.kicks = new HashMap<KickLabel, CallTarget>();
    this.nocks = new HashMap<Cell, CallTarget>();
    this.locations = new HashMap<Cell, Location>();
    this.drivers = new HashMap<String, Arm[]>();
    
    Map<String, List<Arm>> temp = new HashMap<String, List<Arm>>();
    if ( null != arms ) {
      for ( Arm a : arms ) {
        List<Arm> push = temp.get(a.label);
        if ( null == push ) {
          push = new LinkedList<Arm>();
          temp.put(a.label, push);
        }
        push.add(a);
      }
    }
    
    for ( Map.Entry<String, List<Arm>> e : temp.entrySet() ) {
      drivers.put(e.getKey(), e.getValue().toArray(new Arm[0]));
    }
  }
  
  /* If there was a node for this, we could profile it, but it's a slow path operation
   * (in general, we cache formulas) so there's not(?) much benefit to making it a node.
   */
  public FormulaNode parseCell(Cell src, boolean tail) {
    Object op  = src.head,
           arg = src.tail;

    if ( TypesGen.isCell(op) ) {
      return ConsNodeGen.create(
          parseCell(TypesGen.asCell(op), false),
          parseCell(TypesGen.asCell(arg), false));
    }
    else {
      switch ( (int) TypesGen.asLong(op) ) {
        case 0: {
          if ( Atom.isZero(arg) ) {
            return new BailNode();
          }
          if ( Atom.equals(1L, arg) ) {
            return new IdentityNode();
          }
          else {
            return new FragmentNode(arg);
          }
        }
        case 1: {
          if ( TypesGen.isCell(arg) ) {
            return new LiteralCellNode(TypesGen.asCell(arg));
          }
          else if ( TypesGen.isLong(arg) ) {
            return new LiteralLongNode(TypesGen.asLong(arg));
          }
          else {
            return new LiteralIntArrayNode(TypesGen.asIntArray(arg));
          }
        }
        case 2: {
          Cell c = TypesGen.asCell(arg),
               h = TypesGen.asCell(c.head),
               t = TypesGen.asCell(c.tail);
          FormulaNode left = parseCell(h, false),
                     right = parseCell(t, false);
          return new NockNode(left, right, this, tail);
        }
        case 3:
          return DeepNodeGen.create(parseCell(TypesGen.asCell(arg), false));
        case 4:
          return BumpNodeGen.create(parseCell(TypesGen.asCell(arg), false));
        case 5: {
          Cell c = TypesGen.asCell(arg),
               h = TypesGen.asCell(c.head),
               t = TypesGen.asCell(c.tail);
          return SameNodeGen.create(
              parseCell(h, false),
              parseCell(t, false));
        }
        case 6: {
          Cell trel = TypesGen.asCell(arg),
               pair = TypesGen.asCell(trel.tail),
               one  = TypesGen.asCell(trel.head),
               two  = TypesGen.asCell(pair.head),
               tre  = TypesGen.asCell(pair.tail);

          return new IfNode(
              parseCell(one, false),
              parseCell(two, tail),
              parseCell(tre, tail));
        }
        case 7: {
          Cell c = TypesGen.asCell(arg),
               h = TypesGen.asCell(c.head),
               t = TypesGen.asCell(c.tail);

          return new ComposeNode(
              parseCell(h, false), 
              parseCell(t, tail));
        }
        case 8: {
          Cell c = TypesGen.asCell(arg),
               h = TypesGen.asCell(c.head),
               t = TypesGen.asCell(c.tail);
          return new PushNode(
              parseCell(h, false), 
              parseCell(t, tail));
        }
        case 9: {
          Cell c = TypesGen.asCell(arg),
               t = TypesGen.asCell(c.tail);
          Object axis = c.head;
          FormulaNode core = parseCell(t, false);

          return KickNodeGen.create(core, this, tail, Atom.cap(axis) == 2, axis);
        }
        case 10: {
          Cell    cell = TypesGen.asCell(arg);
          FormulaNode next = parseCell(TypesGen.asCell(cell.tail), tail);
          Object  head = cell.head;
          if ( Noun.isAtom(head) ) {
            if ( Atom.MEMO.equals(head) ) {
              return new MemoHintNode(next);
            }
            else {
              // What do you do with static hints you don't recognize? Nothing...
              return next;
            }
          }
          else {
            Cell dyn     = TypesGen.asCell(head);
            FormulaNode dynF = parseCell(TypesGen.asCell(dyn.tail), false);
            Object kind  = dyn.head;
            if ( Atom.FAST.equals(kind) ) {
              return new FastHintNode(this, dynF, next);
            }
            else {
              return new DiscardHintNode(dynF, next);
            }
          }
        }
        case 11: {
          Cell c = TypesGen.asCell(arg);
          return EscapeNodeGen.create(
              parseCell(TypesGen.asCell(c.head), false),
              parseCell(TypesGen.asCell(c.tail), false),
              this);
        }
        default: {
          throw new IllegalArgumentException();
        }
      }
    }
  }

  /* Top-level interpeter entry point */
  public Object nock(Object subject, Cell formula) {
    FormulaNode program = parseCell(formula, true);
    JaqueRootNode root  = new JaqueRootNode(program);
    CallTarget target   = Truffle.getRuntime().createCallTarget(root);
    TopRootNode top     = new TopRootNode(target);
    return Truffle.getRuntime().createCallTarget(top).call(subject);
  }

  public static void main(String[] args) {
    /*
    Arm[] drivers = new Arm[] {
      new AxisArm("kmat/math/dec", 2L, DecNodeGen.class),
      new AxisArm("kmat/math/add", 2L, AddNodeGen.class),
      new AxisArm("kmat/math/sub", 2L, SubNodeGen.class),
      new AxisArm("kmat/math/lth", 2L, LthNodeGen.class),
    };
    */
    Arm[] drivers = new Arm[] {
      new AxisArm("main/mood/lib/add", 2L, AddNodeGen.class),
      new AxisArm("main/mood/lib/bex", 2L, BexNodeGen.class),
      new AxisArm("main/mood/lib/can", 2L, CanNodeGen.class),
      new AxisArm("main/mood/lib/cap", 2L, CapNodeGen.class),
      new AxisArm("main/mood/lib/cat", 2L, CatNodeGen.class),
      new AxisArm("main/mood/lib/con", 2L, ConNodeGen.class),
      new AxisArm("main/mood/lib/cut", 2L, CutNodeGen.class),
      new AxisArm("main/mood/lib/dec", 2L, DecNodeGen.class),
      new AxisArm("main/mood/lib/dis", 2L, DisNodeGen.class),
      new AxisArm("main/mood/lib/div", 2L, DivNodeGen.class),
      new AxisArm("main/mood/lib/dvr", 2L, DvrNodeGen.class),
      new AxisArm("main/mood/lib/end", 2L, EndNodeGen.class),
      new AxisArm("main/mood/lib/gte", 2L, GteNodeGen.class),
      new AxisArm("main/mood/lib/gth", 2L, GthNodeGen.class),
      new AxisArm("main/mood/lib/lsh", 2L, LshNodeGen.class),
      new AxisArm("main/mood/lib/lte", 2L, LteNodeGen.class),
      new AxisArm("main/mood/lib/lth", 2L, LthNodeGen.class),
      new AxisArm("main/mood/lib/mas", 2L, MasNodeGen.class),
      new AxisArm("main/mood/lib/met", 2L, MetNodeGen.class),
      new AxisArm("main/mood/lib/mix", 2L, MixNodeGen.class),
      new AxisArm("main/mood/lib/mod", 2L, ModNodeGen.class),
      new AxisArm("main/mood/lib/mug", 2L, MugNodeGen.class),
      new AxisArm("main/mood/lib/mul", 2L, MulNodeGen.class),
      new AxisArm("main/mood/lib/peg", 2L, PegNodeGen.class),
    };
    Context c = new Context(drivers);
    try {
      /*
      byte[] bytes = Files.readAllBytes(Paths.get("/home/pdriver/math-kernel.nock"));
      String fos   = new String(bytes, "UTF-8").trim();
      Cell formula = TypesGen.asCell(Noun.parse(fos));
      Cell kernel  = TypesGen.asCell(c.nock(0L, formula));
      String calls = "[8 [9 22 0 1] 9 2 [0 4] [1 15] 0 11]";
      Cell call    = TypesGen.asCell(Noun.parse(calls));
      System.out.println(c.nock(kernel, call));
      */
      byte[] bytes = Files.readAllBytes(Paths.get("pills/atom-gates.pill"));
      Object src = Atom.fromByteArray(bytes, Atom.LITTLE_ENDIAN);
      Cell formula = TypesGen.asCell(Atom.cue(src));
      System.out.println(c.nock(0L, formula));
    }
    catch (IOException e) {
      e.printStackTrace();
    }
  }
}
