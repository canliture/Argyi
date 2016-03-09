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

import org.sosy_lab.common.Pair;
import org.sosy_lab.cpachecker.cfa.blocks.Block;
import org.sosy_lab.cpachecker.cfa.blocks.ReferencedVariable;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.FunctionExitNode;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.Reducer;

public class RangeReducer implements Reducer {

  private boolean occursInBlock(Block pBlock, String pVar) {
    for(ReferencedVariable refVar : pBlock.getReferencedVariables()) {
      if(refVar.getName().equals(pVar)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public AbstractState getVariableReducedState(AbstractState pExpandedState, Block pContext, CFANode pCallNode) {
    // TODO Auto-generated method stub
    RangeState expandedState = (RangeState)pExpandedState;
    RangeState clonedElement = RangeState.copyOf(expandedState);
    for(String trackedVar : expandedState.getRangeMap().keySet()) {
      if(!occursInBlock(pContext, trackedVar)) {
        clonedElement.removeRange(trackedVar);
      }
    }

    return clonedElement;
  }

  @Override
  public AbstractState getVariableExpandedState(AbstractState pRootState, Block pReducedContext,
      AbstractState pReducedState) {
    // TODO Auto-generated method stub
    RangeState rootState = (RangeState)pRootState;
    RangeState reducedState = (RangeState)pReducedState;
    RangeState diffElement = RangeState.copyOf(reducedState);

    for(String trackedVar : rootState.getRangeMap().keySet()) {
      if(!occursInBlock(pReducedContext, trackedVar)) {
        diffElement.addRange(trackedVar, rootState.getRange(trackedVar), rootState.getType(trackedVar));
      }
    }

    return diffElement;
  }

  @Override
  public Precision getVariableReducedPrecision(Precision pPrecision, Block pContext) {
    // TODO Auto-generated method stub
    return pPrecision;
  }

  @Override
  public Precision getVariableExpandedPrecision(Precision pRootPrecision, Block pRootContext,
      Precision pReducedPrecision) {
    // TODO Auto-generated method stub
    return pReducedPrecision;
  }

  @Override
  public Object getHashCodeForState(AbstractState pStateKey, Precision pPrecisionKey) {
    // TODO Auto-generated method stub
    RangeState stateKey = (RangeState)pStateKey;
    return Pair.of(stateKey.getRangeMap(), pPrecisionKey);
  }

  @Override
  public int measurePrecisionDifference(Precision pPrecision, Precision pOtherPrecision) {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public AbstractState getVariableReducedStateForProofChecking(AbstractState pExpandedState, Block pContext,
      CFANode pCallNode) {
    // TODO Auto-generated method stub
    return getVariableReducedState(pExpandedState, pContext, pCallNode);
  }

  @Override
  public AbstractState getVariableExpandedStateForProofChecking(AbstractState pRootState, Block pReducedContext,
      AbstractState pReducedState) {
    // TODO Auto-generated method stub
    return getVariableExpandedState(pRootState, pReducedContext, pReducedState);
  }

  @Override
  public AbstractState rebuildStateAfterFunctionCall(AbstractState pRootState, AbstractState pEntryState,
      AbstractState pExpandedState, FunctionExitNode pExitLocation) {
    // TODO Auto-generated method stub
    RangeState rootState = (RangeState)pRootState;
    RangeState expandedState = (RangeState)pExpandedState;

    return expandedState.rebuildStateAfterFunctionCall(rootState, pExitLocation);
  }

}
