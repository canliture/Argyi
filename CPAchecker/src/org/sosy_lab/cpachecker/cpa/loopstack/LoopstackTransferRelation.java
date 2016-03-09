/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2014  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.loopstack;

import static com.google.common.base.Predicates.*;
import static com.google.common.collect.Iterables.filter;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionReturnEdge;
import org.sosy_lab.cpachecker.core.defaults.SingleEdgeTransferRelation;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.LoopStructure;
import org.sosy_lab.cpachecker.util.LoopStructure.Loop;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;

/**
 * Transfer relation for {@link LoopstackState}s:
 *  add to stack if we are entering the loop,
 *  pop from the stack if we are leaving the loop,
 *  identity otherwise.
 */
public class LoopstackTransferRelation extends SingleEdgeTransferRelation {

  private Map<CFAEdge, Loop> loopEntryEdges = null;
  private Map<CFAEdge, Loop> loopExitEdges = null;

  private Multimap<CFANode, Loop> loopHeads = null;

  private final int maxLoopIterations;
  private final int loopIterationsBeforeAbstraction;

  public LoopstackTransferRelation(
      int pLoopIterationsBeforeAbstraction,
      int maxLoopIterations, LoopStructure loops) {

    loopIterationsBeforeAbstraction = pLoopIterationsBeforeAbstraction;
    this.maxLoopIterations = maxLoopIterations;

    ImmutableMap.Builder<CFAEdge, Loop> entryEdges = ImmutableMap.builder();
    ImmutableMap.Builder<CFAEdge, Loop> exitEdges  = ImmutableMap.builder();
    ImmutableMultimap.Builder<CFANode, Loop> heads = ImmutableMultimap.builder();

    for (Loop l : loops.getAllLoops()) {
      // function edges do not count as incoming/outgoing edges
      Iterable<CFAEdge> incomingEdges = filter(l.getIncomingEdges(),
                                               not(instanceOf(CFunctionReturnEdge.class)));
      Iterable<CFAEdge> outgoingEdges = filter(l.getOutgoingEdges(),
                                               not(instanceOf(CFunctionCallEdge.class)));

      for (CFAEdge e : incomingEdges) {
        entryEdges.put(e, l);
      }
      for (CFAEdge e : outgoingEdges) {
        exitEdges.put(e, l);
      }
      for (CFANode h : l.getLoopHeads()) {
        heads.put(h, l);
      }
    }
    loopEntryEdges = entryEdges.build();
    loopExitEdges = exitEdges.build();
    loopHeads = heads.build();
  }


  @Override
  public Collection<? extends AbstractState> getAbstractSuccessorsForEdge(
      AbstractState pElement, Precision pPrecision, CFAEdge pCfaEdge)
      throws CPATransferException {

    if (pCfaEdge instanceof CFunctionCallEdge) {
      // such edges do never change loop stack status
      // Return here because they might be mis-classified as exit edges
      // because our idea of a loop contains only those nodes within the same function
      return Collections.singleton(pElement);
    }

    CFANode loc = pCfaEdge.getSuccessor();
    LoopstackState e = (LoopstackState)pElement;

    // comment by Xi Cheng
    // case 1: current edge exits a loop
    Loop oldLoop = loopExitEdges.get(pCfaEdge);
    if (oldLoop != null) {
      // modified by Xi Cheng
      if(!oldLoop.equals(e.getLoop())) {
        return Collections.singleton(pElement);
      }
      //assert oldLoop.equals(e.getLoop()) : e + " " + oldLoop + " " + pCfaEdge;
      e = e.getPreviousState();
    }

    if (pCfaEdge instanceof CFunctionReturnEdge) {
      // such edges may be real loop-exit edges "while () { return; }",
      // but never loop-entry edges
      // Return here because they might be mis-classified as entry edges
      return Collections.singleton(pElement);
    }

    // comment by Xi Cheng
    // case 2: current edge enters a loop (exclusive with case 1)
    Loop newLoop = loopEntryEdges.get(pCfaEdge);
    if (newLoop != null) {
      e = new LoopstackState(e, newLoop, 0, false);
    }

    // comment by Xi Cheng
    // case 3: the edge that points to the entry node of current loop
    //         (incl. entry edge and loop return edge)
    Collection<Loop> loops = loopHeads.get(loc);
    assert loops.size() <= 1;

    // The loop we are in corresponds to the currently traversed loop-head.
    if (loops.contains(e.getLoop())) {
      int newIteration;
      if (loopIterationsBeforeAbstraction != 0 &&
          e.getIteration() == loopIterationsBeforeAbstraction) {
        newIteration = loopIterationsBeforeAbstraction;
      } else {
        newIteration = e.getIteration() + 1;
      }

      // The "stop" flag is only ever read by the AssumptionStorageCPA.
      boolean stop = (maxLoopIterations > 0) &&
          (e.getIteration() >= maxLoopIterations);

      // comment: if we desire an unsound analysis, should we return an empty
      //          set of abstract states here?
      // Modified by Xi Cheng
      if(stop) {
        // discard extra iterations
        return Collections.emptySet();
      }

      // Update values for "newIteration" and "stop".
      e = new LoopstackState(
          e.getPreviousState(),
          e.getLoop(),
          newIteration,
          stop);
    }

    return Collections.singleton(e);
  }

  @Override
  public Collection<? extends AbstractState> strengthen(
      AbstractState pElement, List<AbstractState> pOtherElements,
      CFAEdge pCfaEdge, Precision pPrecision) {

    return null;
  }
}