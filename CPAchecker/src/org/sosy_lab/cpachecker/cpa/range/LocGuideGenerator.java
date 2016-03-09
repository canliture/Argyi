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
import java.util.Map.Entry;

import org.json.JSONWriter;
import org.sosy_lab.common.collect.PersistentMap;
import org.sosy_lab.cpachecker.cfa.ast.FileLocation;

public class LocGuideGenerator {

  private String filePath;

  private JSONWriter jwriter;
  private BufferedWriter bwriter;

  // predefined names
  // >> FileLocation
  private static final String sl = "startline";
  private static final String el = "endline";
  private static final String fn = "filename";
  private static final String os = "offset";
  private static final String len = "length";
  // >> FixGuide
  private static final String kind = "kind";
  private static final String method = "method";
  private static final String bt = "basetype";
  private static final String rl = "reflevel";
  private static final String tg = "target";
  public static final String nullstr = "$NULLSTR$";

  public LocGuideGenerator(String filePath) {
    this.filePath = filePath;
    this.jwriter = null;
    this.bwriter = null;
  }

  public void generateLoc2Guide(PersistentMap<FileLocation, FixGuide> loc2Guide) {
    // STEP 1 : Initialize JSON writer
    try {
      bwriter = new BufferedWriter(new FileWriter(this.filePath));
      jwriter = new JSONWriter(bwriter);
      jwriter.array();
    } catch(IOException ex) {
      System.err.println("JSON writer initialization failed!");
      ex.printStackTrace();
    }

    // STEP 2: traverse the mapping for all entries
    for(Entry<FileLocation, FixGuide> entry : loc2Guide.entrySet()) {
      FileLocation loc = entry.getKey();
      FixGuide guide = entry.getValue();
      String kindOfGuide = guide.isIntVar() ? "int" : "ptr";
      String methodOfGuide = guide.needSanityCheck() ? "check" : "conv";
      String btOfGuide = guide.getBaseType().toOBJString();
      int refLevel = guide.getRefLevel();
      String tgOfGuide = guide.getTarget();
      if(tgOfGuide == null) {
        tgOfGuide = nullstr;
      }
      // write file location first
      jwriter.object()
        .key(sl).value(loc.getStartingLineNumber())
        .key(el).value(loc.getEndingLineNumber())
        .key(fn).value(loc.getFileName())
        .key(os).value(loc.getNodeOffset())
        .key(len).value(loc.getNodeLength())
        // ---------------------------------
        .key(kind).value(kindOfGuide)
        .key(method).value(methodOfGuide)
        .key(bt).value(btOfGuide)
        .key(rl).value(refLevel)
        .key(tg).value(tgOfGuide)
        .endObject();
    }

    // STEP 3: after traversing the structure, close the writer
    try {
      jwriter.endArray();
      bwriter.flush();
      bwriter.close();
    } catch(IOException ex) {
      System.err.println("JSON writer termination failed!");
      ex.printStackTrace();
    }
  }

}
