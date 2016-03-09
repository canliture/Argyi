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


public class FixGuide {

  /*
   * This data structure defines the fix guide, i.e. how to insert
   * explicit conversion for pointers and sanity check function for
   * integer expressions.
   *
   * Type specifier fix in declaration is not included in this structure,
   * since the fix depends on the result from Max-SMT solving
   */

  private boolean intFlag;
  private boolean ptrFlag;
  private boolean sanityCheckFlag;
  private IntType baseType;
  private int referenceLevel;
  private String target;

  public FixGuide() {
    intFlag = false;
    ptrFlag = false;
    sanityCheckFlag = false;
    baseType = new IntType();
    referenceLevel = -1;
    target = "";
  }

  public FixGuide(boolean intFlag, boolean ptrFlag, boolean scFlag, IntType baseType, int refLevel, String target) {
    this.intFlag = intFlag;
    this.ptrFlag = ptrFlag;
    this.sanityCheckFlag = scFlag;
    this.baseType = baseType;
    this.referenceLevel = refLevel;
    this.target = target;
  }

  public boolean isIntVar() {
    return this.intFlag;
  }

  public boolean isPointer() {
    return this.ptrFlag;
  }

  public boolean needSanityCheck() {
    return this.sanityCheckFlag;
  }

  public String getTarget() {
    if(!this.ptrFlag || referenceLevel == -1) {
      return null;
    } else {
      if(target.isEmpty()) {
        return null;
      } else {
        return target;
      }
    }
  }

  public int getRefLevel() {
    return this.referenceLevel;
  }

  public IntType getBaseType() {
    return this.baseType;
  }

  public FixGuide merge(FixGuide other) {
    if(this.intFlag == other.intFlag && this.ptrFlag == other.ptrFlag && this.sanityCheckFlag == other.sanityCheckFlag && this.referenceLevel == other.referenceLevel) {
      IntType newType = this.baseType.mergeWith(other.baseType);
      if(this.baseType.getSign() || other.baseType.getSign()) {
        if(!newType.getSign()) {
          newType = newType.flipSign();
        }
      }
      if(this.target.equals(other.target)) {
        return new FixGuide(intFlag, ptrFlag, sanityCheckFlag, newType, referenceLevel, target);
      } else {
        return this;
      }
    }
    return this;
  }

}
