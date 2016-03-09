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

import java.util.ArrayList;
import java.util.List;

import org.sosy_lab.common.Triple;
import org.sosy_lab.common.collect.PersistentList;

public class Constraint {

  /**
   * list of predicates. For a Constraint object, we disjunct each of them as a clause
   */
  private List<Triple<String, String, String>> predicates;
  private boolean softness;

  public Constraint() {
    predicates = new ArrayList<>();
    softness = false; // false means this constraint must be satisfied
  }

  public void addConstraint(String op, String pred1, String pred2) {
    predicates.add(Triple.of(op, pred1, pred2));
  }

  public void addConstraint(String op, PersistentList<String> preds, String pred2) {
    for(String pred : preds) {
      predicates.add(Triple.of(op, pred, pred2));
    }
  }

  public boolean getSoftness() {
    return softness;
  }

  public void setSoftness(boolean soft) {
    this.softness = soft;
  }

  public int getPredicateSize() {
    return predicates.size();
  }

  public Triple<String, String, String> getPredicate(int index) {
    if(index >= this.predicates.size()) {
      return null;
    }
    return predicates.get(index);
  }

}
