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

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.sosy_lab.common.Pair;
import org.sosy_lab.common.collect.PathCopyingPersistentTreeMap;
import org.sosy_lab.common.collect.PersistentLinkedList;
import org.sosy_lab.common.collect.PersistentList;
import org.sosy_lab.common.collect.PersistentMap;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.ast.FileLocation;
import org.sosy_lab.cpachecker.cfa.ast.c.CArraySubscriptExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CAssignment;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression.BinaryOperator;
import org.sosy_lab.cpachecker.cfa.ast.c.CCastExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CCharLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFieldReference;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializer;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CIntegerLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CLeftHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CParameterDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CPointerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CRightHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CRightHandSideVisitor;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CTypeIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CTypeIdExpression.TypeIdOperator;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression.UnaryOperator;
import org.sosy_lab.cpachecker.cfa.ast.c.CVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.DefaultCExpressionVisitor;
import org.sosy_lab.cpachecker.cfa.model.BlankEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.FunctionExitNode;
import org.sosy_lab.cpachecker.cfa.model.MultiEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CAssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionReturnEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionSummaryEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CReturnStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.cfa.types.Type;
import org.sosy_lab.cpachecker.cfa.types.c.CEnumType.CEnumerator;
import org.sosy_lab.cpachecker.cfa.types.c.CFunctionType;
import org.sosy_lab.cpachecker.cfa.types.c.CNumericTypes;
import org.sosy_lab.cpachecker.cfa.types.c.CPointerType;
import org.sosy_lab.cpachecker.cfa.types.c.CSimpleType;
import org.sosy_lab.cpachecker.cfa.types.c.CStorageClass;
import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.cfa.types.c.CTypedefType;
import org.sosy_lab.cpachecker.core.defaults.ForwardingTransferRelation;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCCodeException;

import com.google.common.base.Optional;

@Options(prefix="cpa.range")
public class RangeTransferRelation extends ForwardingTransferRelation<Collection<RangeState>, RangeState, Precision> {

  @Option(secure=true, description="split intervals")
  private boolean splitIntervals = false;

  /**
   * This mapping stores pointer relation of identifiers.
   * NOTE: in pointRel we have a special virtual node "!!notid".
   * First, no variable can have the same name as this virtual node, by C11 standard;
   * Second, operation on pointer is dynamic, if we specify a pointer to point to array subscript or field reference,
   * this pointer will be actually specified to virtual node and the structure of other pointers that point to this pointer keeps.
   * If this pointer points to an integer variable again, we can have correct pointer-to relation.
   *
   * UPDATE: all nodes in point-to graph correspond to left-hand expressions, including variables, array subscripts, field references
   * Although pointer expression is also lvalue, it can be replaced with the three types of expressions listed above.
   */
  private static PersistentMap<String, String> pointRel;
  private static String dumbnode = "!!notid";

  /**
   * This counter is for generating names for intermediate variables only
   */
  private static long nameCounter;
  private static String intermPrefix = "!INTERM_";

  /**
   * These special reference levels are for left-shift/right-shift operations
   */
  public static final int shiftLeftLevel = -10;
  public static final int shiftRightLevel = -11;

  /**
   * They are total fix metadata. Metadata yielded from ExpressionValueVistor will be appended to this data structure.
   */
  private static PersistentList<Constraint> totalConstraints;
  private static PersistentMap<String, FileLocation> totalName2Loc;
  private static PersistentMap<FileLocation, FixGuide> totalLoc2Guide;

  /**
   * This structure records all integer variables for pointer analysis
   */
  private static Set<String> totalIntNames;

  // File processing
  private static final String predDP = "dp";
  private static final String predP = "p";
  private static final String predEq = "eq";


  public RangeTransferRelation(Configuration config) throws InvalidConfigurationException {
    config.inject(this);

    // TODO: this is the constructor of transfer relation. Any initialization work can be done here
    pointRel = PathCopyingPersistentTreeMap.of();
    nameCounter = 0;

    totalConstraints = PersistentLinkedList.of();
    totalName2Loc = PathCopyingPersistentTreeMap.of();
    totalLoc2Guide = PathCopyingPersistentTreeMap.of();

    totalIntNames = new HashSet<>();
  }

  public PersistentList<Constraint> getConstraints() {
    return totalConstraints;
  }

  public PersistentMap<String, FileLocation> getName2Loc() {
    return totalName2Loc;
  }

  public PersistentMap<FileLocation, FixGuide> getLoc2Guide() {
    return totalLoc2Guide;
  }

  private PersistentMap<FileLocation, FixGuide> insertNewFixGuide(FileLocation loc, FixGuide guide) {
    FixGuide prev = totalLoc2Guide.get(loc);
    if(prev != null) {
      // we should merge existing solution with the new coming one
      FixGuide nouveau = prev.merge(guide);
      return totalLoc2Guide.putAndCopy(loc, nouveau);
    } else {
      return totalLoc2Guide.putAndCopy(loc, guide);
    }
  }

  @Override
  protected Collection<RangeState> postProcessing(Collection<RangeState> successors) {
    return new HashSet<>(successors);
  }

  @Override
  protected Collection<RangeState> handleBlankEdge(BlankEdge cfaEdge) {
    RangeState newState = state;
    if(cfaEdge.getSuccessor() instanceof FunctionExitNode) {
      newState = RangeState.copyOf(state);
      newState.dropFrame(functionName);
    }
    return soleSuccessor(newState);
  }

  @Override
  protected Collection<RangeState> handleMultiEdge(MultiEdge cfaEdge) throws CPATransferException {
    return super.handleMultiEdgeReturningCollection(cfaEdge);
  }

  @Override
  protected Collection<RangeState> handleFunctionReturnEdge(CFunctionReturnEdge cfaEdge, CFunctionSummaryEdge sumEdge, CFunctionCall funcExpr, String callerFuncName) throws UnrecognizedCCodeException {
    // (1) constraint: if funcExpr is assignment, we need to add a constraint to prevent truncation
    // (2) name2Loc: none
    // (3) fix2Guide: none (return expression should be enclosed with sanitization routine, but we add check when handling ReturnStatementEdge.)

    RangeState newState = RangeState.copyOf(state);
    Optional<CVariableDeclaration> retVar = sumEdge.getFunctionEntry().getReturnVariable();
    if(retVar.isPresent()) {
      // the return variable actually exists
      // after exiting this function, we should do some clean work
      newState.removeRange(retVar.get().getQualifiedName());
    }

    if(funcExpr instanceof CFunctionCallAssignmentStatement) {
      CFunctionCallAssignmentStatement funcExp = (CFunctionCallAssignmentStatement)funcExpr;
      if(state.contains(retVar.get().getQualifiedName())) {
        // then we should update the left-hand variable with return value of this function call
        // NOTE: since return variable is defined in range map, return type of this function must be integer
        Range retRange = state.getRange(retVar.get().getQualifiedName());
        Type t = cfaEdge.getPredecessor().getEntryNode().getFunctionDefinition().getType().getReturnType();
        // canonical type: type specifier without storage specification such as extern, static
        CType ct = ((CType)t).getCanonicalType();
        IntType thisType = evaluateType(ct);
        addRange(newState, funcExp.getLeftHandSide(), retRange, thisType);
        String leftTarget = findLeftHandNode(funcExp.getLeftHandSide());
        if(leftTarget != null) {
          Constraint assertion = new Constraint();
          assertion.setSoftness(true);
          assertion.addConstraint(predP, leftTarget, thisType.toOBJString());
          totalConstraints = totalConstraints.with(assertion);
        }
      } else {
        // _ret is not in the RangeState
        Type t = cfaEdge.getPredecessor().getEntryNode().getFunctionDefinition().getType().getReturnType();
        CType ct = ((CType)t).getCanonicalType();
        if(ct instanceof CPointerType) {
          String retName = retVar.get().getQualifiedName();
          String leftName = findLeftHandNode(funcExp.getLeftHandSide());
          String successor = pointRel.get(retName);
          if(leftName != null && successor != null) {
            // we can assign this to the left hand side
            pointRel = pointRel.putAndCopy(leftName, successor);
          } else if(leftName != null) {
            pointRel = pointRel.putAndCopy(leftName, dumbnode);
          }
        } else {
          // nothing to do
        }
      }
    } else if(funcExpr instanceof CFunctionCallStatement) {
      // nothing to do
    } else {
      // this is an unexpected case
      throw new UnrecognizedCCodeException("on function return", edge, funcExpr);
    }

    return soleSuccessor(newState);
  }

  @Override
  protected Collection<RangeState> handleFunctionCallEdge(CFunctionCallEdge callEdge, List<CExpression> args, List<CParameterDeclaration> params, String calledFuncName) throws UnrecognizedCCodeException {
    // constraint: for a parameter variable, its type should be larger than that defined in function signature
    // name2Loc: required. If a parameter variable requires elevation, we should rename parameter variable and declare a new variable of new type
    // fix2Guide: required. Each argument should be in the range of corresponding parameter type

    // TODO: variadic function support [now unsupported]
    assert (params.size() == args.size()) : "variadic function is temporarily unsupported.";
    RangeState newState = RangeState.copyOf(state);

    for(int i = 0; i < args.size(); i++) {
      ExpressionInfo eval = evaluateExpression(state, args.get(i));
      Range argRange = eval.getRange();
      CType paramOrigType = params.get(i).getType().getCanonicalType();
      IntType paramType = evaluateType(paramOrigType);
      Range typeRange = paramType.getTypeRange();
      if(!typeRange.contains(argRange)) {
        // since we can ensure the value of argument does not exceed type restriction
        argRange = argRange.intersect(typeRange);
      }

      if(!paramType.isNotInt()) {
        // ONLY integer variables are added into range state!
        String fName = params.get(i).getQualifiedName();
        newState.addRange(fName, argRange, paramType);

        // Don't forget to record this name of integer
        totalIntNames.add(fName);

        // then, we output fix guide metadata here
        // PART I: constraint
        // I.1 each parameter variable should have larger type than pre-defined one
        Constraint assertion = new Constraint();
        assertion.setSoftness(true);
        assertion.addConstraint(predP, fName, paramType.toOBJString());
        totalConstraints = totalConstraints.with(assertion);
        Constraint assertion2 = new Constraint();
        assertion2.setSoftness(true);
        // but we don't want to change types so much
        assertion2.addConstraint(predEq, fName, paramType.toOBJString());
        totalConstraints = totalConstraints.with(assertion2);
        // PART II: name2Loc
        totalName2Loc = totalName2Loc.putAndCopy(fName, params.get(i).getFileLocation());
        // PART III: argument should be enclosed with sanitization routine
        FixGuide guide = new FixGuide(true, false, true, paramType, -1, "");
        totalLoc2Guide = insertNewFixGuide(args.get(i).getFileLocation(), guide);
      } else if(paramOrigType instanceof CPointerType) {
        // it is OK even if this is not an pointer relation
        // well-typeness is guaranteed anyway
        String fName = params.get(i).getQualifiedName();
        String target = findNextNode(args.get(i));
        if(target == null) {
          pointRel = pointRel.putAndCopy(fName, dumbnode);
        } else {
          pointRel = pointRel.putAndCopy(fName, target);
        }

      } // otherwise, we do nothing
    }
    return soleSuccessor(newState);
  }

  @Override
  protected Collection<RangeState> handleReturnStatementEdge(CReturnStatementEdge retEdge) throws UnrecognizedCCodeException {
    // (1) constraints: none
    // (2) name2Loc: none (return variable is virtual and it shouldn't be elevated)
    // (3) loc2Guide: required. Return expression should not violate the constraints by return type

    RangeState newState = RangeState.copyOf(state);
    newState.dropFrame(functionName);

    if(retEdge.asAssignment().isPresent()) {
      // return expression exists
      CAssignment ass = retEdge.asAssignment().get();
      String retvar = ((CIdExpression)ass.getLeftHandSide()).getDeclaration().getQualifiedName();

      // get the return type from the signature
      Type rawRetType = retEdge.getSuccessor().getEntryNode().getFunctionDefinition().getType().getReturnType();
      // obviously, this type should be a CType
      IntType retType = evaluateType((CType)rawRetType);
      // evaluate expression statement now
      ExpressionInfo evalRet = evaluateExpression(state, ass.getRightHandSide());
      Range retRange = evalRet.getRange();
      if(!retType.isNotInt()) {
        // ONLY integer return variable is appended into range state
        Range typeRange = retType.getTypeRange();
        if(!typeRange.contains(retRange)) {
          retRange = retRange.intersect(typeRange);
          // return expression seems to have risks of overflow
          FixGuide guide = new FixGuide(true, false, true, retType, -1, "");
          totalLoc2Guide = insertNewFixGuide(ass.getRightHandSide().getFileLocation(), guide);
        }
        newState.addRange(retvar, retRange, retType);
      }
    }

    return soleSuccessor(newState);
  }

  @Override
  protected Collection<RangeState> handleAssumption(CAssumeEdge cfaEdge, CExpression condExpr, boolean truthValue) throws UnrecognizedCCodeException {
    // (1) constraints: none
    // (2) name2Loc: none
    // (3) loc2Guide: since we parse the logical expression independently, we have to address explicit type conversions
    RangeState newState = RangeState.copyOf(state);
    BinaryOperator op = ((CBinaryExpression)condExpr).getOperator();
    CExpression op1 = ((CBinaryExpression)condExpr).getOperand1();
    CExpression op2 = ((CBinaryExpression)condExpr).getOperand2();
    if(!truthValue) {
      op = negateOperator(op);
    }
    ExpressionInfo info1 = evaluateExpression(newState, op1);
    ExpressionInfo info2 = evaluateExpression(newState, op2);
    Range r1 = info1.getRange();
    Range r2 = info2.getRange();
    IntType t1 = info1.getType();
    IntType t2 = info2.getType();
    Range mergedRange = r1.union(r2);
    IntType updType = new IntType(mergedRange); // this type is used for explicit type conversion
    if(updType.isNotOverlong()) {
      // otherwise, we cannot handle this
      FixGuide guide = new FixGuide(true, false, false, updType, -1, "");
      // since operators in rational expression can be constructed by CFA, we just discard fix guide
      // corresponding to empty locations
      FileLocation loc1 = op1.getFileLocation();
      if(!loc1.equals(FileLocation.DUMMY)) {
        totalLoc2Guide = insertNewFixGuide(op1.getFileLocation(), guide);
      }
      FileLocation loc2 = op2.getFileLocation();
      if(!loc2.equals(FileLocation.DUMMY)) {
        totalLoc2Guide = insertNewFixGuide(op2.getFileLocation(), guide);
      }
    }

    // try to refine range analysis by predicate
    Range nr1, nr2;
    switch(op) {
    case LESS_THAN: {
      // if either of two ranges is empty?
      // in order to prevent unexpected analysis termination, we should discuss these cases
      if(r2.equals(Range.EMPTY)) {
        return noSuccessors();
      } else if(r1.equals(Range.EMPTY)) {
        // nothing should be changed
        return soleSuccessor(newState);
      }
      nr1 = r1.limitUpperBoundBy(r2.minus(1L));
      nr2 = r2.limitLowerBoundBy(r1.plus(1L));
      if(nr1.equals(Range.EMPTY) || nr2.equals(Range.EMPTY)) {
        // if we use limited loop unrolling, return empty successor set could lead to
        // low code coverage
        return soleSuccessor(newState);
        //return noSuccessors(); // unsatisfiable
      } else {
        addRange(newState, op1, nr1, t1);
        addRange(newState, op2, nr2, t2);
        return soleSuccessor(newState);
      }
    }
    case LESS_EQUAL: {
      if(r1.equals(Range.EMPTY)) {
        return soleSuccessor(newState);
      } else if(r2.equals(Range.EMPTY)) {
        return noSuccessors();
      }
      nr1 = r1.limitUpperBoundBy(r2);
      nr2 = r2.limitLowerBoundBy(r1);
      if(nr1.equals(Range.EMPTY) || (nr2.equals(Range.EMPTY))) {
        return soleSuccessor(newState);
        //return noSuccessors();
      } else {
        addRange(newState, op1, nr1, t1);
        addRange(newState, op2, nr2, t2);
        return soleSuccessor(newState);
      }
    }
    case GREATER_THAN: {
      if(r1.equals(Range.EMPTY)) {
        return noSuccessors();
      } else if(r2.equals(Range.EMPTY)) {
        return soleSuccessor(newState);
      }
      nr1 = r1.limitLowerBoundBy(r2.plus(1L));
      nr2 = r2.limitUpperBoundBy(r1.minus(1L));
      if(nr1.equals(Range.EMPTY) || (nr2.equals(Range.EMPTY))) {
        return soleSuccessor(newState);
        //return noSuccessors();
      } else {
        addRange(newState, op1, nr1, t1);
        addRange(newState, op2, nr2, t2);
        return soleSuccessor(newState);
      }
    }
    case GREATER_EQUAL: {
      if(r2.equals(Range.EMPTY)) {
        return soleSuccessor(newState);
      } else if(r1.equals(Range.EMPTY)) {
        return noSuccessors();
      }
      nr1 = r1.limitLowerBoundBy(r2);
      nr2 = r2.limitUpperBoundBy(r1);
      if(nr1.equals(Range.EMPTY) || (nr2.equals(Range.EMPTY))) {
        return soleSuccessor(newState);
        //return noSuccessors();
      } else {
        addRange(newState, op1, nr1, t1);
        addRange(newState, op2, nr2, t2);
        return soleSuccessor(newState);
      }
    }
    case EQUALS: {
      boolean r1Empty = r1.equals(Range.EMPTY);
      boolean r2Empty = r2.equals(Range.EMPTY);
      if(r1Empty && r2Empty) {
        return soleSuccessor(newState);
      } else if(r1Empty || r2Empty) {
        return noSuccessors();
      }
      nr1 = r1.intersect(r2);
      nr2 = r2.intersect(r1);
      if(nr1.equals(Range.EMPTY) || (nr2.equals(Range.EMPTY))) {
        return soleSuccessor(newState);
        //return noSuccessors();
      } else {
        addRange(newState, op1, nr1, t1);
        addRange(newState, op2, nr2, t2);
        return soleSuccessor(newState);
      }
    }
    case NOT_EQUALS: {
      boolean r1Empty = r1.equals(Range.EMPTY);
      boolean r2Empty = r2.equals(Range.EMPTY);
      if(r1Empty && r2Empty) {
        return noSuccessors();
      } else if(r1Empty || r2Empty) {
        return soleSuccessor(newState);
      }
      // FIXME: remove interval split for now since it has some defects
      return soleSuccessor(newState);
//      if(r2.getLow().equals(r2.getHigh())) {
//        // the right-hand side is a singleton
//        return splitRange(newState, op1, r1, r2, t1);
//      } else if(r1.getLow().equals(r1.getHigh())) {
//        return splitRange(newState, op2, r2, r1, t2);
//      } else {
//        // no new information to refine ranges
//        return soleSuccessor(newState);
//      }
    }
    default:
      throw new UnrecognizedCCodeException("unexpected operator in assumption", edge, condExpr);
    }
  }

  @SuppressWarnings("unused")
  private Collection<RangeState> splitRange(RangeState newState, CExpression expr, Range r, Range splitPoint, IntType t) {
    assert (splitPoint.getLow().equals(splitPoint.getHigh()));

    Collection<RangeState> successors = new ArrayList<>();
    Range pr1 = r.intersect(Range.createUpperBoundedRange(splitPoint.getLow().subtract(CompInteger.one)));
    Range pr2 = r.intersect(Range.createUpperBoundedRange(splitPoint.getLow().add(CompInteger.one)));
    if(pr1.isEmpty() && pr2.isEmpty()) {
      return noSuccessors();
    }

    if(!pr1.isEmpty()) {
      RangeState state2 = RangeState.copyOf(newState);
      addRange(state2, expr, pr1, t);
      successors.add(state2);
    }
    if(!pr2.isEmpty()) {
      RangeState state3 = RangeState.copyOf(newState);
      addRange(state3, expr, pr2, t);
      successors.add(state3);
    }

    return successors;
  }

  private static BinaryOperator negateOperator(BinaryOperator op) {
    switch(op) {
    case EQUALS: return BinaryOperator.NOT_EQUALS;
    case NOT_EQUALS: return BinaryOperator.EQUALS;
    case LESS_THAN: return BinaryOperator.GREATER_EQUAL;
    case LESS_EQUAL: return BinaryOperator.GREATER_THAN;
    case GREATER_THAN: return BinaryOperator.LESS_EQUAL;
    case GREATER_EQUAL: return BinaryOperator.LESS_THAN;
    default: return op; // since we don't know how to negate it
    }
  }

  @Override
  protected Collection<RangeState> handleDeclarationEdge(CDeclarationEdge declEdge, CDeclaration decl) throws UnrecognizedCCodeException {
    RangeState newState = RangeState.copyOf(state);
    if(declEdge.getDeclaration() instanceof CVariableDeclaration) {
      CVariableDeclaration vardecl = (CVariableDeclaration)declEdge.getDeclaration();
      String qName = vardecl.getQualifiedName();
      if(vardecl.getType() instanceof CPointerType) {
        // a non-integer pointer can also point to integer, but the type is not compatible
        CInitializer init = vardecl.getInitializer();
        if(init instanceof CInitializerExpression) {
          CExpression initExpr = ((CInitializerExpression)init).getExpression();
          evaluateExpression(newState, initExpr);
          // initExpr must be a pointer expression
          String target = findNextNode(initExpr);
          if(target == null) {
            pointRel = pointRel.putAndCopy(qName, dumbnode);
          } else {
            pointRel = pointRel.putAndCopy(qName, target);
          }
        } else {
          // this pointer is not initialized
          pointRel = pointRel.putAndCopy(qName, dumbnode);
        }
        return soleSuccessor(newState);
      }
      // otherwise, this is not a pointer
      CInitializer init = vardecl.getInitializer();
      if(init instanceof CInitializerExpression) {
        CExpression initExpr = ((CInitializerExpression) init).getExpression();
        ExpressionInfo info = evaluateExpression(newState, initExpr);
        Range initRange = info.getRange();

        // whether this declaration is of integer type
        CType declType = vardecl.getType();
        IntType declIntType = evaluateType(declType);
        if(!declIntType.isNotInt()) {
          // integer type
          totalIntNames.add(qName);
          // constraint PART I: type of this variable should have a larger type than the declared one
          Constraint assertion = new Constraint();
          assertion.addConstraint(predDP, qName, declIntType.toOBJString());
          assertion.setSoftness(true);
          totalConstraints = totalConstraints.with(assertion);
          Constraint assertion3 = new Constraint();
          assertion3.addConstraint(predEq, qName, declIntType.toOBJString());
          assertion3.setSoftness(true);
          totalConstraints = totalConstraints.with(assertion3);
          // constraint PART II: type of this variable should be larger than the range of initializer
          IntType updType = new IntType(initRange);
          Constraint assertion2 = new Constraint();
          assertion2.addConstraint(predP, qName, updType.toOBJString());
          assertion2.setSoftness(true);
          totalConstraints = totalConstraints.with(assertion2);
          // name2Loc: add location information for this declaration
          totalName2Loc = totalName2Loc.putAndCopy(qName, vardecl.getFileLocation());
          // most importantly, add the range information of this variable
          newState.addRange(qName, initRange, info.getType());
        } else {
          return soleSuccessor(newState);
        }
      } else {
        // no initializer
        CType declType = vardecl.getType();
        IntType declIntType = evaluateType(declType);
        Range initRange = Range.UNBOUND;
        if(!declIntType.isNotInt()) {
          // integer type
          totalIntNames.add(qName);
          CStorageClass storcls = vardecl.getCStorageClass();
          if(storcls == CStorageClass.EXTERN) {
            // add a hard constraint: extern int declaration cannot be altered
            Constraint assertion = new Constraint();
            assertion.addConstraint(predEq, qName, declIntType.toOBJString());
            assertion.setSoftness(false);
            totalConstraints = totalConstraints.with(assertion);
            // name2Loc
            totalName2Loc = totalName2Loc.putAndCopy(qName, vardecl.getFileLocation());
            // since we couldn't change external variable, we can derive its range precisely by its type
            initRange = declIntType.getTypeRange();
          } else {
            // constraint PART I
            Constraint assertion = new Constraint();
            assertion.addConstraint(predDP, qName, declIntType.toOBJString());
            assertion.setSoftness(true);
            totalConstraints = totalConstraints.with(assertion);
            Constraint assertion2 = new Constraint();
            assertion2.addConstraint(predEq, qName, declIntType.toOBJString());
            assertion2.setSoftness(true);
            totalConstraints = totalConstraints.with(assertion2);
            // name2Loc
            totalName2Loc = totalName2Loc.putAndCopy(qName, vardecl.getFileLocation());
          }
          newState.addRange(qName, initRange, declIntType);
        } else {
          // nothing to do
          return soleSuccessor(newState);
        }
      }
    }
    return soleSuccessor(newState);
  }

  @Override
  protected Collection<RangeState> handleStatementEdge(CStatementEdge cfaEdge, CStatement expr) throws UnrecognizedCCodeException {
    RangeState newState = RangeState.copyOf(state);
    // we only consider assignment here since function assignment is addressed in handling FunctionReturnEdge
    if(expr instanceof CAssignment) {
      CAssignment assign = (CAssignment)expr;
      CLeftHandSide op1 = assign.getLeftHandSide();
      CRightHandSide op2 = assign.getRightHandSide();

      // whether it is a pointer assignment or not?
      CType operandType = op1.getExpressionType();
      if(operandType instanceof CPointerType) {
        // pointer assignment
        evaluateExpression(newState, op2);
        String leftName = findLeftHandNode(op1);
        // op2 can only be CExpression, since CFunctionCallExpression has already been addressed in handling FunctionReturnEdge
        // if the right-hand side is a function call expression, the result is unpredictable
        if(op2 instanceof CFunctionCallExpression) {
          return soleSuccessor(newState);
        }
        String rightName = findNextNode((CExpression)op2);
        if(leftName != null && rightName != null) {
          pointRel = pointRel.putAndCopy(leftName, rightName);
        } else if(leftName != null) {
          // now the right-hand expression is an integer pointer points to some other locations
          pointRel = pointRel.putAndCopy(leftName, dumbnode);
        }
        return soleSuccessor(newState);
      }
      // it could have integer type
      IntType baseOprdType = evaluateType(operandType);
      if(!baseOprdType.isNotInt()) {
        // integer type!
        ExpressionInfo info = evaluateExpression(newState, op2);
        Range rightRange = info.getRange();
        String leftName = findLeftHandNode(op1);
        if(leftName != null) {
          // Constraint: type of this integer should be larger than the range of right-side expression
          IntType updType = new IntType(rightRange);
          Constraint assertion = new Constraint();
          assertion.addConstraint(predP, leftName, updType.toOBJString());
          assertion.setSoftness(true);
          totalConstraints = totalConstraints.with(assertion);
          // name2Loc: none
          // loc2Fix: none
          // most importantly, add the range information
          newState.addRange(leftName, rightRange,baseOprdType);
          return soleSuccessor(newState);
        } else {
          // such as field reference and array subscript
          // the value of right-hand side expression should be sanity-checked
          FixGuide guide = new FixGuide(true, false, true, baseOprdType, -1, "");
          totalLoc2Guide = insertNewFixGuide(op2.getFileLocation(), guide);
          return soleSuccessor(newState);
        }
      } else {
        // not integer type
        // FIXME: please evaluate the right-hand-side expression!!
        evaluateExpression(newState, op2);
        return soleSuccessor(newState);
      }
    }
    if(expr instanceof CFunctionCallStatement) {
      CFunctionCallExpression funcCall = ((CFunctionCallStatement) expr).getFunctionCallExpression();
      // a function call could be a standalone statement
      evaluateExpression(newState, funcCall);
      return soleSuccessor(newState);
    }
    // CFunctionCall is also covered when handling FunctionReturnEdge
    return soleSuccessor(newState);
  }

  private ExpressionInfo evaluateExpression(RangeState readableState, CRightHandSide expr) throws UnrecognizedCCodeException {
    // isBound: if this expression is bounded, this information can be at higher level than expression
    // If this expression can be freely elevated, we just generate a series of constraints on this expression and intermediate results
    // If this expression is bounded, we can *re-evaluate* the expression with range limitation, thus the result range is also limited by its type
    ExpressionValueVisitor visitor = new ExpressionValueVisitor(readableState, edge);
    ExpressionInfo eval = expr.accept(visitor);

    // merge metadata here
    totalConstraints = totalConstraints.withAll(visitor.getConstraints());
    PersistentMap<String, FileLocation> localName2Loc = visitor.getName2Loc();
    for(Entry<String, FileLocation> entry : localName2Loc.entrySet()) {
      totalName2Loc = totalName2Loc.putAndCopy(entry.getKey(), entry.getValue());
    }
    PersistentMap<FileLocation, FixGuide> localLoc2Fix = visitor.getLoc2Guide();
    for(Entry<FileLocation, FixGuide> entry : localLoc2Fix.entrySet()) {
      totalLoc2Guide = insertNewFixGuide(entry.getKey(), entry.getValue());
    }

    return eval;
  }

  private static IntType evaluateType(CType type) {
    if(type instanceof CSimpleType) {
      return new IntType((CSimpleType)type);
    } else if(type instanceof CTypedefType) {
      CType realType = ((CTypedefType)type).getRealType().getCanonicalType();
      return evaluateType(realType);
    } else {
      return IntType.UNKNOWN;
    }
  }

  private static String generateIntermName() {
    String name = intermPrefix.concat(String.valueOf(nameCounter));
    nameCounter++;
    return name;
  }

  private Collection<RangeState> soleSuccessor(RangeState successor) {
    return Collections.singleton(successor);
  }

  private Collection<RangeState> noSuccessors() {
    return Collections.emptySet();
  }

  private void addRange(RangeState newState, CExpression lhs, Range range, IntType type) {
    // left hand expression has 4 cases (plus Complex, but that is not considered now):
    // - (1) array subscript (x)
    // - (2) field reference (x)
    // - (3) variable (CIdExpression)
    // - (4) pointer dereference (CPointerExpression)
    if(lhs instanceof CIdExpression) {
      newState.addRange(((CIdExpression)lhs).getDeclaration().getQualifiedName(), range, type);
    } else if(lhs instanceof CPointerExpression) {
      // if the operand of lhs points to an integer variable?
      Pair<Integer, String> eval = distanceComp(lhs);
      if(eval.getFirst() == 0) {
        // lhs is actually an integer variable now.
        String target = eval.getSecond();
        assert (!target.equals(dumbnode)) : "Invalid pointer target!";
        newState.addRange(target, range, type);
      }
    }
  }

  private static Pair<Integer, String> distanceComp(CExpression ptr) {
    if(ptr instanceof CIdExpression) {
      String qName = ((CIdExpression) ptr).getDeclaration().getQualifiedName();
      // in the following we calculate the distance
      // FIRST, this name is an integer
      if(totalIntNames.contains(qName)) {
        return Pair.of(0, qName);
      }

      // SECOND, otherwise we compute the distance step by step
      int dist = 0;
      if(pointRel.containsKey(qName)) {
        String newId = qName;
        while(pointRel.containsKey(newId)) {
          newId = pointRel.get(newId);
          dist++;
        }
        // if this pointer points to a dumb node, this is not what we want
        if(newId.equals(dumbnode)) {
          return Pair.of(-1, null);
        }
        // otherwise, this pointer points to an integer variable
        // (Since the sink of point-to graph must be a dumb node or integer variable node)
        return Pair.of(dist, newId);
      } else {
        // not in this graph (not an integer pointer) nor an integer variable
        dist = -1;
        return Pair.of(dist, null);
      }
    } else if(ptr instanceof CPointerExpression) {
      CExpression oprd = ((CPointerExpression) ptr).getOperand();
      Pair<Integer, String> eval = distanceComp(oprd);
      int dist = eval.getFirst();
      if(dist > -1) {
        // since the operand of ptr must be a pointer, thus dist should be greater or equal to 1
        return Pair.of(dist - 1, eval.getSecond());
      } else {
        return Pair.of(-1, null);
      }
    } else if(ptr instanceof CUnaryExpression) {
      // in this case, we consider ampersand expression only
      UnaryOperator optr = ((CUnaryExpression) ptr).getOperator();
      CExpression oprd = ((CUnaryExpression) ptr).getOperand();
      if(optr == UnaryOperator.AMPER) {
        Pair<Integer, String> eval = distanceComp(oprd);
        int dist = eval.getFirst();
        if(dist > -1) {
          return Pair.of(dist + 1, eval.getSecond());
        } else {
          return Pair.of(-1, null);
        }
      } else {
        // other cases
        return Pair.of(-1, null);
      }
    } else {
      return Pair.of(-1, null);
    }
  }

  private static String findNextNode(CExpression ptr) {
    // the input ptr is a pointer expression in principle
    if(ptr instanceof CIdExpression) {
      String qName = ((CIdExpression) ptr).getDeclaration().getQualifiedName();
      if(totalIntNames.contains(qName)) {
        return qName;
      }
      return pointRel.get(qName);
    } else if(ptr instanceof CPointerExpression) {
      CExpression oprd = ((CPointerExpression) ptr).getOperand();
      String target = findNextNode(oprd);
      if(target == null) {
        return null;
      }
      return pointRel.get(target);
    } else if(ptr instanceof CUnaryExpression) {
      CExpression oprd = ((CUnaryExpression) ptr).getOperand();
      UnaryOperator optr = ((CUnaryExpression) ptr).getOperator();
      if(optr == UnaryOperator.AMPER) {
        assert (oprd instanceof CLeftHandSide);
        String target = findLeftHandNode((CLeftHandSide)oprd);
        return target;
      } else {
        return null;
      }
    } else if(ptr instanceof CCastExpression) {
      return findNextNode(((CCastExpression) ptr).getOperand());
    } else {
      return null;
    }
  }

  private static String findLeftHandNode(CLeftHandSide leftExpr) {
    String name = "";
    if(leftExpr instanceof CIdExpression) {
      name = ((CIdExpression) leftExpr).getDeclaration().getQualifiedName();
      if(totalIntNames.contains(name) || pointRel.containsKey(name)) {
        return name;
      } else {
        return null;
      }
    } else if(leftExpr instanceof CPointerExpression) {
      CExpression operand = ((CPointerExpression)leftExpr).getOperand();
      if(operand instanceof CLeftHandSide) {
        String target = findLeftHandNode((CLeftHandSide)operand);
        if(target == null) {
          return null;
        }
        return pointRel.get(target);
      } else {
        // only one case: &exp where exp is a left-hand expression
        if(operand instanceof CUnaryExpression) {
          CExpression oprd = ((CUnaryExpression) operand).getOperand();
          UnaryOperator unop = ((CUnaryExpression) operand).getOperator();
          if(unop == UnaryOperator.AMPER) {
            assert (oprd instanceof CLeftHandSide) : "The operand of address-of must be a lvalue.";
            String target = findLeftHandNode((CLeftHandSide)oprd);
            return target;
          } else {
            return null;
          }
        } else {
          return null;
        }
      }
    } else {
      return null;
    }
  }

  @Override
  public Collection<? extends AbstractState> strengthen(AbstractState pState, List<AbstractState> pOtherStates,
      CFAEdge pCfaEdge, Precision pPrecision) throws CPATransferException, InterruptedException {
    return null;
  }

  private static class ExpressionValueVisitor
    extends DefaultCExpressionVisitor<ExpressionInfo, UnrecognizedCCodeException>
    implements CRightHandSideVisitor<ExpressionInfo, UnrecognizedCCodeException> {

    private final RangeState readableState;
    private final CFAEdge cfaEdge;

    // the expression is stored in a tree structure, each node has three fields:
    // (1) expression; (2) its range; (3) its type
    // if the range and specifier cannot match, we can add a constraint on integer variables

    private PersistentList<Constraint> constraints;
    private PersistentMap<String, FileLocation> name2Loc;  // variable and its declaration location
    // this structure records expressions that are strictly constrained
    private PersistentMap<FileLocation, FixGuide> loc2Guide;
    // expression and its location, for (1) expressions that are constrained externally (intolerable error in COMPSAC 16 paper)
    //                                  (2) pointer expressions requiring explicit conversion (such as converting an integer pointer to long pointer)


    public ExpressionValueVisitor(RangeState pState, CFAEdge edge) {
      readableState = pState;
      cfaEdge = edge;
      constraints = PersistentLinkedList.of();
      name2Loc = PathCopyingPersistentTreeMap.of();
      loc2Guide = PathCopyingPersistentTreeMap.of();
    }

    public PersistentList<Constraint> getConstraints() {
      return constraints;
    }

    public PersistentMap<String, FileLocation> getName2Loc() {
      return name2Loc;
    }

    public PersistentMap<FileLocation, FixGuide> getLoc2Guide() {
      return loc2Guide;
    }

    @Override
    public ExpressionInfo visit(CArraySubscriptExpression arrExpr) throws UnrecognizedCCodeException {
      // (1) constraint is not necessary
      // (2) variable and its declaration is not required
      // (3) fix guide: case (1)->index expression; case (2) none

      CExpression arrayExpr = arrExpr.getArrayExpression();
      CExpression idxExpr = arrExpr.getSubscriptExpression();
      // after "accept" method, more items will be inserted into constraints list
      arrayExpr.accept(this);
      idxExpr.accept(this);
      // the value of array element cannot be elevated since it corresponds to a fixed size of memory block
      // its range is constrained by the type of array element
      CType elemType = arrExpr.getExpressionType().getCanonicalType();
      // convert CType into IntType
      IntType resultType = evaluateType(elemType);

      // insert fix guide information
      FileLocation idxLoc = idxExpr.getFileLocation();
      FixGuide guide = new FixGuide(true, false, true, IntType.INDEX_TYPE, -1, "");
      loc2Guide = loc2Guide.putAndCopy(idxLoc, guide);

      Range resultRange = resultType.getTypeRange();

      return new ExpressionInfo(resultRange, resultType);
    }

    @Override
    public ExpressionInfo visit(CBinaryExpression binExpr) throws UnrecognizedCCodeException {
      // (1) constraint for this expression node
      // (2) variable and its declaration is not required
      // (3) fix guide: (1) none; (2) none

      ExpressionInfo tr1 = binExpr.getOperand1().accept(this);
      ExpressionInfo tr2 = binExpr.getOperand2().accept(this);

      BinaryOperator binOptr = binExpr.getOperator();
      if(binOptr.isLogicalOperator()) {
        IntType resultType = new IntType(CNumericTypes.INT); // according to C11
        Range resultR = getLogicRange(binOptr, tr1.getRange(), tr2.getRange());
        ExpressionInfo tr = new ExpressionInfo(resultR, resultType); // list of integer names remains empty
        Range mergedRange = tr1.getRange().union(tr2.getRange());
        IntType updType = new IntType(mergedRange);
        // append fix guide if necessary
        if(!tr1.getType().equals(updType)) {
          FixGuide guide = new FixGuide(true, false, false, updType, -1, "");
          loc2Guide = loc2Guide.putAndCopy(binExpr.getOperand1().getFileLocation(), guide);
        }
        if(!tr2.getType().equals(updType)) {
          FixGuide guide = new FixGuide(true, false, false, updType, -1, "");
          loc2Guide = loc2Guide.putAndCopy(binExpr.getOperand2().getFileLocation(), guide);
        }
        return tr;
      } else {
        // arithmetic operator
        PersistentList<String> namesF = tr1.getIntNames();
        PersistentList<String> namesS = tr2.getIntNames();
        // union of two name lists
        PersistentList<String> namesT = union(namesF, namesS);

        // address different operators
        Range r1 = tr1.getRange();
        Range r2 = tr2.getRange();
        IntType t1 = tr1.getType();
        IntType t2 = tr2.getType();
        Range resultRange;
        IntType mergedType = t1.mergeWith(t2);

        switch(binOptr) {
        case PLUS:
          resultRange = r1.plus(r2);
          break;
        case MINUS:
          resultRange = r1.minus(r2);
          break;
        case MULTIPLY:
          resultRange = r1.times(r2);
          break;
        case DIVIDE:
          resultRange = r1.divide(r2);
          break;
        case MODULO:
          resultRange = r1.modulo(r2);
          break;
        case SHIFT_LEFT: {
          // bit manipulation
          // (1) for operands, convert them to unsigned ones if they are signed
          // (2) for the whole expression, restrict bit length by explicit conversion (truncation)
          // **NOTE: for bit shift operation, the result type is consistent with that of the left operand
          //         That means, we cannot elevate the left type.

          // we mark the ideal type of whole operation on the left operand
          IntType lt = t1.promoteInt();
          FixGuide guide = new FixGuide(true, false, true, lt, shiftLeftLevel, "");
          // We should protect each bit shift operation by sanitization routine:
          // - if the second operand is larger than the first or negative, UB!
          // - if the first operand is signed and negative, UB!
          // Since bit shift operation is relatively rarer in practice, the overhead by sanity function call is negligible.
          loc2Guide = loc2Guide.putAndCopy(binExpr.getFileLocation(), guide);

          resultRange = r1.shiftLeft(r2);
          // Since the final result of left shift can exceed the type range, we have to restrict this.
          if(!lt.getTypeRange().contains(resultRange)) {
            resultRange = lt.getTypeRange();
          }

          // return ExpressionInfo early
          // no names included because this expression cannot be elevated
          ExpressionInfo info = new ExpressionInfo(resultRange, lt);
          return info;
        }
        case SHIFT_RIGHT: {
          IntType lt = t1.promoteInt();
          FixGuide guide = new FixGuide(true, false, true, lt, shiftRightLevel, "");
          loc2Guide = loc2Guide.putAndCopy(binExpr.getFileLocation(), guide);

          resultRange = r1.shiftRight(r2);
          if(!lt.getTypeRange().contains(resultRange)) {
            resultRange = lt.getTypeRange();
          }
          ExpressionInfo info = new ExpressionInfo(resultRange, lt);
          return info;
        }
        case BINARY_AND:
        case BINARY_OR:
        case BINARY_XOR: {
          // bit manipulation will not introduce overflow
          // thus, we use the range of type as the result
          // NOTE: for bit manipulation we can get some kinds of precise results, but that requires
          //       sophisticated technique and improved representation method
          IntType newt;
          if(mergedType.getSign()) {
            newt = mergedType.flipSign();
          } else {
            newt = mergedType;
          }
          FixGuide guide = new FixGuide(true, false, false, newt, -1, "");
          loc2Guide = loc2Guide.putAndCopy(binExpr.getOperand1().getFileLocation(), guide);
          loc2Guide = loc2Guide.putAndCopy(binExpr.getOperand2().getFileLocation(), guide);
          resultRange = newt.getTypeRange();
          ExpressionInfo info = new ExpressionInfo(resultRange, newt);
          return info;
        }
        default:
          throw new AssertionError("unknown binary operator " + binOptr);
        }

        // for other arithmetic operations...
        ExpressionInfo info = new ExpressionInfo(namesT, resultRange, mergedType);
        // insert constraints for binary expressions
        IntType updType = new IntType(resultRange);
        if(mergedType.contains(updType.getTypeRange())) {
          // unnecessary to add such constraint
          return info;
        }
        if(namesT.size() > 0) {
          Constraint assertion = new Constraint();
          assertion.addConstraint(predP, namesT, updType.toOBJString());
          assertion.setSoftness(true);
          constraints = constraints.with(assertion);
        } else {
          // necessary to append an explicit conversion
          // but if the desired type is OVERLONG, we cannot append such conversion
          if(!updType.checkOverlong()) {
            // TODO: there is a potential bug here. We may replace a good fix with a bad fix(narrower type)
            FixGuide fix = new FixGuide(true, false, false, updType, -1, "");
            loc2Guide = loc2Guide.putAndCopy(binExpr.getOperand1().getFileLocation(), fix);
            loc2Guide = loc2Guide.putAndCopy(binExpr.getOperand2().getFileLocation(), fix);
          } else {
            // we can do nothing to save this...
          }
        }

        return info;
      }
    }

    @Override
    public ExpressionInfo visit(CCastExpression castExpr) throws UnrecognizedCCodeException {
      // (1) constraint for this *intermediate* variable
      // (2) generate a random name for cast operand and then insert it into name2Loc
      // (3) fix guide: (1) none; (2) none
      // *** How about pointer cast? No!
      // *** Since pointer conversion occurs only when we obtain the dereference of certain pointer,
      //     which is relevant to [pointerExpression].

      ExpressionInfo trChild = castExpr.getOperand().accept(this);

      // convert CType to IntType if possible
      CType newType = castExpr.getCastType().getCanonicalType();
      IntType resultType = evaluateType(newType);

      // if the cast specifier is integer type, we generate a temporary name and push a constraint
      if(!resultType.isNotInt()) {
        // generate a temporary name
        String nameforCast = generateIntermName();
        FileLocation locforCast = castExpr.getFileLocation();
        name2Loc = name2Loc.putAndCopy(nameforCast, locforCast);
        // push constraints:
        // (1) temporary variable should have higher type than operands
        // (2) type of temporary variable should contain calculated range
        PersistentList<String> names = trChild.getIntNames();
        Constraint assertion1 = new Constraint();
        for(String name : names) {
          assertion1.addConstraint(predP, nameforCast, name);
        }
        assertion1.setSoftness(true);
        constraints = constraints.with(assertion1);
        IntType updType = new IntType(trChild.getRange());
        Constraint assertion2 = new Constraint();
        assertion2.addConstraint(predP, nameforCast, updType.toOBJString());
        assertion2.setSoftness(true);
        constraints = constraints.with(assertion2);
        Constraint assertion3 = new Constraint();
        assertion3.addConstraint(predEq, nameforCast, resultType.toOBJString());
        assertion3.setSoftness(true);
        constraints = constraints.with(assertion3);

        // finally, we return the ExpressionInfo object
        PersistentList<String> newNames = PersistentLinkedList.of();
        newNames = newNames.with(nameforCast);
        ExpressionInfo tr = new ExpressionInfo(newNames, trChild.getRange(), resultType);
        return tr;
      } else {
        // no need for temporary name and no need to append new constraints
        ExpressionInfo tr = new ExpressionInfo(trChild.getRange(), resultType);
        return tr;
      }

    }

    @Override
    public ExpressionInfo visit(CFieldReference fldref) throws UnrecognizedCCodeException {
      // owner is usually an expression of some special type (e.g. type of specific data structure)
      // field is a just a string literal

      // (1) constraint: none
      // (2) variable and its declaration are not required
      // (3) fix guide: none

      CExpression owner = fldref.getFieldOwner();
      owner.accept(this); // we need its side effects only
      // field reference cannot be elevated either
      CType type = fldref.getExpressionType().getCanonicalType();
      IntType resultType;
      if(type instanceof CSimpleType) {
        resultType = new IntType((CSimpleType)type);
      } else if(type instanceof CTypedefType) {
        CType realType = ((CTypedefType)type).getRealType().getCanonicalType();
        if(realType instanceof CSimpleType) {
          resultType = new IntType((CSimpleType)realType);
        } else {
          resultType = IntType.UNKNOWN;
        }
      } else {
        resultType = IntType.UNKNOWN;
      }

      ExpressionInfo info = new ExpressionInfo(resultType.getTypeRange(), resultType);
      return info;
    }

    @Override
    public ExpressionInfo visit(CPointerExpression ptrExpr) throws UnrecognizedCCodeException {
      // pointer expression refers to dereference of pointer, such as *p

      // (1) constraints: none
      // (2) name2Loc: none
      // (3) loc2Guide: we may need to add a type cast for pointer operand, such as
      //     *p  ----->  *((long*)p) where p points to an int value

      CExpression ptr = ptrExpr.getOperand();
      // visit this pointer
      ptr.accept(this);

      // the range of this expression depends on pointer relation
      // if p points to an integer variable v, then *p ranges in the range of v
      // otherwise, the type of p and *p cannot be elevated
      Range resultRange;
      CType type = ptrExpr.getExpressionType().getCanonicalType();
      IntType resultType = evaluateType(type);

      // since p is a pointer, it can have these forms
      // (1) CIdExpression, so p is a variable
      // (2) CUnaryExpression which has AMPERSAND(&) operator
      // (3) CPointerExpression which is a level2(or higher) pointer
      Pair<Integer, String> eval = distanceComp(ptr);
      int dist = eval.getFirst();
      if(dist > -1) {
        // if so, we must have dist > 0 for sure
        dist--;
      }
      if(dist == 0) {
        // OK, if dist == 0, we say this expression refers to an integer variable
        String qName = eval.getSecond();
        if(qName == null) {
          throw new AssertionError("problems in distance computing.");
        } else {
          resultRange = readableState.getRange(qName);
          // and this type can be elevated

          // add an explicit pointer cast if necessary
          IntType actualType = readableState.getType(qName);
          if(!actualType.equals(resultType)) {
            // actualType is larger than resultType (by the monotonicity of type evolution)
            FixGuide guide = new FixGuide(false, true, false, actualType, 1, qName);
            loc2Guide = loc2Guide.putAndCopy(ptr.getFileLocation(), guide);
          }

          PersistentList<String> intNames = PersistentLinkedList.of();
          intNames = intNames.with(qName);
          // !! if we use resultType here, our fix will have no effects
          ExpressionInfo info = new ExpressionInfo(intNames, resultRange, actualType);
          return info;
        }
      } else if(dist > 0) {
        // this expression is still a pointer
        ExpressionInfo info = new ExpressionInfo(Range.EMPTY, resultType);
        return info;
      } else {
        // dist == -1
        // this expression can be array subscript (in the form of pointer expression, they are equivalent)
        ExpressionInfo info = new ExpressionInfo(resultType.getTypeRange(), resultType);
        return info;
      }
    }

    @Override
    public ExpressionInfo visit(CFunctionCallExpression funcExpr) throws UnrecognizedCCodeException {
      // In CFA, if we call a function of ours, a fresh temporary variable will be introduced to hold the value of this function call
      // therefore, the function call expression here denotes one library call
      // NOTE: since we have no knowledge on the function call, we assume the result range is consistent with return type of function

      // (1) constraints: none
      // (2) name2Loc: none
      // (3) loc2Guide: required for integer arguments

      CExpression funcExpression = funcExpr.getFunctionNameExpression();

      CType funcType = funcExpression.getExpressionType();
      assert (funcType instanceof CFunctionType);
      CType returnType = ((CFunctionType)funcType).getReturnType();

      IntType resultType = evaluateType(returnType);

      Range resultRange = resultType.getTypeRange();

      List<CExpression> args = funcExpr.getParameterExpressions();
      List<CType> types = ((CFunctionType)funcType).getParameters();
      if(args.size() == types.size()) {
        // this is an ordinary function
        for(int i = 0; i < args.size(); i++) {
          args.get(i).accept(this);
          CType thisType = types.get(i);
          // convert CType into IntType
          IntType intType = evaluateType(thisType);

          if(!intType.isNotInt()) {
            // this argument is integer, we should add sanity check to protect this value
            FixGuide guide = new FixGuide(true, false, true, intType, -1, "");
            loc2Guide = loc2Guide.putAndCopy(args.get(i).getFileLocation(), guide);
          }
        }
      } else {
        // TODO: this is a variadic function, such as printf, scanf
      }

      ExpressionInfo info = new ExpressionInfo(resultRange, resultType);
      return info;
    }

    @Override
    public ExpressionInfo visit(CCharLiteralExpression charlit) throws UnrecognizedCCodeException {
      // constraints: none
      // name2Loc: none
      // loc2Guide: none
      Range resultR = new Range(charlit.getCharacter());
      IntType resultType = new IntType(CNumericTypes.CHAR);
      return new ExpressionInfo(resultR, resultType);
    }

    @Override
    public ExpressionInfo visit(CIntegerLiteralExpression intlit) throws UnrecognizedCCodeException {
      // type of integer constant is signed, and dynamic according to the value
      BigInteger realValue = intlit.getValue();

      // NOTE: since the type of constant is determined by its form and value, we should obtain the actual type
      //       of this constant by calling corresponding method instead of analyzing range
      CType constType = intlit.getExpressionType();
      if(constType instanceof CTypedefType) {
        constType = ((CTypedefType)constType).getRealType().getCanonicalType();
      }
      assert (constType instanceof CSimpleType) : "integer constant should have simple type!";
      IntType realType = new IntType((CSimpleType)constType);
      Range realRange = new Range(new CompInteger(realValue));
      return new ExpressionInfo(realRange, realType);
    }

    @Override
    public ExpressionInfo visit(CIdExpression id) throws UnrecognizedCCodeException {
      // (1) constraints: none (constraints on this variable should be asserted at a higher level)
      // (2) name2Loc: none (variable has its name)
      // (3) loc2Guide: none (as (1), we should handle this at a higher level)
      if(id.getDeclaration() instanceof CEnumerator) {
        // this id is a member of one enumerator
        long enumValue = ((CEnumerator)id.getDeclaration()).getValue();
        // C11: "An identifier declared as an enumeration constant has type int"
        Range resultRange = new Range(enumValue);
        IntType resultType = new IntType(CNumericTypes.INT);
        return new ExpressionInfo(resultRange, resultType);
      }

      // otherwise, this is a simple variable identifier
      final String idName = id.getDeclaration().getQualifiedName();
      Range resultRange = readableState.getRange(idName);
      IntType resultType = readableState.getType(idName);
      if(resultType.isOther()) {
        // we treat its type carefully
        CType realType = id.getExpressionType();
        IntType realCannType = evaluateType(realType);
        if(!realCannType.isNotInt()) {
          resultType = IntType.OLTYPE;
        }
      }
      return new ExpressionInfo(resultRange, resultType);
    }

    @Override
    public ExpressionInfo visit(CUnaryExpression unExpr) throws UnrecognizedCCodeException {
      /*
       * MINUS: negation, equivalent to 0-i.
       * - (1) constraints: we should add constraints for integer variables in the operand
       * - (2) none
       * - (3) no fix guide
       * AMPER: get the address, the result type MUST be a pointer
       * - (1) constraints: none
       * - (2) none
       * - (3) none (if we add sanity check or explicit cast on operand, then new operand is not lvalue any more!)
       * TILDE: bitwise NOT
       * - (1) constraints: none
       * - (2) none
       * - (3) explicit cast is required if new type of operand is not consistent with the original one
       *       ** since this is bit manipulation, we should keep original type, i.e. discard all extra bits.
       *       ** Otherwise, the result will be incorrect
       * SIZEOF:
       * - (1) constraints: none
       * - (2) none
       * - (3) none
       * ** NOTE: We can obtain the size of a static array, the size of which is determined in compile-time
       *          For typeid, we just keep the code. For other variables, we use a range to contain all possible cases
       * ALIGNOF:
       * the same as SIZEOF
       */

      ExpressionInfo info0 = unExpr.getOperand().accept(this);
      Range r = info0.getRange();
      IntType t = info0.getType();

      // handle unary operators respectively
      Range resultRange;
      ExpressionInfo info;
      switch(unExpr.getOperator()) {
      case MINUS: {
        resultRange = r.negate();
        IntType updType = new IntType(resultRange);
        PersistentList<String> intNames = info0.getIntNames();
        // add constraints
        if(intNames.size() > 0) {
          Constraint assertion = new Constraint();
          assertion.addConstraint(predP, intNames, updType.toOBJString());
          assertion.setSoftness(true);
          constraints = constraints.with(assertion);
        } else {
          // explicit conversion, like binary expression
          if(!t.contains(updType.getTypeRange())) {
            // updType is OVERLONG?
            if(!updType.checkOverlong()) {
              FixGuide fix = new FixGuide(true, false, false, updType, -1, "");
              loc2Guide = loc2Guide.putAndCopy(unExpr.getOperand().getFileLocation(), fix);
            }
          }
        }
        info = new ExpressionInfo(intNames, resultRange, t);
        return info;
      }
      case AMPER: {
        // this expression is pointer, thus its range should be UNBOUND
        resultRange = Range.UNBOUND;
        info = new ExpressionInfo(resultRange, IntType.UNKNOWN);
        return info;
      }
      case TILDE: {
        // this is bit manipulation
        // !! the value of expression should be unsigned regardless of the sign of operand
        IntType resultType = t.promoteInt();
        if(resultType.getSign()) {
          resultType = resultType.flipSign();
        }
        // insert explicit type cast on this expression
        FileLocation locForCast = unExpr.getOperand().getFileLocation();
        FixGuide guide = new FixGuide(true, false, false, resultType, -1, "");
        loc2Guide = loc2Guide.putAndCopy(locForCast, guide);

        resultRange = resultType.getTypeRange();
        info = new ExpressionInfo(resultRange, resultType);
        return info;
      }
      case SIZEOF: {
        // for all types except CSimpleType, size is determined
        // for CSimpleType, we should give a wide range since integers could be elevated
        CExpression operand = unExpr.getOperand();
        CType oprdType = operand.getExpressionType();
        if(oprdType instanceof CSimpleType) {
          IntType intType = new IntType((CSimpleType)oprdType);
          if(!intType.isNotInt()) {
            // integers, its type could only be elevated
            int intLength = intType.getSize();
            resultRange = new Range(new CompInteger(intLength), new CompInteger(IntType.getLongestIntSize()));
          } else {
            // others, we can give the precise range at the compile time
            resultRange = new Range(IntType.getSize(oprdType));
          }
        } else if(oprdType instanceof CTypedefType) {
          CType realType = ((CTypedefType)oprdType).getRealType();
          if(realType instanceof CSimpleType) {
            IntType intType = new IntType((CSimpleType)realType);
            if(!intType.isNotInt()) {
              // integers
              int intLength = intType.getSize();
              resultRange = new Range(new CompInteger(intLength), new CompInteger(IntType.getLongestIntSize()));
            } else {
              resultRange = new Range(IntType.getSize(realType));
            }
          } else {
            resultRange = new Range(IntType.getSize(realType));
          }
        } else {
          resultRange = new Range(IntType.getSize(oprdType));
        }
        // OK, return this info
        info = new ExpressionInfo(resultRange, IntType.SIZET);
        return info;
      }
      case ALIGNOF: {
        CExpression operand = unExpr.getOperand();
        CType oprdType = operand.getExpressionType();
        if(oprdType instanceof CSimpleType) {
          IntType intType = new IntType((CSimpleType)oprdType);
          if(!intType.isNotInt()) {
            // integers
            // NOTE: for integers, the results of alignof and sizeof are the same
            int intLength = intType.getSize();
            resultRange = new Range(new CompInteger(intLength), new CompInteger(IntType.getLongestIntSize()));
          } else {
            resultRange = new Range(IntType.getAlignof(oprdType));
          }
        } else if(oprdType instanceof CTypedefType) {
          CType realType = ((CTypedefType)oprdType).getRealType();
          if(realType instanceof CSimpleType) {
            IntType intType = new IntType((CSimpleType)realType);
            if(!intType.isNotInt()) {
              // integers
              int intLength = intType.getSize();
              resultRange = new Range(new CompInteger(intLength), new CompInteger(IntType.getLongestIntSize()));
            } else {
              resultRange = new Range(IntType.getAlignof(realType));
            }
          } else {
            resultRange = new Range(IntType.getAlignof(realType));
          }
        } else {
          resultRange = new Range(IntType.getAlignof(oprdType));
        }
        info = new ExpressionInfo(resultRange, IntType.SIZET);
        return info;
      }
      default:
        // unexpected case
        info = new ExpressionInfo(Range.UNBOUND, IntType.UNKNOWN);
        return info;
      }
    }

    @Override
    public ExpressionInfo visit(CTypeIdExpression expr) throws UnrecognizedCCodeException {
      // Typeid expression denotes the one with an operator and an operand which is a type specifier
      // For example: size(int)
      // Since we don't change a standalone specifier, the value of typeid expression is determined even at compile time
      // **NOTE: in C11, only SIZEOF and ALIGNOF are supported
      TypeIdOperator operator = expr.getOperator();
      CType type = expr.getType(); // this type is not the type of EXPRESSION!!

      Range resultRange;
      ExpressionInfo info;
      switch(operator) {
      case SIZEOF: {
        resultRange = new Range(IntType.getSize(type));
        info = new ExpressionInfo(resultRange, IntType.SIZET);
        return info;
      }
      case ALIGNOF: {
        resultRange = new Range(IntType.getAlignof(type));
        info = new ExpressionInfo(resultRange, IntType.SIZET);
        return info;
      }
      default:
        // unexpected case
        info = new ExpressionInfo(Range.UNBOUND, IntType.UNKNOWN);
        return info;
      }
    }

    @Override
    public ExpressionInfo visitDefault(CExpression expr) throws UnrecognizedCCodeException {
      CType type = expr.getExpressionType().getCanonicalType();
      IntType resultType = evaluateType(type);

      // for unconsidered expressions, we cannot decide whether an integer error occurs
      // thus we assume the possible range of this expression is consistent with its type
      if(resultType.isNotInt()) {
        // we cannot add this symbol into range map
        return new ExpressionInfo(Range.UNBOUND, resultType);
      } else {
        return new ExpressionInfo(resultType.getTypeRange(), resultType);
      }
    }

    private Range getLogicRange(BinaryOperator op, Range r1, Range r2) {
      // logic expression values in 0 and 1.
      switch(op) {
      case EQUALS:
        if(!r1.intersects(r2)) {
          // they should be unequal
          return Range.ZERO;
        } else if(r1.getLow().equals(r1.getHigh()) && r1.equals(r2)) {
          return Range.ONE;
        } else {
          return Range.BOOL;
        }
      case NOT_EQUALS:
        if(!r1.intersects(r2)) {
          return Range.ONE;
        } else if(r2.getLow().equals(r2.getHigh()) && r1.equals(r2)) {
          return Range.ZERO;
        } else {
          return Range.BOOL;
        }
      case GREATER_THAN:
        if(r1.isGreaterThan(r2)) {
          return Range.ONE;
        } else if(r2.isGreaterOrEqualThan(r1)) {
          return Range.ZERO;
        } else {
          return Range.BOOL;
        }
      case GREATER_EQUAL:
        return getLogicRange(BinaryOperator.GREATER_THAN, r1.plus(1L), r2);
      case LESS_THAN:
        return getLogicRange(BinaryOperator.GREATER_THAN, r2, r1);
      case LESS_EQUAL:
        return getLogicRange(BinaryOperator.GREATER_EQUAL, r2, r1);
      default:
        throw new AssertionError("unknown logic operation: " + op);
      }
    }


    /**
     * We implement a method to solve union of two lists. Check of correctness is seriously required.
     * @param list1
     * @param list2
     * @return
     */
    private <T> PersistentList<T> union(PersistentList<T> list1, PersistentList<T> list2) {
      Set<T> set = new HashSet<>();
      set.addAll(list1);
      set.addAll(list2);
      return PersistentLinkedList.copyOf(new ArrayList<>(set));
    }

  }

  private static class ExpressionInfo {

    // elements of fields
    private PersistentList<String> intNames;
    private Range valueRange;
    private IntType valueType;

    public ExpressionInfo(PersistentList<String> names, Range range, IntType type) {
      intNames = names;
      valueRange = range;
      valueType = type;
    }

    /**
     * This constructor is particularly for constant objects
     * @param range
     * @param type
     */
    public ExpressionInfo(Range range, IntType type) {
      intNames = PersistentLinkedList.of();
      valueRange = range;
      valueType = type;
    }

    public Range getRange() {
      return valueRange;
    }

    public IntType getType() {
      return valueType;
    }

    public PersistentList<String> getIntNames() {
      return intNames;
    }

    // TODO: method to provide elevation suggestions

  }

}
