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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import scala.actors.threadpool.Arrays;

public class Range implements Serializable {

  private static final long serialVersionUID = -773989536433622157L;

  private final CompInteger low;
  private final CompInteger high;

  public static final Range ZERO = new Range(0L, 0L);
  public static final Range ONE = new Range(1L, 1L);
  public static final Range BOOL = new Range(0L, 1L);
  /**
   * Range can be a valid interval, or a empty set which cannot be represented by its two bounds
   */
  public static final Range EMPTY = new Range(null, null);
  public static final Range UNBOUND = new Range(CompInteger.infneg, CompInteger.infpos);

  public Range(long value) {
    this.low = new CompInteger(value);
    this.high = new CompInteger(value);
    isSane();
  }

  public Range(CompInteger value) {
    this.low = value;
    this.high = value;
    isSane();
  }

  public Range(long low, long high) {
    if(low <= high) {
      this.low = new CompInteger(low);
      this.high = new CompInteger(high);
    } else {
      this.low = new CompInteger(high);
      this.high = new CompInteger(low);
    }
    isSane();
  }

  /**
   * In creating a new range object, we can guarantee that the lower and upper bounds are in correct sequence
   * @param low
   * @param high
   */
  public Range(CompInteger low, CompInteger high) {
    if(low == null && high == null) {
      this.low = null;
      this.high = null;
      return;
    } else if(low == null || high == null) {
      // illegal case, addressed in isSane() function
      isSane();
    }
    // Add support to range that may contain NaN
    if(low == CompInteger.nan || high == CompInteger.nan) {
      // since NaN denotes undetermined value, we set this range as UNBOUND
      this.low = CompInteger.infneg;
      this.high = CompInteger.infpos;
    } else{
    if(low.compareTo(high) <= 0) {
        this.low = low;
        this.high = high;
      } else {
        this.low = high;
        this.high = low;
      }
    }
    isSane();
  }

  /**
   * Empty set is allowed, thus we will only prohibit the case that the upperbound is larger
   *  than lower bound
   */
  private boolean isSane() {
    if(low.compareTo(high) == 1) {
      throw new IllegalStateException("low cannot be larger than high");
    }
    return true;
  }

  public CompInteger getLow() {
    return this.low;
  }

  public CompInteger getHigh() {
    return this.high;
  }

  @Override
  public boolean equals(Object other) {
    if(other != null & getClass().equals(other.getClass())) {
      Range another = (Range)other;
      if(isEmpty() && another.isEmpty()) {
        return true;
      } else if(isEmpty() || another.isEmpty()) {
        return false;
      }
      return low.equals(another.low) && high.equals(another.high);
    } else {
      return false;
    }
  }

  public boolean isEmpty() {
    return low == null && high == null;
  }

  public boolean isUnbound() {
    return !isEmpty() && low.equals(CompInteger.infneg) && high.equals(CompInteger.infpos);
  }

  public static Range createLowerBoundedRange(CompInteger lowerbound) {
    return new Range(lowerbound, CompInteger.infpos);
  }

  public static Range createUpperBoundedRange(CompInteger upperbound) {
    return new Range(CompInteger.infneg, upperbound);
  }

  public static Range createFalseRange() {
    return new Range(0L);
  }

  @Override
  public int hashCode() {
    if(isEmpty()) {
      return 0;
    }

    int result = 17;
    result = 31 * result + low.hashCode();
    result = 31 * result + high.hashCode();

    return result;
  }

  public Range union(Range other) {
    if(isEmpty() || other.isEmpty()) {
      return Range.EMPTY;
    } else if(low.compareTo(other.low) >= 0 && high.compareTo(other.high) <= 0) {
      return other;
    } else {
      return new Range(low.min(other.low), high.max(other.high));
    }
  }

  public boolean intersects(Range other) {
    if(isEmpty() || other.isEmpty()) {
      return false;
    }

    return (low.compareTo(other.low) >= 0 && low.compareTo(other.high) <= 0) ||
        (high.compareTo(other.low) >= 0 && high.compareTo(other.high) <= 0) ||
        (low.compareTo(other.low) <= 0 && high.compareTo(other.high) >= 0);
  }

  public Range intersect(Range other) {
    if(this.intersects(other)) {
      return new Range(low.max(other.low), high.min(other.high));
    } else {
      return Range.EMPTY;
    }
  }

  public boolean isGreaterThan(Range other) {
    return !isEmpty() && !other.isEmpty() && low.compareTo(other.high) > 0;
  }

  public boolean isGreaterOrEqualThan(Range other) {
    return !isEmpty() && !other.isEmpty() && low.compareTo(other.high) >= 0;
  }

  public boolean mayBeGreaterThan(Range other) {
    return other.isEmpty() || (!isEmpty() && !other.isEmpty() && high.compareTo(other.low) > 0);
  }

  public boolean mayBeGreaterOrEqualThan(Range other) {
    return other.isEmpty() || (!isEmpty() && !other.isEmpty() && high.compareTo(other.low) >= 0);
  }

  public boolean contains(Range other) {
    return other.isEmpty() || (!isEmpty() && !other.isEmpty() && low.compareTo(other.low) <= 0 && high.compareTo(other.high) >= 0);
  }

  public Range modulo(Range other) {
    // FIXME: if this range contains zero, we can just ignore it

    if(this.isEmpty() || other.isEmpty()) {
      return Range.EMPTY;
    }

    // why we compute the abolute value?
    // because in C expressions, the sig of (a % b) is consistent with that of a
    CompInteger lower = other.low.abs();
    CompInteger upper = other.high.abs();
    if(lower.signum() == 0) {
      lower = lower.add(CompInteger.one);
    }
    if(upper.signum() == 0) {
      upper = upper.add(CompInteger.one);
    }
    // don't worry about sequence of two bounds
    other = new Range(lower, upper);

    CompInteger newHigh;
    CompInteger newLow;

    CompInteger top;
    if(low.signum() >= 0) {
      top = high;
    } else {
      top = low.abs().max(high);
    }

    newHigh = top.min(other.high.subtract(CompInteger.one));

    if (low.signum() >= 0) {
      if(low.signum() == 0 || high.compareTo(other.low) >= 0) {
        newLow = CompInteger.zero;
      } else {
        newLow = low;
      }
    } else {
      newLow = low.max(CompInteger.one.subtract(other.high));
    }

    Range out = new Range(newLow, newHigh);
    return out;
  }

  public Range limitLowerBoundBy(Range other) {
    Range range = null;
    if(isEmpty() || other.isEmpty() || high.compareTo(other.low) < 0) {
      range = Range.EMPTY;
    } else {
      range = new Range(low.max(other.low), high);
    }
    return range;
  }

  public Range limitUpperBoundBy(Range other) {
    Range range = null;
    if(isEmpty() || other.isEmpty() || low.compareTo(other.high) > 0) {
      range = Range.EMPTY;
    } else {
      range = new Range(low, high.min(other.high));
    }
    return range;
  }

  public Range plus(Range other) {
    if(isEmpty() || other.isEmpty()) {
      return Range.EMPTY;
    }

    return new Range(low.add(other.low), high.add(other.high));
  }

  public Range plus(Long offset) {
    return plus(new Range(offset, offset));
  }

  public Range minus(Range other) {
    return plus(other.negate());
  }

  public Range minus(Long offset) {
    return plus(-offset);
  }

  private CompInteger[] removeNaN(CompInteger[] complist) {
    List<CompInteger> newlist = new ArrayList<>();
    for(CompInteger compint : complist) {
      if(compint.equals(CompInteger.nan)) {
        continue;
      }
      newlist.add(compint);
    }
    return newlist.toArray(new CompInteger[newlist.size()]);
  }

  public Range times(Range other) {
    if(isEmpty() || other.isEmpty()) {
      return Range.EMPTY;
    }
    CompInteger[] values = { low.multiply(other.low), low.multiply(other.high), high.multiply(other.low), high.multiply(other.high) };
    values = removeNaN(values);
    // if we reach here, values shouldn't be empty
    @SuppressWarnings("unchecked")
    CompInteger min = Collections.min(Arrays.asList(values));
    @SuppressWarnings("unchecked")
    CompInteger max = Collections.max(Arrays.asList(values));
    return new Range(min, max);
  }

  public Range divide(Range other) {
    if(isEmpty() || other.isEmpty()) {
      return Range.EMPTY;
    }
    if(other.contains(Range.ZERO)) {
      CompInteger upper = low.abs();
      upper = upper.max(high.abs());
      if(upper.signum() == 0) {
        return Range.EMPTY;
      } else {
        return new Range(upper.negate(), upper);
      }
    } else {
      CompInteger[] values = { low.divide(other.low), low.divide(other.high), high.divide(other.low), high.divide(other.high) };
      values = removeNaN(values);
      // if we reach here, values shouldn't be empty
      @SuppressWarnings("unchecked")
      CompInteger min = Collections.min(Arrays.asList(values));
      @SuppressWarnings("unchecked")
      CompInteger max = Collections.max(Arrays.asList(values));
      return new Range(min, max);
    }
  }

  /**
   * Note that shiftLeft can be in integer arithmetic or bit-vector logic
   * Change its semantics on demand when analyzing the CFA
   * @param offset
   * @return range
   */
  public Range shiftLeft(Range offset) {
    if(isEmpty() || offset.isEmpty()) {
      return Range.EMPTY;
    }
    CompInteger newLow, newHigh;
    CompInteger ltaintLow, ltaintHigh; // for the left operand
    Integer taintLow, taintHigh; // for the right operand

    if (ZERO.mayBeGreaterThan(offset)) {
      // instead of returning an empty set, we can keep the positive part of this interval
      if(offset.high.signum() < 0) {
        return Range.EMPTY;
      } else {
        taintLow = 0;
        taintHigh = offset.high.intValue();
      }
    } else {
      taintLow = offset.low.intValue();
      taintHigh = offset.high.intValue();
    }

    // FIXME: the left operand should not be negative, otherwise it is UB!
    if(ZERO.mayBeGreaterThan(this)) {
      if(high.signum() < 0) {
        return Range.EMPTY;
      } else {
        ltaintLow = CompInteger.zero;
        ltaintHigh = high;
      }
    } else {
      ltaintLow = low;
      ltaintHigh = high;
    }

    if(taintLow == null) {
      // too large to fit INT Type
      if(ltaintLow.signum() == 0) {
        newLow = CompInteger.zero;
      } else {
        newLow = CompInteger.infpos;
      }
    } else {
      newLow = ltaintLow.shiftLeft(taintLow.intValue());
    }

    if(taintHigh == null) {
      // too large to fit INT type
      if(ltaintHigh.signum() == 0) {
        newHigh = CompInteger.zero;
      } else {
        newHigh = CompInteger.infpos;
      }
    } else {
      newHigh = ltaintHigh.shiftLeft(taintHigh.intValue());
    }

    return new Range(newLow, newHigh);
  }

  public Range shiftRight(Range offset) {
    if(isEmpty() || offset.isEmpty()) {
      return Range.EMPTY;
    }
    CompInteger newLow, newHigh;
    CompInteger ltaintLow, ltaintHigh; // for the left operand
    Integer taintLow, taintHigh; // for the right operand

    if(ZERO.mayBeGreaterThan(offset)) {
      if(offset.high.signum() < 0) {
        return Range.EMPTY;
      } else {
        taintLow = 0;
        taintHigh = offset.high.intValue();
      }
    } else {
      taintLow = offset.low.intValue();
      taintHigh = offset.high.intValue();
    }

    // FIXME: the same as shiftLeft function
    if(ZERO.mayBeGreaterThan(this)) {
      if(high.signum() < 0) {
        return Range.EMPTY;
      } else {
        ltaintLow = CompInteger.zero;
        ltaintHigh = high;
      }
    } else {
      ltaintLow = low;
      ltaintHigh = high;
    }

    if(taintHigh == null) {
      newLow = CompInteger.zero;
    } else {
      newLow = ltaintLow.shiftRight(taintHigh.intValue());
    }

    if(taintLow == null) {
      newHigh = CompInteger.zero;
    } else {
      newHigh = ltaintHigh.shiftRight(taintLow.intValue());
    }

    return new Range(newLow, newHigh);

  }

  public Range negate() {
    if(isEmpty()) {
      return Range.EMPTY;
    }
    return new Range(high.negate(), low.negate());
  }

  @Override
  public String toString() {
    return "[" + (low == null ? "" :low) + "; " + (high == null ? "" : high) + "]";
  }

}

