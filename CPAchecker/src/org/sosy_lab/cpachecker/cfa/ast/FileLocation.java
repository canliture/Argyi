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
package org.sosy_lab.cpachecker.cfa.ast;

import static com.google.common.base.Preconditions.*;

import java.util.Arrays;
import java.util.List;

import com.google.common.collect.Iterables;

public class FileLocation implements Comparable<FileLocation> {

  private final int endingLine;
  private final String fileName;
  private final String niceFileName;
  private final int length;
  private final int offset;
  private final int startingLine;
  private final int startingLineInOrigin;

  public FileLocation(int pEndingLine, String pFileName, int pLength,
      int pOffset, int pStartingLine) {
    this(pEndingLine, pFileName, pFileName, pLength, pOffset, pStartingLine, pStartingLine);
  }

  public FileLocation(int pEndingLine, String pFileName, String pNiceFileName,
      int pLength, int pOffset, int pStartingLine, int pStartingLineInOrigin) {
    endingLine = pEndingLine;
    fileName = checkNotNull(pFileName);
    niceFileName = checkNotNull(pNiceFileName);
    length = pLength;
    offset = pOffset;
    startingLine = pStartingLine;
    startingLineInOrigin = pStartingLineInOrigin;
  }

  public static final FileLocation DUMMY = new FileLocation(0, "<none>", 0, 0, 0) {
    @Override
    public String toString() {
      return "none";
    }
  };

  public static final FileLocation MULTIPLE_FILES = new FileLocation(0, "<multiple files>", 0, 0, 0) {
    @Override
    public String toString() {
      return getFileName();
    }
  };

  // added by Xi Cheng 7/1/2016
  public static FileLocation merge(FileLocation loc1, FileLocation loc2) {
    List<FileLocation> locs = Arrays.asList(loc1, loc2);
    return merge(locs);
  }

  public static FileLocation merge(List<FileLocation> locations) {
    checkArgument(!Iterables.isEmpty(locations));

    String fileName = null;
    String niceFileName = null;
    int startingLine = Integer.MAX_VALUE;
    int startingLineInOrigin = Integer.MAX_VALUE;
    int endingLine = Integer.MIN_VALUE;
    // add two properties
    int startPos = Integer.MAX_VALUE;
    int endPos = Integer.MIN_VALUE;

    for (FileLocation loc : locations) {
      if (loc == DUMMY) {
        continue;
      }
      if (fileName == null) {
        fileName = loc.fileName;
        niceFileName = loc.niceFileName;
      } else if (!fileName.equals(loc.fileName)) {
        return MULTIPLE_FILES;
      }

      startingLine = Math.min(startingLine, loc.getStartingLineNumber());
      startingLineInOrigin = Math.min(startingLineInOrigin, loc.getStartingLineInOrigin());
      endingLine = Math.max(endingLine, loc.getEndingLineNumber());
      startPos = Math.min(startPos, loc.getNodeOffset());
      endPos = Math.max(endPos, loc.getNodeOffset() + loc.getNodeLength());
    }

    if (fileName == null) {
      // only DUMMY elements
      return DUMMY;
    }

    // modified by Xi Cheng, 7/1/2016
    // derive correct offset and length

    return new FileLocation(endingLine, fileName, niceFileName, endPos - startPos, startPos, startingLine, startingLineInOrigin);
  }

  public int getStartingLineInOrigin() {
    return startingLineInOrigin;
  }

  public int getEndingLineNumber() {
    return endingLine;
  }

  public String getFileName() {
    return fileName;
  }

  public int getNodeLength() {
    return length;
  }

  public int getNodeOffset() {
    return offset;
  }

  public int getStartingLineNumber() {
    return startingLine;
  }

  /* (non-Javadoc)
   * @see java.lang.Object#hashCode()
   */
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 7;
    result = prime * result + endingLine;
    result = prime * result + length;
    result = prime * result + offset;
    result = prime * result + startingLine;
    return result;
  }

  /* (non-Javadoc)
   * @see java.lang.Object#equals(java.lang.Object)
   */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }

    if (!(obj instanceof FileLocation)) {
      return false;
    }

    FileLocation other = (FileLocation) obj;

    return other.endingLine == endingLine
            && other.startingLine == startingLine
            && other.length == length
            && other.offset == offset;
  }

  // added by Xi Cheng, 4/1/2016
  public boolean contains(FileLocation other) {
    if(startingLine <= other.startingLine && endingLine >= other.endingLine &&
        offset <= other.offset && offset + length >= other.offset + other.length) {
      return true;
    }
    return false;
  }

  // modified by Xi Cheng, 4/1/2016
  @Override
  public String toString() {
    String prefix = niceFileName.isEmpty()
        ? ""
        : niceFileName + ", ";
    if (startingLine == endingLine) {
      //return prefix + "line " + startingLineInOrigin;
      return prefix + "line " + startingLine + ";offset " + offset + ";length " + length;
    } else {
      // TODO ending line number could be wrong
      //return prefix + "lines " + startingLineInOrigin + "-" + (endingLine -startingLine+startingLineInOrigin);
      return prefix + "lines " + startingLine + "-" + endingLine;
    }
  }

  // added by Xi Cheng, 29/12/2015
  @Override
  public int compareTo(FileLocation other) {

    if(this.startingLine > other.startingLine) {
      return 1;
    } else if(this.startingLine < other.startingLine) {
      return -1;
    }

    if(this.offset > other.offset) {
      return 1;
    } else if(this.offset < other.offset) {
      return -1;
    }

    if(this.length < other.length) {
      return 1;
    } else if(this.length > other.length) {
      return -1;
    }

    // if we reach here, this two file location can be thought to be the same
    return 0;

  }
}
