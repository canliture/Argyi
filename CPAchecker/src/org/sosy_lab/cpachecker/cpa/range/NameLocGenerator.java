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

public class NameLocGenerator {

  private String filePath;

  private JSONWriter jwriter;
  private BufferedWriter bwriter;

  // predefined names
  private static final String vn = "varname";
  // the following 5 members are belong to FileLocation
  private static final String sl = "startline";
  private static final String el = "endline";
  private static final String fn = "filename";
  private static final String os = "offset";
  private static final String len = "length";

  public NameLocGenerator(String filePath) {
    this.filePath = filePath;
    this.jwriter = null;
    this.bwriter = null;
  }

  public void generateName2Loc(PersistentMap<String, FileLocation> name2Loc) {
    // STEP 1: initialize JSON writer
    try {
      bwriter = new BufferedWriter(new FileWriter(this.filePath));
      jwriter = new JSONWriter(bwriter);
      // content to be written is a sequence of entries
      jwriter.array();
    } catch(IOException e) {
      System.err.println("JSON writer initialization failed!");
      e.printStackTrace();
    }

    // STEP 2: traverse the map structure for all entries
    for(Entry<String, FileLocation> entry : name2Loc.entrySet()) {
      String varName = entry.getKey();
      FileLocation location = entry.getValue();
      jwriter.object()
        .key(vn).value(varName)
        .key(sl).value(location.getStartingLineNumber())
        .key(el).value(location.getEndingLineNumber())
        .key(fn).value(location.getFileName())
        .key(os).value(location.getNodeOffset())
        .key(len).value(location.getNodeLength())
        .endObject();
    }

    // STEP 3: after traversing the structure, close the writer
    try {
      jwriter.endArray();
      bwriter.flush();
      bwriter.close();
    } catch (IOException ex) {
      System.err.println("JSON writer termination failed!");
      ex.printStackTrace();
    }

  }

}
