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

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.sosy_lab.common.Triple;
import org.sosy_lab.common.collect.PersistentList;

import com.google.common.base.Joiner;

public class ConstraintGenerator {

  private String filePath;
  private Set<String> varList;
  private List<String> declarations;
  private List<String> assertions;
  private List<String> restrictions;

  private static final String typeDeclaration = "(declare-datatypes () ((I CHAR UCHAR SHORT USHORT INT UINT LINT ULINT LLINT ULLINT OVERLONG)))";
  private static final String predDefinition = "(define-fun P ((x!1 I) (x!2 I)) Bool (ite (and (= x!1 CHAR) (= x!2 CHAR)) true (ite (and (= x!1 UCHAR) (= x!2 UCHAR)) true (ite (and (= x!1 SHORT) (= x!2 CHAR)) true (ite (and (= x!1 SHORT) (= x!2 UCHAR)) true (ite (and (= x!1 SHORT) (= x!2 SHORT)) true (ite (and (= x!1 USHORT) (= x!2 UCHAR)) true (ite (and (= x!1 USHORT) (= x!2 USHORT)) true (ite (and (= x!1 INT) (= x!2 CHAR)) true (ite (and (= x!1 INT) (= x!2 UCHAR)) true (ite (and (= x!1 INT) (= x!2 SHORT)) true (ite (and (= x!1 INT) (= x!2 USHORT)) true (ite (and (= x!1 INT) (= x!2 INT)) true (ite (and (= x!1 UINT) (= x!2 UCHAR)) true (ite (and (= x!1 UINT) (= x!2 USHORT)) true (ite (and (= x!1 UINT) (= x!2 UINT)) true (ite (and (= x!1 LINT) (= x!2 CHAR)) true (ite (and (= x!1 LINT) (= x!2 UCHAR)) true (ite (and (= x!1 LINT) (= x!2 SHORT)) true (ite (and (= x!1 LINT) (= x!2 USHORT)) true (ite (and (= x!1 LINT) (= x!2 INT)) true (ite (and (= x!1 LINT) (= x!2 UINT)) true (ite (and (= x!1 LINT) (= x!2 LINT)) true (ite (and (= x!1 ULINT) (= x!2 UCHAR)) true (ite (and (= x!1 ULINT) (= x!2 USHORT)) true (ite (and (= x!1 ULINT) (= x!2 UINT)) true (ite (and (= x!1 ULINT) (= x!2 ULINT)) true (ite (and (= x!1 LLINT) (= x!2 CHAR)) true (ite (and (= x!1 LLINT) (= x!2 UCHAR)) true (ite (and (= x!1 LLINT) (= x!2 SHORT)) true (ite (and (= x!1 LLINT) (= x!2 USHORT)) true (ite (and (= x!1 LLINT) (= x!2 INT)) true (ite (and (= x!1 LLINT) (= x!2 UINT)) true (ite (and (= x!1 LLINT) (= x!2 LINT)) true (ite (and (= x!1 LLINT) (= x!2 LLINT)) true (ite (and (= x!1 ULLINT) (= x!2 UCHAR)) true (ite (and (= x!1 ULLINT) (= x!2 USHORT)) true (ite (and (= x!1 ULLINT) (= x!2 UINT)) true (ite (and (= x!1 ULLINT) (= x!2 ULINT)) true (ite (and (= x!1 ULLINT) (= x!2 ULLINT)) true (ite (= x!1 OVERLONG) true false)))))))))))))))))))))))))))))))))))))))))";
  private static final Set<String> types = new HashSet<>(Arrays.asList("CHAR", "UCHAR", "SHORT", "USHORT", "INT", "UINT", "LINT", "ULINT", "LLINT", "ULLINT", "OVERLONG"));

  // format control
  private static final String assertHard = "(assert %s)";
  private static final String assertSoft = "(assert-soft %s :weight %s)";
  private static final String predDP = "dp";
  private static final String predP = "p";
  private static final String predEq = "eq";
  private static final String predPAssert = "(P %s %s)";
  private static final String predEqAssert = "(= %s %s)";
  private static final String or = "(or %s)"; // the first parameter is the clause list for disjunctive connection
  private static final String varDecl = "(declare-fun %s () I)";
  private static final String checkSat = "(check-sat)";
  private static final String getModel = "(get-model)";
  private static final String exit = "(exit)";
  private static final String NOTOVERLONG = "(not (= %s OVERLONG))";
  private static Joiner joiner = Joiner.on(" ");

  // tunable parameter, about constraint weights
  private static final int pweight = 1; // normal: 80
  private static final int pdweight = 1; // normal: 10
  private static final int eqweight = 2; // normal: 1

  public ConstraintGenerator(String filePath) {
    this.filePath = filePath;
    this.varList = new HashSet<>();
    this.declarations = new ArrayList<>();
    this.assertions = new ArrayList<>();
    this.restrictions = new ArrayList<>();
  }

  public void generateConstraints(PersistentList<Constraint> constraints) {
    // STEP 1: read the constraints and generate assertions
    for(Constraint assertion : constraints) {
      boolean isEq = false;
      boolean isDecl = false;
      boolean soft = assertion.getSoftness();
      int size = assertion.getPredicateSize();
      if(size <= 0) {
        continue;
      }
      List<String> clauses = new ArrayList<>();
      for(int i = 0; i < size; i++) {
        Triple<String, String, String> pair = assertion.getPredicate(i);
        String leftName = postProcess(pair.getSecond());
        String rightName = postProcess(pair.getThird());
        String pred = pair.getFirst();
        if(pred.equals(predP)) {
          clauses.add(String.format(predPAssert, leftName, rightName));
        } else if(pred.equals(predEq)) {
          isEq = true;
          clauses.add(String.format(predEqAssert, leftName, rightName));
        } else if(pred.equals(predDP)) {
          isDecl = true;
          clauses.add(String.format(predPAssert, leftName, rightName));
        }
      }
      if(size == 1) {
        if(soft) {
          if(isEq) {
            assertions.add(String.format(assertSoft, clauses.get(0), String.valueOf(eqweight)));
          } else if(isDecl) {
            assertions.add(String.format(assertSoft, clauses.get(0), String.valueOf(pdweight)));
          } else {
            assertions.add(String.format(assertSoft, clauses.get(0), String.valueOf(pweight)));
          }
        } else {
          assertions.add(String.format(assertHard, clauses.get(0)));
        }
      } else {
        // size > 1
        String orExpr = String.format(or, joiner.join(clauses));
        if(soft) {
          assertions.add(String.format(assertSoft, orExpr, String.valueOf(pweight)));
        } else {
          assertions.add(String.format(assertHard, orExpr));
        }
      }
    }

    // STEP 2: generate variable declarations
    // STEP 3: generate restrictions that all variables should not be of type OVERLONG
    for(String varName : varList) {
      declarations.add(String.format(varDecl, varName));
      restrictions.add(String.format(assertHard, String.format(NOTOVERLONG, varName)));
    }

    // STEP 4: generate file by the given path
    // **NOTE: file path should be a specific .smt2 file
    try {
      BufferedWriter bout = new BufferedWriter(new FileWriter(filePath));
      // STEP 4.1: datatype and predicate definition
      bout.write(typeDeclaration);
      bout.newLine();
      bout.write(predDefinition);
      bout.newLine();
      bout.flush();
      // STEP 4.2: variable declarations
      for(String declaration : declarations) {
        bout.write(declaration);
        bout.newLine();
      }
      bout.flush();
      // STEP 4.3: assertions
      for(String assertion : assertions) {
        bout.write(assertion);
        bout.newLine();
      }
      bout.flush();
      // STEP 4.4: restrictions
      for(String restriction : restrictions) {
        bout.write(restriction);
        bout.newLine();
      }
      bout.flush();
      // STEP 4.5: some necessary commands
      bout.write(checkSat);
      bout.newLine();
      bout.write(getModel);
      bout.newLine();
      bout.write(exit);
      bout.close();
    } catch(IOException e) {
      System.err.println("Invalid specified fila path for .smt2 file!");
    }
  }

  /**
   * Since qualified name contains "::" which cannot be a part of SMTLIB2 identifier, we replace all "::" with "!!" admitted by Z3.
   * "!!" is not permitted in C identifier either!
   * PS: we also implement variable record in this function.
   *
   * @param name to be processed
   * @return processed name
   */
  private String postProcess(String name) {
    if(!types.contains(name)) {
      String newName = name.replace("::", "!!");
      varList.add(newName);
      return newName;
    }
    return name;
  }

}
