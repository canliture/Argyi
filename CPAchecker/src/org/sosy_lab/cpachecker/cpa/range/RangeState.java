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
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.sosy_lab.common.collect.PathCopyingPersistentTreeMap;
import org.sosy_lab.common.collect.PersistentMap;
import org.sosy_lab.cpachecker.cfa.model.FunctionExitNode;
import org.sosy_lab.cpachecker.core.defaults.LatticeAbstractState;
import org.sosy_lab.cpachecker.core.interfaces.AbstractQueryableState;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.InvalidQueryException;
import org.sosy_lab.cpachecker.util.CheckTypesOfStringsUtil;

import com.google.common.base.Splitter;

public class RangeState implements Serializable, LatticeAbstractState<RangeState>, AbstractQueryableState {

  private static final long serialVersionUID = 2210917204244239540L;

  private static final Splitter propertySplitter = Splitter.on("<=").trimResults();

  // FIXME: we should not place point-to relation in range state because incremental property
  //        cannot be guaranteed
  private PersistentMap<String, Range> ranges;
  private PersistentMap<String, IntType> types;

  public RangeState() {
    ranges = PathCopyingPersistentTreeMap.of();
    types = PathCopyingPersistentTreeMap.of();
  }

  public RangeState(PersistentMap<String, Range> ranges, PersistentMap<String, IntType> types) {
    this.ranges = ranges;
    this.types = types;
  }

  /**
   * Get the range of the specified variable. If such variable does not exist, then we have no constraints on this variable
   * and its range should be unbounded.
   * @param varName
   * @return range
   */
  public Range getRange(String varName) {
    return ranges.containsKey(varName) ? ranges.get(varName) : Range.UNBOUND;
  }

  /**
   * Get the type of a variable in program. Notice that we don't change variable type in manipulating state.
   * @param varName
   * @return type
   */
  public IntType getType(String varName) {
    return types.containsKey(varName) ? types.get(varName) : IntType.UNKNOWN;
  }

  public boolean contains(String varName) {
    return ranges.containsKey(varName);
  }

  public RangeState addRange(String varName, Range range, IntType type) {
    if(range.isUnbound()) {
      removeRange(varName);
      return this;
    }

    if(!ranges.containsKey(varName) || !ranges.get(varName).equals(range)) {
      // then it is necessary to update range for this variable
      ranges = ranges.putAndCopy(varName, range);
      types = types.putAndCopy(varName, type);
    }

    return this;
  }

  public RangeState removeRange(String varName) {
    if(ranges.containsKey(varName)) {
      ranges = ranges.removeAndCopy(varName);
    }

    return this;
  }

  public void dropFrame(String pCalledFuncName) {
    for(String varName : ranges.keySet()) {
      if(varName.startsWith(pCalledFuncName + "::")) {
        removeRange(varName);
      }
    }
  }

  private IntType mergeType(IntType t1, IntType t2) {
    if(t1.equals(t2)) {
      return t1;
    }
    // if their types are inconsistent, we should merge them and take a sufficient merged type as the result
    return t1.mergeWith(t2);
  }

  @Override
  public RangeState join(RangeState reachedState) {
    boolean changed = false;
    PersistentMap<String, Range> new_range = PathCopyingPersistentTreeMap.of();
    PersistentMap<String, IntType> new_type = PathCopyingPersistentTreeMap.of();

    Range mergedRange;
    IntType newType;

    for(String varName : reachedState.ranges.keySet()) {
      if(ranges.containsKey(varName)) {
        mergedRange = getRange(varName).union(reachedState.getRange(varName));
        if(mergedRange != reachedState.getRange(varName)) {
          changed = true;
        }

        if(!mergedRange.isUnbound()) {
          new_range = new_range.putAndCopy(varName, mergedRange);
        }

        newType = mergeType(reachedState.getType(varName), getType(varName));
        if(!newType.equals(reachedState.getType(varName))) {
          changed = true;
          new_type = new_type.putAndCopy(varName, newType);
        } else {
          new_type = new_type.putAndCopy(varName, reachedState.getType(varName));
        }
      } else {
        // why we do this? Please refer to CPA algorithm in CAV 07' paper
        new_type = new_type.putAndCopy(varName, reachedState.getType(varName));
        changed = true; // why? because varName is defined in reached state, but is unbound in new state, thus in resulting state the range is also unbound
      }
    }

    if(changed) {
      return new RangeState(new_range, new_type);
    } else {
      return reachedState;
    }
  }

  @Override
  public boolean isLessOrEqual(RangeState reachedState) throws CPAException, InterruptedException {
    if(ranges.equals(reachedState.ranges)) {
      return true;
    }
    // why?
    // every variable is defined while so-called undefined one has unbound range in fact
    if(ranges.size() < reachedState.ranges.size()) {
      return false;
    }
    for(String varName : reachedState.ranges.keySet()) {
      if(!ranges.containsKey(varName) || !reachedState.getRange(varName).contains(getRange(varName))) {
        return false;
      }
    }

    return true;
  }

  public static RangeState copyOf(RangeState old) {
    return new RangeState(old.ranges, old.types);
  }

  public Map<String, Range> getRangeMap() {
    return ranges;
  }

  public RangeState rebuildStateAfterFunctionCall(final RangeState callState, final FunctionExitNode funcExitNode) {
    final RangeState rebuildState = RangeState.copyOf(callState);
    for(final String trackedVar : callState.ranges.keySet()) {
      if(!trackedVar.contains("::")) {
        rebuildState.removeRange(trackedVar);
      }
    }

    for(final String trackedVar : this.ranges.keySet()) {
      if(!trackedVar.contains("::")) {
        rebuildState.addRange(trackedVar, this.getRange(trackedVar), this.getType(trackedVar));
      } else if(funcExitNode.getEntryNode().getReturnVariable().isPresent() && funcExitNode.getEntryNode().getReturnVariable().get().getQualifiedName().equals(trackedVar)) {
        assert (!rebuildState.contains(trackedVar));
        if(this.contains(trackedVar)) {
          rebuildState.addRange(trackedVar, this.getRange(trackedVar), this.getType(trackedVar));
        }
      }
    }

    return rebuildState;
  }

  @Override
  public boolean equals(Object other) {
    if(this == other) {
      return true;
    }

    if(other == null || !getClass().equals(other.getClass())) {
      return false;
    }

    RangeState otherElement = (RangeState)other;
    if(ranges.size() != otherElement.ranges.size()) {
      return false;
    }

    for(Entry<String, Range> varName : ranges.entrySet()) {
      if(!otherElement.ranges.containsKey(varName.getKey()) || !otherElement.ranges.get(varName.getKey()).equals(varName.getValue())) {
        return false;
      }
    }

    return true;
  }

  @Override
  public int hashCode() {
    return ranges.hashCode();
  }

  @Override
  public String getCPAName() {
    // TODO Auto-generated method stub
    return "RangeAnalysis";
  }

  @Override
  public boolean checkProperty(String pProperty) throws InvalidQueryException {
    List<String> parts = propertySplitter.splitToList(pProperty);
    if(parts.size() == 2) {
      if(CheckTypesOfStringsUtil.isLong(parts.get(0))) {
        // then this string can be parsed into an integer
        BigInteger parseInt = new BigInteger(parts.get(0));
        CompInteger compInt = new CompInteger(parseInt);
        Range iv = getRange(parts.get(1));
        return (compInt.compareTo(iv.getLow()) <= 0);
      } else if(CheckTypesOfStringsUtil.isLong(parts.get(1))) {
        BigInteger parseInt = new BigInteger(parts.get(1));
        CompInteger compInt = new CompInteger(parseInt);
        Range iv = getRange(parts.get(0));
        return (iv.getHigh().compareTo(compInt) <= 0);
      } else {
        Range iv1 = getRange(parts.get(0));
        Range iv2 = getRange(parts.get(1));
        return (iv1.contains(iv2));
      }
    } else if(parts.size() == 3) {
      if(CheckTypesOfStringsUtil.isLong(parts.get(0)) && CheckTypesOfStringsUtil.isLong(parts.get(2))) {
        CompInteger value1 = new CompInteger(new BigInteger(parts.get(0)));
        CompInteger value2 = new CompInteger(new BigInteger(parts.get(2)));
        Range iv = getRange(parts.get(1));
        return (value1.compareTo(iv.getLow()) <= 0 && iv.getHigh().compareTo(value2) <= 0);
      }
    }

    return false;
  }

  @Override
  public Object evaluateProperty(String pProperty) throws InvalidQueryException {
    return Boolean.valueOf(checkProperty(pProperty));
  }

  @Override
  public void modifyProperty(String pModification) throws InvalidQueryException {
    throw new InvalidQueryException("The modifying query " + pModification + " is an unsupported operation in " + getCPAName() + "!");
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("[\n");

    for(Map.Entry<String, Range> entry : ranges.entrySet()) {
      String key = entry.getKey();
      sb.append(" <");
      sb.append(key);
      sb.append(" = ");
      sb.append(entry.getValue());
      sb.append(" :: ");
      sb.append(getType(key));
      sb.append(">\n");
    }

    return sb.append("] size->  ").append(ranges.size()).toString();
  }

}
