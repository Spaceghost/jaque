package net.frodwith.jaque.data;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.google.common.hash.Hashing;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

import gnu.math.MPN;
import net.frodwith.jaque.Bail;
import net.frodwith.jaque.Ed25519;
import net.frodwith.jaque.truffle.TypesGen;

/* Atoms are primitive (unboxed) longs unless they don't fit
 * in which case they're represented as int[] (NOT java bigint,
 * because it doesn't give us fine-grained enough access for hoon
 * functions like cut).
 * 
 * Please implement all library functions involving atoms (cut, etc)
 * as static methods accepting and returning Object in this class.
 * 
 * Methods in this class assume that their argument is some type of
 * atom (either long or array of ints in little-endian byte-order).
 * Passing any of these functions other types of objects(ints, etc)
 * has undefined behavior.
 * 
 * Many of these methods were adapted from urbit's vere code. The ones
 * referencing MPN were also developed with reference to Kawa Scheme's
 * IntNum class.
 */

public class Atom {
  
  // next() is destructive - use with care!
  public static class Counter implements Iterator<Object> {
    long direct = 0;
    int[] indirect = null;
    
    public Counter() {
      this(0L);
    }
    
    public Counter(Object initial) {
      if ( TypesGen.isLong(initial) ) {
        this.direct = (long) initial;
      }
      else {
        this.indirect = (int[]) initial;
      }
    }
    
    public Counter(long initial) {
      this.direct = initial;
    }
    
    public Counter(int[] initial) {
      this.indirect = Arrays.copyOf(initial, initial.length);
    }

    @Override
    public boolean hasNext() {
      return true;
    }

    @Override
    public Object next() {
      if ( null != indirect ) {
        return incrementInPlace(indirect);
      }
      else if ( 0 == ++direct ) {
        return indirect = new int[] { 0, 0, 1 };
      }
      else {
        return direct;
      }
    }
  }
  
  // get two equally sized int[]s for mpn functions
  private static class Square {
    int[] x;
    int[] y;
    int   len;

    public Square(Object a, Object b) {
      int[] aw = TypesGen.asImplicitIntArray(a), bw = TypesGen.asImplicitIntArray(b);
      int   as = aw.length, bs = bw.length;
      if (as > bs) {
        len = as;
        x   = aw;
        y   = new int[len];
        System.arraycopy(bw, 0, y, 0, bs);
      }
      else if (as < bs) {
        len = bs;
        x   = new int[len];
        y   = bw;
        System.arraycopy(aw, 0, x, 0, as);
      }
      else {
        len = as;
        x   = aw;
        y   = bw;
      }
    }
  }
  public static final int[] MINIMUM_INDIRECT = new int[] {0, 0, 1};

  public static final boolean BIG_ENDIAN = true;
  public static final boolean LITTLE_ENDIAN = false;
  

  public static final long YES = 0L;
  public static final long NO = 1L;

  /* Don't try to make these an enum somewhere. Or do, see if I care. But I warned you. */
  public static final Object FAST = mote("fast"),
                             MEMO = mote("memo"),
                             SPOT = mote("spot"),
                             MEAN = mote("mean"),
                             HUNK = mote("hunk"),
                             LOSE = mote("lose"),
                             SLOG = mote("slog"),
                             LEAF = mote("leaf"),
                             ROSE = mote("rose"),
                             PALM = mote("palm");

  public static Object add(int[] a, int[] b) {
    Square s   = new Square(a, b);
    int[] dst  = new int[s.len+1];
    dst[s.len] = MPN.add_n(dst, s.x, s.y, s.len);
    return malt(dst);
  }
  public static long add(long a, long b) throws ArithmeticException {
    long c = a + b;
    if ( Long.compareUnsigned(c, a) < 0 ||
         Long.compareUnsigned(c, b) < 0 ) {
      throw new ArithmeticException();
    }
    return c;
  }
  
 public static Object add(Object a, Object b) {
    if ( TypesGen.isLong(a) && TypesGen.isLong(b) ) {
      try {
        return add(TypesGen.asLong(a), TypesGen.asLong(b));
      }
      catch (ArithmeticException e) {
      }
    }
    return add(TypesGen.asImplicitIntArray(a), TypesGen.asImplicitIntArray(b));
  }

  @TruffleBoundary
  private static Object aes_cbc(int mode, int keysize, Object key, Object iv, Object msg) {
    int len = met((byte)3, msg),
        out = (len % 16 == 0)
            ? len
            : len + 16 - (len % 16);
    byte[] ky = reverse(forceBytes(key, keysize)),
           iy = reverse(forceBytes(iv, 16)),
           my = reverse(forceBytes(msg, out));
    
    try {
      Cipher c = Cipher.getInstance("AES/CBC/NoPadding");
      SecretKeySpec k = new SecretKeySpec(ky, "AES");
      c.init(mode, k, new IvParameterSpec(iy));

      return takeBytes(reverse(c.doFinal(my)), out);
    }
    catch (BadPaddingException e) {
      e.printStackTrace();
    } 
    catch (IllegalBlockSizeException e) {
      e.printStackTrace();
    } 
    catch (InvalidKeyException e) {
      e.printStackTrace();
    }
    catch (InvalidAlgorithmParameterException e) {
      e.printStackTrace();
    }
    catch (NoSuchAlgorithmException e) {
      e.printStackTrace();
    }
    catch (NoSuchPaddingException e) {
      e.printStackTrace();
    }
    throw new Bail();   
  }
  
  public static Object aes_cbca_de(Object key, Object iv, Object msg) {
    return aes_cbc(Cipher.DECRYPT_MODE, 16, key, iv, msg);
  }
  
  public static Object aes_cbca_en(Object key, Object iv, Object msg) {
    return aes_cbc(Cipher.ENCRYPT_MODE, 16, key, iv, msg);
  }

  public static Object aes_cbcb_de(Object key, Object iv, Object msg) {
    return aes_cbc(Cipher.DECRYPT_MODE, 24, key, iv, msg);
  }
  
  public static Object aes_cbcb_en(Object key, Object iv, Object msg) {
    return aes_cbc(Cipher.ENCRYPT_MODE, 24, key, iv, msg);
  }

  public static Object aes_cbcc_de(Object key, Object iv, Object msg) {
    return aes_cbc(Cipher.DECRYPT_MODE, 32, key, iv, msg);
  }

  public static Object aes_cbcc_en(Object key, Object iv, Object msg) {
    return aes_cbc(Cipher.ENCRYPT_MODE, 32, key, iv, msg);
  }

  @TruffleBoundary
  private static Object aes_ecb(int mode, int keysize, Object key, Object block) {
    byte[] ky = reverse(forceBytes(key, keysize)),
           by = reverse(forceBytes(block, 16));
    
    try {
      Cipher c = Cipher.getInstance("AES/ECB/NoPadding");
      SecretKeySpec k = new SecretKeySpec(ky, "AES");
      c.init(mode, k);

      return takeBytes(reverse(c.doFinal(by)), 16);
    }
    catch (BadPaddingException e) {
      e.printStackTrace();
    } 
    catch (IllegalBlockSizeException e) {
      e.printStackTrace();
    } 
    catch (InvalidKeyException e) {
      e.printStackTrace();
    }
    catch (NoSuchAlgorithmException e) {
      e.printStackTrace();
    }
    catch (NoSuchPaddingException e) {
      e.printStackTrace();
    }
    throw new Bail();   
  }

  public static Object aes_ecba_de(Object key, Object block) {
    return aes_ecb(Cipher.DECRYPT_MODE, 16, key, block);
  }

  public static Object aes_ecba_en(Object key, Object block) {
    return aes_ecb(Cipher.ENCRYPT_MODE, 16, key, block);
  }

  public static Object aes_ecbb_de(Object key, Object block) {
    return aes_ecb(Cipher.DECRYPT_MODE, 24, key, block);
  }

  public static Object aes_ecbb_en(Object key, Object block) {
    return aes_ecb(Cipher.ENCRYPT_MODE, 24, key, block);
  }
  
  public static Object aes_ecbc_de(Object key, Object block) {
    return aes_ecb(Cipher.DECRYPT_MODE, 32, key, block);
  }
  
  public static Object aes_ecbc_en(Object key, Object block) {
    return aes_ecb(Cipher.ENCRYPT_MODE, 32, key, block);
  }

  public static Object aescDe(Object key, Object txt) {
    return doAesc(Cipher.DECRYPT_MODE, key, txt);
  }

  public static Object aescEn(Object key, Object txt) {
    return doAesc(Cipher.ENCRYPT_MODE, key, txt);
  }

  public static Object bex(long a) {
    if (a < 64) {
      return 1L << a;
    }
    else {
      int whole = (int) (a >> 5),
          parts = (int) a & 31;

      int[] words = new int[whole+1];
      words[whole] = 1 << parts;
      return words;
    }
  }

  public static Object can(byte a, Iterable<Object> b) {
    int tot = 0;

    try {
      for ( Object i : b ) {
        Cell c = TypesGen.expectCell(i);
        long pil = TypesGen.expectLong(c.head);
        int pi = (int) pil;
        
        if (pi != pil) {
          throw new Bail();
        }

        Object qi = c.tail;
        if ( !Noun.isAtom(qi) ) {
          throw new Bail();
        }
        tot = Math.addExact(tot, pi);
      }
    }
    catch (ArithmeticException e) {
      throw new Bail();
    }
    catch (UnexpectedResultException e) {
      throw new Bail();
    }

    if ( 0 == tot ) {
      return 0L;
    }

    int[] sal = slaq(a, tot);
    int pos = 0;
    
    for ( Object i : b ) {
      Cell c = TypesGen.asCell(i);
      int pi = (int) TypesGen.asLong(c.head);
      chop(a, 0, pi, pos, sal, c.tail);
      pos += pi;
    }

    return malt(sal);
  }

  public static int cap(Object atom) {
    int b = met(atom);
    if ( b < 2 ) {
      throw new Bail();
    }
    return getNthBit(atom, b - 2) ? 3 : 2;
  }

  public static Object cat(byte a, Object b, Object c) {
    int lew = met(a, b),
        ler = met(a, c),
        all = lew + ler;
    
    if ( 0 == all ) {
      return 0L;
    }
    else {
      int[] sal = slaq(a, all);

      chop(a, 0, lew, 0, sal, b);
      chop(a, 0, ler, lew, sal, c);

      return malt(sal);
    }
  }
  
  public static void chop(byte met, int fum, int wid, int tou, int[] dst, Object src) {
    int[] buf = TypesGen.asImplicitIntArray(src);
    int   len = buf.length, i;

    if (met < 5) {
      int san = 1 << met,
      mek = ((1 << san) - 1),
      baf = fum << met,
      bat = tou << met;

      for (i = 0; i < wid; ++i) {
        int waf = baf >>> 5,
            raf = baf & 31,
            wat = bat >>> 5,
            rat = bat & 31,
            hop;

        hop = (waf >= len) ? 0 : buf[waf];
        hop = (hop >>> raf) & mek;
        dst[wat] ^= hop << rat;
        baf += san;
        bat += san;
      }
    }
    else {
      int hut = met - 5,
          san = 1 << hut,
          j;

      for (i = 0; i < wid; ++i) {
        int wuf = (fum + i) << hut,
            wut = (tou + i) << hut;

        for (j = 0; j < san; ++j) {
          dst[wut + j] ^= ((wuf + j) >= len)
                       ? 0
                       : buf[wuf + j];
        }
      }
    }
  }
  
  public static int compare(int[] a, int[] b) {
    return MPN.cmp(a, a.length, b, b.length);
  }
  
  public static int compare(long a, long b) {
    return Long.compareUnsigned(a, b);
  }
  
  // -1, 0, 1 for less than, equal, or greater than respectively
  public static int compare(Object a, Object b) {
    if ( TypesGen.isLong(a) && TypesGen.isLong(b) ) {
      return compare(TypesGen.asLong(a), TypesGen.asLong(b));
    } 
    return compare(TypesGen.asImplicitIntArray(a), TypesGen.asImplicitIntArray(b));
  }
  
  public static long con(long a, long b) {
    return a | b;
  }

  public static Object con(Object a, Object b) {
    byte w   = 5;
    int  lna = met(w, a);
    int  lnb = met(w, b);

    if ( (0 == lna) && (0 == lnb) ) {
      return 0L;
    }
    else {
      int i, len = Math.max(lna, lnb);
      int[] sal  = new int[len];
      int[] bow  = TypesGen.asImplicitIntArray(b);

      chop(w, 0, lna, 0, sal, a);

      for ( i = 0; i < lnb; i++ ) {
        sal[i] |= bow[i];
      }

      return malt(sal);
    } 
  }
  
  @TruffleBoundary
  public static String cordToString(Object atom) {
    try {
      return new String(toByteArray(atom), "UTF-8");
    }
    catch (UnsupportedEncodingException e) {
      return null;
    }
  }
  
  @TruffleBoundary
  private static Cell cue(Map<Object,Object> m, Object a, Object b) throws UnexpectedResultException {
    Object p, q;

    if ( isZero(cut((byte) 0, b, 1L, a)) ) {
      Object x = increment(b);
      Cell   c = rub(x, a);

      p = increment(c.head);
      q = c.tail;
      m.put(Noun.key(b), q);
    }
    else {
      Object c = add(2L, b),
             l = increment(b);
      
      if ( isZero(cut((byte) 0, l, 1L, a)) ) {
        Cell u, v;
        Object w, x, y;
        
        u = cue(m, a, c);
        x = add(u.head, c);
        v = cue(m, a, x);
        w = new Cell(
            TypesGen.expectCell(u.tail).head,
            TypesGen.expectCell(v.tail).head);
        y = add(u.head, v.head);
        p = add(2L, y);
        q = w;
        m.put(Noun.key(b), q);
      }
      else {
        Cell d = rub(c, a);
        Object x = m.get(Noun.key(d.tail));
        
        if ( null == x ) {
          throw new Bail();
        }

        p = add(2L, d.head);
        q = x;
      }
    }
    return new Cell(p, new Cell(q, 0L));
  }
  
  @TruffleBoundary
  public static Object cue(Object a) {
    try {
      Cell x = cue(new HashMap<Object,Object>(), a, 0L);
      return TypesGen.expectCell(x.tail).head;
    }
    catch ( UnexpectedResultException e ) {
      throw new Bail();
    }
  }
  
  public static Object cut(byte a, Object b, Object c, Object d) {
    int ci, bi = intOrBail(b);
    try {
      ci = intOrBail(c);
    } 
    catch (Bail e) {
      ci = 0x7fffffff;
    }
    int len = met(a, d);

    if ( (0 == ci) || (bi >= len) ) {
      return 0L;
    }

    if ( (bi + ci) > len ) {
      ci = len - bi;
    }

    if ( (bi == 0) && (ci == len) ) {
      return d;
    }
    else {
      int[] sal = slaq(a, ci);
      chop(a,  bi, ci, 0, sal, d);
      return malt(sal);
    }
  }

  public static Object dec(int[] atom) {
    int[] result;
    if ( atom[0] == 0 ) {
      result = new int[atom.length - 1];
      Arrays.fill(result, 0xFFFFFFFF);
    }
    else {
      result = Arrays.copyOf(atom, atom.length);
      result[0] -= 1;
    }
    return malt(result);
  }
  
  public static long dec(long atom) {
    if ( atom == 0 ) {
      throw new Bail();
    }
    else {
      return atom - 1;
    }
  }

  public static Object dec(Object atom) {
    if ( TypesGen.isLong(atom) ) {
      return dec(TypesGen.asLong(atom));
    }
    else {
      return dec(TypesGen.asIntArray(atom));
    }
  }

  public static long dis(long a, long b) {
    return a & b;
  } 
  
  public static Object dis(Object a, Object b) {
    byte w   = 5;
    int  lna = met(w, a);
    int  lnb = met(w, b);

    if ( (0 == lna) && (0 == lnb) ) {
      return 0L;
    }
    else {
      int i, len = Math.max(lna, lnb);
      int[] sal  = new int[len];
      int[] bow  = TypesGen.asImplicitIntArray(b);

      chop(w, 0, lna, 0, sal, a);

      for ( i = 0; i < len; i++ ) {
        sal[i] &= (i >= lnb) ? 0 : bow[i];
      }

      return malt(sal);
    } 
  }
  
  public static Object div(int[] x, int[] y) {
    int cmp = compare(x,y);
    if ( cmp < 0 ) {
      return 0L;
    }
    else if ( 0 == cmp ) {
      return 1L;
    }
    else if ( 1 == y.length ) {
      int[] q = new int[x.length];
      MPN.divmod_1(q, x, x.length, y[0]);
      return malt(q);
    }
    else {
      return divmod(x,y).head;
    }
  }
  
  public static long div(long a, long b) {
    return Long.divideUnsigned(a, b);
  }

  public static Object div(Object a, Object b) {
    if ( TypesGen.isLong(a) && TypesGen.isLong(b) ) {
      return div(TypesGen.asLong(a), TypesGen.asLong(b));
    }
    return div(TypesGen.asImplicitIntArray(a), TypesGen.asImplicitIntArray(b));
  }
  
  /* This code is substantially adapted from Kawa's IntNum.java -- see the note at
   * the top of gnu.math.MPN */
  private static Cell divmod(int[] x, int[] y) {
    int xlen = x.length,
        ylen = y.length,
        rlen, qlen;
    int[] xwords = Arrays.copyOf(x, xlen+2),
          ywords = Arrays.copyOf(y, ylen);

    int nshift = MPN.count_leading_zeros(ywords[ylen-1]);
    if (nshift != 0) {
      MPN.lshift(ywords, 0, ywords, ylen, nshift);
      int x_high = MPN.lshift(xwords, 0, xwords, xlen, nshift);
      xwords[xlen++] = x_high;
    }

    if (xlen == ylen) {
      xwords[xlen++] = 0;
    }

    MPN.divide(xwords, xlen, ywords, ylen);
    rlen = ylen;
    MPN.rshift0(ywords, xwords, 0, rlen, nshift);
    qlen = xlen + 1 - ylen;
    xwords = Arrays.copyOfRange(xwords, ylen, ylen+qlen);
    while ( rlen > 1 && 0 == ywords[rlen - 1] ) {
      --rlen; 
    }
    if ( ywords[rlen-1] < 0 ) {
      ywords[rlen++] = 0;
    }
    
    return new Cell(malt(xwords), malt(ywords));
  }
  
  @TruffleBoundary
  private static Object doAesc(int mode, Object a, Object b) {
    byte[] ay = forceBytes(a, 32),
           by = forceBytes(b, 16);
    
    try {
      Cipher c = Cipher.getInstance("AES/ECB/NoPadding");
      SecretKeySpec k = new SecretKeySpec(ay, "AES");
      c.init(mode, k);

      return takeBytes(c.doFinal(by), 16);
    }
    catch (BadPaddingException e) {
      e.printStackTrace();
    }
    catch (IllegalBlockSizeException e) {
      e.printStackTrace();
    }
    catch (InvalidKeyException e) {
      e.printStackTrace();
    } 
    catch (NoSuchAlgorithmException e) {
      e.printStackTrace();
    }
    catch (NoSuchPaddingException e) {
      e.printStackTrace();
    }
    throw new Bail();   
  }
  
  public static Cell dvr(int[] x, int[] y) {
    int cmp = compare(x,y);
    if ( cmp < 0 ) {
      return new Cell(0L, y);
    }
    else if ( 0 == cmp ) {
      return new Cell(1L, 0L);
    }
    else if ( 1 == y.length ) {
      int[] q = new int[x.length];
      int rem = MPN.divmod_1(q, x, x.length, y[0]);
      return new Cell(malt(q), (long) rem);
    }
    else {
      return divmod(x,y);
    }   
  }

  public static Cell dvr(long a, long b) {
    return new Cell(a / b, a % b);
  }
  
  public static Cell dvr(Object a, Object b) {
    if ( TypesGen.isLong(a) && TypesGen.isLong(b) ) {
      return dvr(TypesGen.asLong(a), TypesGen.asLong(b));
    }
    return dvr(TypesGen.asImplicitIntArray(a), TypesGen.asImplicitIntArray(b));
  }

  @TruffleBoundary
  public static Object edPuck(Object a) {
    byte[] pub = new byte[32],
           sec = new byte[64],
           sed = forceBytes(a, 32);
    
    Ed25519.ed.ed25519_create_keypair(pub, sec, sed);
    return takeBytes(pub, 32);
  }
  
  @TruffleBoundary
  public static Object edShar(Object pub, Object sek) {
    byte[] puy = forceBytes(pub, 32),
           sey = forceBytes(sek, 32),
           self = new byte[32],
           exp  = new byte[64],
           shr =  new byte[32];
    Ed25519.ed.ed25519_create_keypair(self, exp, sey);
    Ed25519.ed.ed25519_key_exchange(shr, puy, exp);
    return takeBytes(shr, 32);
  }
  
  @TruffleBoundary
  public static Object edSign(Object a, Object b) {
    byte[] sig = new byte[64],
           sed = forceBytes(b, 32),
           pub = new byte[64],
           sec = new byte[64],
           mes = toByteArray(a);
    Ed25519.ed.ed25519_create_keypair(pub, sec, sed);
    Ed25519.ed.ed25519_sign(sig, mes, mes.length, pub, sec);
    return takeBytes(sig, 64);
  }

  @TruffleBoundary
  public static Object edVeri(Object s, Object m, Object pk) {
    byte[] sig = forceBytes(s, 64),
           pub = forceBytes(pk, 32),
           mes = toByteArray(m);
    
    return ( 1 == Ed25519.ed.ed25519_verify(sig, mes, mes.length, pub) )
        ? YES
        : NO;
  }
  
  public static Object end(byte a, Object b, Object c) {
    int bi;

    try {
      bi = intOrBail(b);
    }
    catch (Bail e) {
      return c;
    }
    if ( 0 == bi ) {
      return 0L;
    }

    int len = met(a, c);
    if ( bi >= len ) {
      return c;
    }

    int[] sal = slaq(a, bi);
    chop(a, 0, bi, 0, sal, c);
    return malt(sal);
  }
  
  public static boolean equals(int[] a, int[] b) {
    return Arrays.equals(a,  b);
  }
  
  public static boolean equals(long a, long b) {
    return a == b;
  }
  
  public static boolean equals(Object a, Object b) {
    return ( TypesGen.isLong(a) 
        && TypesGen.isLong(b)
        && equals(TypesGen.asLong(a), TypesGen.asLong(b)) )
        || Arrays.equals(TypesGen.asImplicitIntArray(a), TypesGen.asImplicitIntArray(b));
  }
  
  public static Object expect(Object o) throws UnexpectedResultException {
    if ( !Noun.isAtom(o) ) {
      throw new UnexpectedResultException(o);
    }
    return o;
  }
  
  public static Object orBail(Object o) {
    if ( !Noun.isAtom(o) ) {
      throw new Bail();
    }
    return o;
  }

  public static byte bloqOrBail(long atom) {
    if ( atom >= 32 || atom < 0) {
      throw new Bail();
    }
    return (byte) atom;
  }
  
  public static int expectInt(Object a) throws UnexpectedResultException {
    long al = expectLong(a);
    int  ai = (int) al;
    if ( al != ai ) {
      throw new UnexpectedResultException(a);
    }
    return ai;
  }

  public static int intOrBail(Object a) {
    try {
      return expectInt(a);
    }
    catch ( UnexpectedResultException e ) {
      throw new Bail();
    }
  }
  
  public static long expectLong(Object a) throws UnexpectedResultException {
    return TypesGen.expectLong(a);
  }
  
  public static long longOrBail(Object a) {
    try {
      return expectLong(a);
    }
    catch (UnexpectedResultException e) {
      throw new Bail();
    }
  }
  
  public static int expectUnsignedInt(Object a) throws UnexpectedResultException {
    long al  = expectLong(a);
    if ( al != (al & 0x00000000FFFFFFFFL) ) {
      throw new UnexpectedResultException(a);
    }
    return (int) al;
  }
  
  public static int unsignedIntOrBail(Object a) {
    try {
      return expectUnsignedInt(a);
    }
    catch (UnexpectedResultException e ) {
      throw new Bail();
    }
  }
  
  public static byte[] forceBytes(Object a, int maxLen) {
    return forceBytes(a, maxLen, false);
  }
  
  public static byte[] forceBytes(Object a, int maxLen, boolean trim) {
    byte[] src = toByteArray(a);
    if ( src.length == maxLen ) {
      return src;
    }
    if ( !trim && src.length > maxLen ) {
      throw new Bail();
    }
    byte[] r = new byte[maxLen];
    System.arraycopy(src, 0, r, 0, Math.min(src.length, maxLen));
    return r;
  }
  
  public static Object fromByteArray(byte[] pill) {
    return fromByteArray(pill, LITTLE_ENDIAN);
  }
  
  public static Object fromByteArray(byte[] pill, boolean endian) {
    int len  = pill.length;
    int trim = len % 4;

    if (endian == BIG_ENDIAN) {
      pill = Arrays.copyOf(pill, len);
      reverse(pill);
    }

    if (trim > 0) {
      int    nlen = len + (4-trim);
      byte[] npil = new byte[nlen];
      System.arraycopy(pill, 0, npil, 0, len);
      pill = npil;
      len = nlen;
    }

    int   size  = len / 4;
    int[] words = new int[size];
    int i, b, w;
    for (i = 0, b = 0; i < size; ++i) {
      w =  (pill[b++] << 0)  & 0x000000FF;
      w ^= (pill[b++] << 8)  & 0x0000FF00;
      w ^= (pill[b++] << 16) & 0x00FF0000;
      w ^= (pill[b++] << 24) & 0xFF000000;
      words[i] = w;
    }

    return malt(words);
  }
  
  public static Object fromString(String s) {
    return fromString(s, 10);
  }
  
  public static Object fromString(String s, int radix) {
    char[] car = s.toCharArray();
    int    len = car.length,
           cpw = MPN.chars_per_word(radix),
           i;
    byte[] dig = new byte[len];
    int[]  wor = new int[(len / cpw) + 1];

    for (i = 0; i < len; ++i) {
        dig[i] = (byte) Character.digit(car[i], radix);
    }

    MPN.set_str(wor, dig, len, radix);

    return malt(wor);
}
  
  public static boolean getNthBit(int[] atom, int n) {
    int pix = n >> 5;
    
    if ( pix >= atom.length ) {
      return false;
    }
    else {
      return (1 & (atom[pix] >>> (n & 31))) != 0;
    }
  }
  
  public static boolean getNthBit(long atom, int n) {
    if ( n >= (Long.SIZE - 1) ) {
      return false;
    }
    else {
      return ((atom & (1L << n)) != 0);
    }
  }
  
  public static boolean getNthBit(Object atom, int n) {
    if ( atom instanceof Long ) {
      return getNthBit((long) atom, n);
    }
    else {
      return getNthBit((int[]) atom, n);
    }
  }
  
  public static int[] incrementInPlace(int[] vol) {
    for ( int i = 0; i < vol.length; i++ ) {
      if ( 0 != ++vol[i] ) {
        return vol;
      }
    }
    int[] bigger = new int[vol.length + 1];
    bigger[bigger.length] = 1;
    return bigger;
  }
  
  public static int[] increment(int[] atom) {
    return incrementInPlace(Arrays.copyOf(atom, atom.length));
  }
  
  public static long increment(long atom) throws ArithmeticException {
    if ( 0L == ++atom ) {
      throw new ArithmeticException();
    }
    return atom;
  }
  
  public static Object increment(Object atom) {
    if ( TypesGen.isLong(atom) ) {
      try {
        return increment(TypesGen.asLong(atom));
      } 
      catch (ArithmeticException e) {
        return MINIMUM_INDIRECT;
      }
    }
    else {
      return increment(TypesGen.asIntArray(atom));
    }
  }

  private static boolean isTa(String s) {
    for ( char c : s.toCharArray() ) {
      if ( c < 32 || c > 127 ) {
        return false;
      }
    }
    return true;
  }
  
  private static boolean isTas(String s) {
    for ( char c : s.toCharArray() ) {
      if ( !Character.isLowerCase(c)
          && !Character.isDigit(c)
          && '-' != c) {
        return false;
      }
    }
    return true;
  }
  
  public static boolean isZero(Object atom) {
    return TypesGen.isLong(atom) && 0L == TypesGen.asLong(atom);
  }
  
  private static Trel jam(Map<Object,Object> m, Object a, Object b, Object l) {
    Object c = m.get(a);
    
    if ( m.containsKey(a) ) {
      if ( Noun.isAtom(a) && met(a) <= met(c) ) {
        return jamFlat(m, a, l);
      }
      else {
        return jamPtr(m, c, l);
      }
    }
    else {
      m.put(a, b);
      if ( Noun.isAtom(a) ) {
        return jamFlat(m, a, l);
      }
      else {
        Cell pair = Cell.orBail(a);
        return jamPair(m, pair.head, pair.tail, b, l);
      }     
    }
  }
  
  @TruffleBoundary
  public static Object jam(Object a) {
    Trel x   = jam(new HashMap<Object,Object>(), a, 0L, 0L);
    Object q = List.flop(x.q);
    
    return can((byte)0, new List(q));
  }
  
  private static Trel jamFlat(Map<Object, Object> m, Object a, Object l) {
    Cell d     = mat(a);
    Object x   = increment(d.head),
           tcc = new Cell(x, lsh((byte) 0, 1, d.tail)),
           tc  = new Cell(tcc, l);
    
    return new Trel(x, tc, 0L);
  }
  
  private static Trel jamPair(Map<Object, Object> m, Object ha, Object ta, Object b, Object l) {
    Object x, y, z;
    Cell w;
    Trel d, e;

    w = new Cell(new Cell(2L, 1L), l);
    x = add(2L, b);
    d = jam(m, ha, x, w);
    y = add(x, d.p);
    e = jam(m, ta, y, d.q);
    z = add(d.p, e.p);
    
    return new Trel(add(2L, z), e.q, 0L);
  }
  
  private static Trel jamPtr(Map<Object, Object> m, Object uc, Object l) {
    Cell d, ii, in;
    Object x, y;

    d = mat(uc);
    x = lsh((byte)0, 2, d.tail);
    y = add(2L, d.head);
    ii = new Cell(y, mix(3L, x));
    in = new Cell(ii, l);
    
    return new Trel(y, in, 0L);
  }

  public static Object lsh(byte bloq, int count, Object atom) {
    int len = met(bloq, atom),
        big;

    if ( 0 == len ) {
      return 0L;
    }
    try {
      big = Math.addExact(count, len);
    }
    catch (ArithmeticException e) {
      throw new Bail();
    }
    
    int[] sal = slaq(bloq, big);
    chop(bloq, 0, len, count, sal, atom);

    return malt(sal);
  }

  public static Object malt(int[] words) {
    int bad = 0;

    for ( int i = words.length - 1; i >= 0; --i) {
      if ( words[i] == 0 ) {
        ++bad;
      }
      else {
        break;
      }
    }

    if ( bad > 0 ) {
      words = Arrays.copyOfRange(words, 0, words.length - bad);
    }

    if ( 0 == words.length ) {
      return 0L;
    }
    else if ( words != null && words.length > 2 ) {
      return words;
    }
    else if (words.length == 1) {
      return words[0] & 0xffffffffL;
    }
    else {
      return ((words[1] & 0xffffffffL) << 32) | (words[0] & 0xffffffffL);
    }
  }

  public static Object mas(Object atom) {
    int b = met(atom);
    if ( b < 2 ) {
      throw new Bail();
    }
    Object c = bex(b - 1),
           d = bex(b - 2),
           e = sub(atom, c);
    return con(e, d);
  }
  
  private static Cell mat(Object a) {
    if ( isZero(a) ) {
      return new Cell(1L, 1L);
    }
    int bi, ci, ui;
    Object b, c, u, v, w, x, y, z, p, q;
    
    bi = met(a);
    b  = (long) bi;
    ci = met(b);
    c  = (long) ci;

    u  = dec(c);
    ui = Atom.intOrBail(u);
    v  = add(c, c);
    w  = bex(ci);
    x  = end((byte) 0, u, b);
    y  = lsh((byte) 0, ui, a);
    z  = mix(x, y);

    p  = add(v, b);
    q  = cat((byte) 0, w, z);
    
    return new Cell(p, q);
  }
  
  public static int met(byte bloq, Object atom) {
    int gal, daz;

    if ( TypesGen.isLong(atom) ) {
      long v = (long) atom;
      if ( 0 == v ) {
        return 0;
      }
      else {
        int left = (int) (v >>> 32);
        if ( left == 0 ) {
          gal = 0;
          daz = (int) v;
        }
        else {
          gal = 1;
          daz = left; 
        }
      }
    }
    else {
      int[] w = (int[]) atom;
      gal = w.length - 1;
      daz = w[gal];
    }
    
    switch (bloq) {
      case 0:
      case 1:
      case 2:
        int col = 32 - Integer.numberOfLeadingZeros(daz),
            bif = col + (gal << 5);

        return (bif + ((1 << bloq) - 1) >>> bloq);

      case 3:
        return (gal << 2)
          + ((daz >>> 24 != 0)
            ? 4
            : (daz >>> 16 != 0)
            ? 3
            : (daz >>> 8 != 0)
            ? 2
            : 1);

      case 4:
        return (gal << 1) + ((daz >>> 16 != 0) ? 2 : 1);

      default: {
        int gow = bloq - 5;
        return ((gal + 1) + ((1 << gow) - 1)) >>> gow;
      }
    }
  }
  
  public static int met(Object atom) {
    return met((byte)0, atom);
  }
  
  public static long mix(long a, long b) {
    return a ^ b;
  }
  
  public static Object mix(Object a, Object b) {
    byte w   = 5;
    int  lna = met(w, a);
    int  lnb = met(w, b);

    if ( (0 == lna) && (0 == lnb) ) {
      return 0L;
    }
    else {
      int i, len = Math.max(lna, lnb);
      int[] sal  = new int[len];
      int[] bow  = TypesGen.asImplicitIntArray(b);

      chop(w, 0, lna, 0, sal, a);

      for ( i = 0; i < lnb; i++ ) {
        sal[i] ^= bow[i];
      }

      return malt(sal);
    } 
  }
  
  public static Object mod(int[] x, int[] y) {
    int cmp = compare(x,y);
    if ( cmp < 0 ) {
      return y;
    }
    else if ( 0 == cmp ) {
      return 0L;
    }
    else if ( 1 == y.length ) {
      int[] q = new int[x.length];
      return (long) MPN.divmod_1(q, x, x.length, y[0]);
    }
    else {
      return divmod(x,y).tail;
    }
  }
  
  public static long mod(long a, long b) {
    return Long.remainderUnsigned(a, b);
  }

  public static Object mod(Object a, Object b) {
    if ( TypesGen.isLong(a) && TypesGen.isLong(b) ) {
      return mod(TypesGen.asLong(a), TypesGen.asLong(b));
    }
    return mod(TypesGen.asImplicitIntArray(a), TypesGen.asImplicitIntArray(b));
  }

  public static long mote(String s) {
    return unsignedIntOrBail(stringToCord(s));
  }
  
  public static int mug(Object atom) {
    int[] words = TypesGen.asImplicitIntArray(atom);
    return mug_words((int) 2166136261L, words.length, words);
  }
  
  private static int mug_words(int off, int nwd, int[] wod) {
    int has, out; 

    while ( true ) {
      has = mug_words_in(off, nwd, wod);
      out = Noun.mug_out(has);
      if ( 0 != out ) {
        return out;
      }
      ++off;
    }
  }
  
  private static int mug_words_in(int off, int nwd, int[] wod) {
    if (0 == nwd) {
      return off;
    }
    int i, x;
    for (i = 0; i < (nwd - 1); ++i) {
      x = wod[i];

      off = Noun.mug_fnv(off ^ ((x >>> 0)  & 0xff));
      off = Noun.mug_fnv(off ^ ((x >>> 8)  & 0xff));
      off = Noun.mug_fnv(off ^ ((x >>> 16) & 0xff));
      off = Noun.mug_fnv(off ^ ((x >>> 24) & 0xff));
    }
    x = wod[nwd - 1];
    if (x != 0) {
      off = Noun.mug_fnv(off ^ (x & 0xff));
      x >>>= 8;
      if (x != 0) {
        off = Noun.mug_fnv(off ^ (x & 0xff));
        x >>>= 8;
        if (x != 0) {
          off = Noun.mug_fnv(off ^ (x & 0xff));
          x >>>= 8;
          if (x != 0) {
            off = Noun.mug_fnv(off ^ (x & 0xff));
          }
        }
      }
    }
    return off;
  }

  @TruffleBoundary
  public static long muk(int seed, int length, Object atom) {
    assert Atom.met((byte) 5, seed) <= 1;
    assert Atom.met(length) <= 31;
    byte[] key = forceBytes(atom, length);
    return Hashing.murmur3_32(seed).hashBytes(key).padToLong();
  }
  
  public static Object mul(int[] x, int[] y) {
    int xlen = x.length,
        ylen = y.length;
    int[] dest = new int[xlen + ylen];
       
    if ( xlen < ylen ) {
      int zlen = xlen;
      int[] z = x;

      x = y;
      y = z;
      xlen = ylen;
      ylen = zlen;
    }

    MPN.mul(dest, x, xlen, y, ylen);
    return malt(dest);
  }
  
  public static long mul(long a, long b) throws ArithmeticException {
    return Math.multiplyExact(a, b);
  }

  public static Object mul(Object a, Object b) {
    if ( TypesGen.isLong(a) && TypesGen.isLong(b) ) {
      try {
        return mul(TypesGen.asLong(a), TypesGen.asLong(b));
      }
      catch (ArithmeticException e) {
      }
    }
    return mul(TypesGen.asImplicitIntArray(a), TypesGen.asImplicitIntArray(b));
  }

  public static Object peg(Object axis, Object to) {
    if ( equals(to, 1L) ) {
      return axis;
    }
    else {
      int c = met(to),
          d = c - 1;

      Object e = lsh((byte)0, d, 1L),
             f = sub(to, e),
             g = lsh((byte) 0, d, axis);
      
      return add(f, g);
    }
  }
  
  @TruffleBoundary
  public static void pretty(Writer out, int[] cur) throws IOException {
    if ( 1 == cur.length && Long.compareUnsigned(cur[0], 65536) < 0 ) {
      raw(out, cur, 10, 3);
    }
    else {
      String cord = cordToString(cur);
      if ( null != cord ) {
        if ( isTas(cord) ) {
          out.write('%');
          out.write(cordToString(cur));
          return;
        }
        else if ( isTa(cord) ) {
          out.write('\'');
          out.write(cordToString(cur));
          out.write('\'');
          return;
        }
      }
      out.write("0x");
      raw(out, cur, 16, 4);
    }
  }

  public static Object rap(byte a, Iterable<Object> b) {
    int tot = 0;
    try {
      for ( Object i : b ) {
        tot = Math.addExact(tot, met(a, Atom.orBail(i)));
      }
    }
    catch ( ArithmeticException e ) {
      throw new Bail();
    }
      
    if ( 0 == tot ) {
      return 0L;
    }
    
    int[] sal = slaq(a, tot);
    int pos = 0;
    
    for ( Object i : b ) {
      int len = met(a, i);
      chop(a, 0, len, pos, sal, i);
      pos += len;
    }
    
    return malt(sal);
  }
  
  @TruffleBoundary
  public static void raw(Writer out, int[] cur, int radix, int dot) throws IOException {
    Deque<Character> digits = new ArrayDeque<Character>();

    int len = cur.length,
        size = len,
        doc  = 0;

    cur = Arrays.copyOf(cur, len);

    while ( true ) {
      char dig = Character.forDigit(MPN.divmod_1(cur, cur, size, radix), radix);
      digits.push(dig);
      if (cur[len-1] == 0) {
        if (--len == 0) {
          break;
        }
      }
      if (++doc == dot) {
        doc = 0;
        digits.push('.');
      }
    }
    
    while ( !digits.isEmpty() ) {
      out.write(digits.pop());
    }
  }
  
  public static Object rep(byte a, Iterable<Object> b) {
    int tot = 0;
    try {
      for ( Object i : b ) {
        Atom.orBail(i);
        tot = Math.incrementExact(tot);
      }
    }
    catch ( ArithmeticException e ) {
      throw new Bail();
    }
    
    int[] sal = slaq(a, tot);
    int pos = 0;
    
    for ( Object i : b ) {
      chop(a, 0, 1, pos++, sal, i);
    }
    return malt(sal);
  }
  
  /* IN-PLACE */
  private static byte[] reverse(byte[] a) {
    int i, j;
    byte b;
    for (i = 0, j = a.length - 1; j > i; ++i, --j) {
      b = a[i];
      a[i] = a[j];
      a[j] = b;
    }
    return a;
  }
  
  public static Object rip(byte a, Object b) {
    int[] words = TypesGen.asImplicitIntArray(b);
    Object pir = 0L;
    if ( a < 5 ) {
      int met = met(a, b),
          mek = ((1 << (1 << a)) - 1);

      for ( int i = 0; i < met; ++i ) {
        int pat = met - (i + 1),
            bit = pat << a,
            wor = bit >>> 5,
            sif = bit & 31,
            src = words[wor],
            rip = (src >> sif) & mek;

        pir = new Cell((long) rip, pir);
      }
    }
    else {
      byte sang = (byte) (a - 5);
      int met = met(a, b),
          len = met((byte) 5, b),
          san = 1 << sang,
          dif = (met << sang) - len,
          tub = (0 == dif) ? san : san - dif;

      for ( int i = 0; i < met; ++i ) {
        int pat = met - (i + 1),
            wut = pat << sang,
            sap = ((0 == i) ? tub : san);
        int[] sal = new int[sap];
        
        for ( int j = 0; j < sap; ++j ) {
          sal[j] = words[wut + j];
        }
        
        pir = new Cell(malt(sal), pir);
      }
    }
    return pir;
  }
  
  public static Object rsh(byte bloq, int count, Object atom) {
    int len = met(bloq, atom);

    if ( count >= len ) {
      return 0L;
    }
    else {
      int[] sal = slaq(bloq, len - count);

      chop(bloq, count, len - count, 0, sal, atom);

      return malt(sal);
    }
  }

  public static Cell rub(Object a, Object b) {
    Object c, d, e, w, x, y, z, p, q, m;

    m = add(a, (long) met(b));
    x = a;

    while ( isZero(cut((byte)0, x, 1L, b)) ) {
      y = increment(x);
      
      //  Sanity check: crash if decoding more bits than available
      if ( compare(x, m) > 0 ) {
        throw new Bail();
      }

      x = y;
    }

    if ( equals(x, a) ) {
      return new Cell(1L, 0L);
    }
    c = sub(x, a);
    d = increment(x);

    x = dec(c);
    y = bex(longOrBail(x));
    z = cut((byte)0, d, x, b);

    e = add(y, z);
    w = add(c, c);
    y = add(w, e);
    z = add(d, x);

    p = add(w, e);
    q = cut((byte)0, z, e, b);
    
    return new Cell(p, q);
  }
  
  @TruffleBoundary
  private static byte[] doSha(String algo, byte[] bytes) {
    try {
      return MessageDigest.getInstance(algo).digest(bytes);
    }
    catch (NoSuchAlgorithmException e) {
      e.printStackTrace();
      throw new Bail();
    }
  }

  private static Object sha_help(Object len, Object atom, String algo) {
    int lei = intOrBail(len);
    byte[] in = forceBytes(atom, lei, true);
    return Atom.fromByteArray(doSha(algo, in));
  }
  
  public static Object shal(Object len, Object atom) {
    return sha_help(len, atom, "SHA-512");
  }
  
  public static Object shan(Object atom) {
    byte[] bytes, hash;

    bytes = Atom.toByteArray(atom);
    try {
      hash = MessageDigest.getInstance("SHA-1").digest(bytes);
      // XX: Why is this backwards?
      return Atom.fromByteArray(hash, BIG_ENDIAN);
    }
    catch (NoSuchAlgorithmException e) {
      e.printStackTrace();
      throw new Bail();
    }
  }

  public static Object shay(Object len, Object atom) {
    return sha_help(len, atom, "SHA-256");
  }

  public static int[] slaq(byte bloq, int len) {
    int big = ((len << bloq) + 31) >>> 5;
    return new int[big];
  }
  
  @TruffleBoundary
  public static Object stringToCord(String s) {
    try {
      return fromByteArray(s.getBytes("UTF-8"));
    }
    catch (UnsupportedEncodingException e) {
      return null;
    }
  }
  
  public static int[] sub(int[] a, int[] b) {
    Square s = new Square(a, b);
    int[] dst = new int[s.len];
    int bor = MPN.sub_n(dst, s.x, s.y, s.len);
    if ( bor != 0 ) {
      throw new Bail();
    }
    return dst;
  }

  public static long sub(long a, long b) {
    if ( -1 == compare(a, b) ) {
      throw new Bail();
    }
    else {
      return a - b;
    }
  }
  
  public static Object sub(Object a, Object b) {
    if ( TypesGen.isLong(a) && TypesGen.isLong(b) ) {
      return sub(TypesGen.asLong(a), TypesGen.asLong(b));
    }
    else {
      int[] aa = TypesGen.asImplicitIntArray(a);
      int[] ba = TypesGen.asImplicitIntArray(b);
      return malt(sub(aa, ba));
    }
  }

  private static Object takeBytes(byte[] src, int len) {
    return fromByteArray(Arrays.copyOfRange(src, 0, len));
  }
  
  public static byte[] toByteArray(Object a) {
    return toByteArray(a, LITTLE_ENDIAN);
  }
  
  public static byte[] toByteArray(Object atom, boolean endian) {
    if (isZero(atom)) {
      return new byte[1];
    }
    int[]  wor = TypesGen.asImplicitIntArray(atom);
    int    bel = met((byte)3, atom);
    byte[] buf = new byte[bel];
    int    w, i, b;
    for (i = 0, b = 0;;) {
      w = wor[i++];

      buf[b++] = (byte) ((w & 0x000000FF) >>> 0);
      if (b >= bel) break;

      buf[b++] = (byte) ((w & 0x0000FF00) >>> 8);
      if (b >= bel) break;

      buf[b++] = (byte) ((w & 0x00FF0000) >>> 16);
      if (b >= bel) break;

      buf[b++] = (byte) ((w & 0xFF000000) >>> 24);
      if (b >= bel) break;
    }
    if (endian == BIG_ENDIAN) {
      reverse(buf);
    }
    return buf;
  }

  public static String toString(Object atom) {
    StringWriter out = new StringWriter();
    try {
      pretty(out, TypesGen.asImplicitIntArray(atom));
      return out.toString();
    }
    catch ( IOException e ) {
      return null;
    }
  }

  public static String toString(Object atom, int radix, int dot) {
    StringWriter out = new StringWriter();
    try {
      raw(out, TypesGen.asImplicitIntArray(atom), radix, dot);
      return out.toString();
    }
    catch ( IOException e ) {
      return null;
    }
  }

  public final Object value;

  /*
 * Only make an instance if you need .equals and .hashCode() for a map, etc.
 */ 
  public Atom(Object atom) {
    assert Noun.isAtom(atom);
    this.value = atom;
  }

  @Override
  public boolean equals(Object b) {
    return (b instanceof Atom)
        && equals(value, ((Atom) b).value);
  }

  public int hashCode() {
    return mug(value);
  }

  public String toString() {
    return toString(value);
  }

  public static Object trip(Object atom) {
    return rip((byte)3, atom);
  }

  public static long vor(Object a, Object b) {
    long c = Atom.mug((long)Noun.mug(a));
    long d = Atom.mug((long)Noun.mug(b));
    return (c == d)
        ? dor(a, b)
        : (compare(c, d) == -1)
        ? YES
        : NO;
  }

  @TruffleBoundary
  public static long dor(Object a, Object b) {
    if ( Noun.isAtom(a) ) {
      if ( Noun.isAtom(b) ) {
        return (compare(a, b) == -1) ? YES : NO;
      }
      else {
        return YES;
      }
    }
    else if ( Noun.isAtom(b) ) {
      return NO;
    }
    else {
      Cell ac = TypesGen.asCell(a),
           bc = TypesGen.asCell(b);
      return a.equals(b)
          ? YES
          : Noun.equals(ac.head, bc.head)
          ? dor(ac.tail, bc.tail)
          : dor(ac.head, bc.head);
    }
  }

  public static long gor(Object a, Object b) {
    long c = Noun.mug(a),
         d = Noun.mug(b);
    
    return (c == d)
        ? dor(a, b)
        : (compare(c, d) == -1)
        ? YES
        : NO;
  }

  public static Object lore(Object lub) {
    int pos = 0;
    Object tez = 0L;
    byte[] lubytes = toByteArray(lub);
    int len = lubytes.length;

    while ( true ) {
      int meg = 0;
      boolean end;
      byte byt;
      while ( true ) {
        if ( pos >= len ) {
          byt = 0;
          end = true;
          break;
        }
        byt = pos+meg < len ? lubytes[pos+meg] : 0;

        if ( 10 == byt || 0 == byt ) {
          end = (byt == 0);
          break;
        }
        else {
          ++meg;
        }
      }
      if ( (byt == 0) && ((pos + meg + 1) < len) ) {
        throw new Bail();
      }
      byte[] byts = Arrays.copyOfRange(lubytes, pos, pos+meg);
      if ( pos >= len ) {
        return List.flop(tez);
      }
      else {
        Object mega = fromByteArray(Arrays.copyOf(byts, meg));
        tez = new Cell(mega, tez);
        if ( end ) {
          return List.flop(tez);
        }
        pos += meg + 1;
      }
    }
  }

  public static byte expectBloq(Object atom) throws UnexpectedResultException {
    int i = expectUnsignedInt(atom);
    if ( i >= 32 ) {
      throw new UnexpectedResultException(atom);
    }
    return (byte) atom;
  }
}
