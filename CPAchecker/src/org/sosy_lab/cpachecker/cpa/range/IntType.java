/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2015  Dirk Beyer
 *  All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 *  CPAchecker web page:
 *    http://cpachecker.sosy-lab.org
 */
package org.sosy_lab.cpachecker.cpa.range;

import java.io.Serializable;

import org.eclipse.cdt.core.dom.ast.IBasicType;
import org.eclipse.cdt.core.dom.ast.IType;
import org.eclipse.cdt.core.dom.ast.ITypedef;
import org.sosy_lab.cpachecker.cfa.types.MachineModel;
import org.sosy_lab.cpachecker.cfa.types.c.CNumericTypes;
import org.sosy_lab.cpachecker.cfa.types.c.CPointerType;
import org.sosy_lab.cpachecker.cfa.types.c.CSimpleType;
import org.sosy_lab.cpachecker.cfa.types.c.CType;

public class IntType implements Serializable {

  private static final long serialVersionUID = 6762360048912709384L;

  /**
   * we specify the platform as LINUX64 since it is our current platform
   * LINUX64 can be replaced with LINUX32 or other custom machine models
   */
  private static MachineModel machineModel = MachineModel.LINUX64;

  /**
   * all possible integer types are listed below
   */
  private static final int CHAR = 1;
  private static final int SHORT = 2;
  private static final int INT = 3;
  private static final int LONGINT = 4;
  private static final int LONGLONGINT = 5;

  /**
   * OVERLONG: no internal type is capable to hold the value
   */
  private static final int OVERLONG = 6;
  /**
   * INDEX: for array subscript, ranges in [-SIZE_MAX, SIZE_MAX].
   * This is a virtual type for representation purpose only. You
   * should not merge it with other types or obtain an instance of
   * corresponding CType.
   */
  private static final int INDEX = 7;
  /**
   * SHIFT_LEFT: FixGuide for left bit shift operation
   * This is a virtual type and SHOULD NOT be used for any other purpose
   */
  private static final int SHIFT_LEFT = 8;
  /**
   * SHIFT_RIGHT: FixGuide for right bit shift operation
   * This is a virtual type and SHOULD NOT be used for any other purpose
   */
  private static final int SHIFT_RIGHT = 9;
  /**
   * OTHER includes float, double, pointer, void, etc.
   */
  private static final int OTHER = 10;

  /**
   * integer has sign or not?
   * (for other types, sign information is unimportant)
   */
  private static final boolean SIGNED = true;
  private static final boolean UNSIGNED = false;

  private int type;
  private boolean sign;

  public IntType() {
    this.type = OTHER;
    this.sign = SIGNED;
  }

  public static final IntType UNKNOWN = new IntType();

  public static final IntType OLTYPE = new IntType(OVERLONG, SIGNED);

  // this is platform-dependent
  public static final IntType SIZET = new IntType(INT, false);
  public static final CompInteger SIZE_MAX = new CompInteger(machineModel.getMaximalIntegerValue(CNumericTypes.UNSIGNED_INT));

  // virtual types
  public static final IntType INDEX_TYPE = new IntType(INDEX, false);
  public static final IntType LSHIFT_TYPE = new IntType(SHIFT_LEFT, false);
  public static final IntType RSHIFT_TYPE = new IntType(SHIFT_RIGHT, false);

  public IntType(CSimpleType t) {
    this.type = getIntType(t);
    this.sign = machineModel.isSigned(t);
  }

  public IntType(Range r) {
    if(r.isEmpty()) {
      // TODO: appropriate type for empty set? can be anything
      this.type = CHAR;
      this.sign = SIGNED;
      return;
    }
    if(r.getLow().signum() >= 0) {
      // in this case unsigned type is sufficient
      CompInteger high = r.getHigh();
      if(high.compareTo(machineModel.getMaximalIntegerValue(CNumericTypes.UNSIGNED_CHAR)) <= 0) {
        this.type = CHAR;
        this.sign = UNSIGNED;
      } else if(high.compareTo(machineModel.getMaximalIntegerValue(CNumericTypes.UNSIGNED_SHORT_INT)) <= 0) {
        this.type = SHORT;
        this.sign = UNSIGNED;
      } else if(high.compareTo(machineModel.getMaximalIntegerValue(CNumericTypes.UNSIGNED_INT)) <= 0) {
        this.type = INT;
        this.sign = UNSIGNED;
      } else if(high.compareTo(machineModel.getMaximalIntegerValue(CNumericTypes.UNSIGNED_LONG_INT)) <= 0) {
        this.type = LONGINT;
        this.sign = UNSIGNED;
      } else if(high.compareTo(machineModel.getMaximalIntegerValue(CNumericTypes.UNSIGNED_LONG_LONG_INT)) <= 0) {
        this.type = LONGLONGINT;
        this.sign = UNSIGNED;
      } else {
        this.type = OVERLONG;
        this.sign = SIGNED;
      }
    } else {
      // in this case we should consider signed type instead
      CompInteger low = r.getLow();
      CompInteger high = r.getHigh();
      if(low.compareTo(machineModel.getMinimalIntegerValue(CNumericTypes.SIGNED_CHAR)) >= 0 &&
          high.compareTo(machineModel.getMaximalIntegerValue(CNumericTypes.SIGNED_CHAR)) <= 0) {
        this.type = CHAR;
        this.sign = SIGNED;
      } else if(low.compareTo(machineModel.getMinimalIntegerValue(CNumericTypes.SIGNED_SHORT_INT)) >= 0 &&
          high.compareTo(machineModel.getMaximalIntegerValue(CNumericTypes.SIGNED_SHORT_INT)) <= 0) {
        this.type = SHORT;
        this.sign = SIGNED;
      } else if(low.compareTo(machineModel.getMinimalIntegerValue(CNumericTypes.SIGNED_INT)) >= 0 &&
          high.compareTo(machineModel.getMaximalIntegerValue(CNumericTypes.SIGNED_INT)) <= 0) {
        this.type = INT;
        this.sign = SIGNED;
      } else if(low.compareTo(machineModel.getMinimalIntegerValue(CNumericTypes.SIGNED_LONG_INT)) >= 0 &&
          high.compareTo(machineModel.getMaximalIntegerValue(CNumericTypes.SIGNED_LONG_INT)) <= 0) {
        this.type = LONGINT;
        this.sign = SIGNED;
      } else if(low.compareTo(machineModel.getMinimalIntegerValue(CNumericTypes.SIGNED_LONG_LONG_INT)) >= 0 &&
          high.compareTo(machineModel.getMaximalIntegerValue(CNumericTypes.SIGNED_LONG_LONG_INT)) <= 0) {
        this.type = LONGLONGINT;
        this.sign = SIGNED;
      } else {
        this.type = OVERLONG;
        this.sign = SIGNED;
      }

    }
  }

  public int getIntType(CSimpleType t) {
    switch(t.getType()) {
    case CHAR: return IntType.CHAR;
    case UNSPECIFIED:
    case INT:
      if(t.isLongLong()) {
        return IntType.LONGLONGINT;
      } else if(t.isLong()) {
        return IntType.LONGINT;
      } else if(t.isShort()) {
        return IntType.SHORT;
      } else {
        return IntType.INT;
      }
    case BOOL:
      return IntType.INT;
    case FLOAT:
    case DOUBLE:
      return IntType.OTHER;
    default:
      throw new AssertionError("Unrecognized CBasicType " + t.getType());
    }
  }

  private IntType(int type, boolean sign) {
    this.sign = sign;
    this.type = checkType(type);
  }

  public boolean checkOverlong() {
    return this.type == OVERLONG;
  }

  private int checkType(int type) {
    switch(type) {
    case CHAR:
    case SHORT:
    case INT:
    case LONGINT:
    case LONGLONGINT:
    case OVERLONG:
    case INDEX:
    case SHIFT_LEFT:
    case SHIFT_RIGHT:
    case OTHER:
      return type;
    default:
      throw new AssertionError("Unrecognized Specified Type " + type);
    }
  }

  private CSimpleType getCSimpleType() {
    if(this.sign) {
      // signed case
      switch(this.type) {
      case CHAR: return CNumericTypes.SIGNED_CHAR;
      case SHORT: return CNumericTypes.SIGNED_SHORT_INT;
      case INT: return CNumericTypes.SIGNED_INT;
      case LONGINT: return CNumericTypes.SIGNED_LONG_INT;
      case LONGLONGINT: return CNumericTypes.UNSIGNED_LONG_LONG_INT;
      default: throw new AssertionError("Non-integer Type " + this.type);
      }
    } else {
      // unsigned case
      switch(this.type) {
      case CHAR: return CNumericTypes.UNSIGNED_CHAR;
      case SHORT: return CNumericTypes.UNSIGNED_SHORT_INT;
      case INT: return CNumericTypes.UNSIGNED_INT;
      case LONGINT: return CNumericTypes.UNSIGNED_LONG_INT;
      case LONGLONGINT: return CNumericTypes.UNSIGNED_LONG_LONG_INT;
      default: throw new AssertionError("Non-integer Type " + this.type);
      }
    }
  }

  public static int getSize(CType type) {
    return machineModel.getSizeof(type);
  }

  public static int getAlignof(CType type) {
    return machineModel.getAlignof(type);
  }

  public Range getTypeRange() {
    if(this.type == OTHER) {
      // this variable could have other types(e.g. void, ptr)
      return Range.EMPTY;
    } else if(this.type == OVERLONG) {
      // this variable is integer but too large to fit any internal types
      return Range.UNBOUND;
    } else if(this.type == INDEX) {
      return new Range(SIZE_MAX.negate(), SIZE_MAX);
    }
    // getCSimpleType can be sure to be called safely
    CSimpleType thisType = getCSimpleType();
    CompInteger lowerBound, upperBound;
    lowerBound = new CompInteger(machineModel.getMinimalIntegerValue(thisType));
    upperBound = new CompInteger(machineModel.getMaximalIntegerValue(thisType));
    return new Range(lowerBound, upperBound);
  }

  public static Range getTypeRange(CSimpleType t) {
    CompInteger lower, upper;
    lower = new CompInteger(machineModel.getMinimalIntegerValue(t));
    upper = new CompInteger(machineModel.getMaximalIntegerValue(t));
    return new Range(lower, upper);
  }

  public boolean contains(Range range) {
    Range thisRange = this.getTypeRange();
    return thisRange.contains(range);
  }

  public boolean isOverflow(Range range) {
    // if the type range does not fully contain the specified range, then overflow occurs
    if(this.contains(range)) {
      return false;
    } else {
      return true;
    }
  }

  public boolean isNotInt() {
    if(this.type == OTHER) {
      return true;
    } else {
      return false;
    }
  }

  public int getSize() {
    switch(this.type) {
    case OVERLONG:
    case LONGLONGINT:
      return machineModel.getSizeofLongLongInt();
    case LONGINT:
      return machineModel.getSizeofLongInt();
    case INT:
      return machineModel.getSizeofInt();
    case SHORT:
      return machineModel.getSizeofShort();
    case CHAR:
      return machineModel.getSizeofChar();
    default:
      // for OTHER case
      return machineModel.getSizeofPtr();
    }
  }

  public boolean getSign() {
    return this.sign;
  }

  public IntType flipSign() {
    return new IntType(this.type, !this.sign);
  }

  public static int getLongestIntSize() {
    return machineModel.getSizeofLongLongInt();
  }

  public int getAlignOf() {
    if(this.type == OTHER) {
      // by default we are discussing pointers
      return machineModel.getAlignof(CPointerType.POINTER_TO_CONST_CHAR);
    }
    // if we reach here, no exception will be risen
    CSimpleType t = this.getCSimpleType();
    return machineModel.getAlignof(t);
  }

  /**
   * Integer promotion is required on bit-shift operations
   * @return promoted type
   */
  public IntType promoteInt() {
    int newkind = this.type;
    if(newkind < INT) {
      newkind = INT;
    }
    return new IntType(newkind, this.sign);
  }

  public IntType mergeWith(IntType tOther) {
    // For example, a pointer plus an integer is still a pointer
    if(this.type == OTHER || tOther.type == OTHER) {
      return IntType.UNKNOWN;
    }
    // these rules are for C integers. You can find the rules from C standard.
    if((this.type == OVERLONG && this.sign == false) || (tOther.type == OVERLONG && tOther.sign == false)) {
      return new IntType(OVERLONG, false);
    } else if((this.type == OVERLONG && this.sign == true) || (tOther.type == OVERLONG && tOther.sign == true)) {
      return new IntType(OVERLONG, true);
    } else if((this.type == LONGLONGINT && this.sign == false) || (tOther.type == LONGLONGINT && tOther.sign == false)) {
      return new IntType(LONGLONGINT, false);
    } else if(this.type == INDEX || tOther.type == INDEX) {
      return INDEX_TYPE;
    } else if((this.type == LONGLONGINT && this.sign == true) || (tOther.type == LONGLONGINT && tOther.sign == true)) {
      return new IntType(LONGLONGINT, true);
    } else if((this.type == LONGINT && this.sign == false) || (tOther.type == LONGINT && tOther.sign == false)) {
      return new IntType(LONGINT, false);
    } else if((this.type == LONGINT && this.sign == true) || (tOther.type == LONGINT && tOther.sign == true)) {
      return new IntType(LONGINT, true);
    } else if((this.type == INT && this.sign == false) || (tOther.type == INT && tOther.sign == false)) {
      return new IntType(INT, false);
    } else if((this.type == INT && this.sign == true) || (tOther.type == INT && tOther.sign == true)) {
      return new IntType(INT, true);
    } else if((this.type == SHORT && this.sign == false) || (tOther.type == SHORT && tOther.sign == false)) {
      return new IntType(SHORT, false);
    } else if((this.type == SHORT && this.sign == true) || (tOther.type == SHORT && tOther.sign == true)) {
      return new IntType(SHORT, true);
    } else if((this.type == CHAR && this.sign == false) || (tOther.type == CHAR && tOther.sign == false)) {
      return new IntType(CHAR, false);
    } else {
      return new IntType(CHAR, true);
    }
  }

  public boolean isNotOverlong() {
    return this.type != OVERLONG;
  }

  public boolean isOther() {
    return this.type == OTHER;
  }

  public static IntType interpret(IType astType) {
    int kind = OTHER;
    boolean astSign = true;
    if(astType instanceof IBasicType) {
      if(((IBasicType) astType).isUnsigned()) {
        astSign = false;
      }
      switch(((IBasicType) astType).getKind()) {
      case eChar:
        kind = CHAR;
        break;
      case eInt: {
        if(((IBasicType) astType).isShort()) {
          kind = SHORT;
        } else if(((IBasicType) astType).isLong()) {
          kind = LONGINT;
        } else if(((IBasicType) astType).isLongLong()) {
          kind = LONGLONGINT;
        }
        break;
      }
      default:
        // nothing to do
      }
      return new IntType(kind, astSign);
    } else if(astType instanceof ITypedef) {
      // we should interpret the REAL type of this named type
      return IntType.interpret(((ITypedef) astType).getType());
    } else {
      return IntType.UNKNOWN;
    }
  }

  @Override
  public int hashCode() {
    return super.hashCode();
  }

  @Override
  public String toString() {
    String repString = "";
    if(!this.sign) {
      repString = repString.concat("unsigned ");
    }

    // by default, an integer type is signed
    switch(this.type) {
    case CHAR:
      repString = repString.concat("char");
      return repString;
    case SHORT:
      repString = repString.concat("short");
      return repString;
    case INT:
      repString = repString.concat("int");
      return repString;
    case LONGINT:
      repString = repString.concat("long");
      return repString;
    case LONGLONGINT:
      repString = repString.concat("long long");
      return repString;
    default:
      /**
       * then we just return an empty string
       * NOTE: this does not mean that this type is illegal!
       */
      return "";
    }
  }

  @Override
  public boolean equals(Object obj) {
    if(this == obj) {
      return true;
    }

    if(!(obj instanceof IntType)) {
      return false;
    }

    IntType otherType = (IntType)obj;
    return otherType.type == this.type
        && otherType.sign == this.sign;
  }

  /**
   * Similar to toString method, but we print the type with specific
   * symbols used in SMT formula.
   *
   * @return
   */
  public String toOBJString() {
    // FIXME: a potential problem ---> how about using C identifiers with the same names?
    String result = "";

    if(this.type == OVERLONG) {
      return "OVERLONG";
    } else if(this.type == OTHER) {
      // this should not happen!
      return "OTHER";
    } else if(this.type == INDEX) {
      return "INDEX";
    } else if(this.type == SHIFT_LEFT) {
      return "SHIFT_LEFT";
    } else if(this.type == SHIFT_RIGHT) {
      return "SHIFT_RIGHT";
    }

    if(this.sign == SIGNED) {
      // nothing to do
    } else {
      result = result.concat("U");
    }

    switch(this.type) {
    case CHAR:
      result = result.concat("CHAR");
      break;
    case SHORT:
      result = result.concat("SHORT");
      break;
    case INT:
      result = result.concat("INT");
      break;
    case LONGINT:
      result = result.concat("LINT");
      break;
    case LONGLONGINT:
      result = result.concat("LLINT");
      break;
    }

    return result;
  }

  public static IntType fromOBJString(String string) {
    // parse the OBJString and return the corresponding IntType object
    if(string.equals("OVERLONG")) {
      return new IntType(OVERLONG, true);
    } else if(string.equals("INDEX")) {
      return INDEX_TYPE;
    } else if(string.equals("SHIFT_LEFT")) {
      return LSHIFT_TYPE;
    } else if(string.equals("SHIFT_RIGHT")) {
      return RSHIFT_TYPE;
    }
    // regular types
    if(string.charAt(0) == 'U') {
      // this is an unsigned type
      String subtype = string.substring(1);
      if(subtype.equals("CHAR")) {
        return new IntType(CHAR, false);
      } else if(subtype.equals("SHORT")) {
        return new IntType(SHORT, false);
      } else if(subtype.equals("INT")) {
        return new IntType(INT, false);
      } else if(subtype.equals("LINT")) {
        return new IntType(LONGINT, false);
      } else if(subtype.equals("LLINT")) {
        return new IntType(LONGLONGINT, false);
      }
    } else {
      if(string.equals("CHAR")) {
        return new IntType(CHAR, true);
      } else if(string.equals("SHORT")) {
        return new IntType(SHORT, true);
      } else if(string.equals("INT")) {
        return new IntType(INT, true);
      } else if(string.equals("LINT")) {
        return new IntType(LONGINT, true);
      } else if(string.equals("LLINT")) {
        return new IntType(LONGLONGINT, true);
      }
    }

    // other types are not permitted!
    return IntType.UNKNOWN;
  }

}
