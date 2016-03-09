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

public class CompInteger implements Comparable<CompInteger> {

  private BigInteger value;

  /**
   * status denotes the special status of this integer, it can be
   *  0: this is a normal integer
   *  1: negative infinity
   *  2: positive infinity
   *  3: NaN (Not a Number) denotes a number which have indefinite value, in some cases NaN is used:
   *    (a) (+\infty) + (-\infty)
   *    (b) x / 0, where x is any number in CompInteger (including NaN itself)
   *  We have the following propositions:
   *    (a) NaN == NaN
   *    (b) NaN < +\infty
   *    (c) NaN > -\infty
   *    (d) NaN == x (x is a normal integer constant)
   */
  private int status;

  private final static int NORM = 0;
  private final static int NINF = 1;
  private final static int PINF = 2;
  private final static int NAN = 3;

  public static final CompInteger infpos = new CompInteger(PINF);
  public static final CompInteger infneg = new CompInteger(NINF);
  public static final CompInteger nan = new CompInteger(NAN);
  public static final CompInteger zero = new CompInteger(BigInteger.ZERO);
  public static final CompInteger one = new CompInteger(BigInteger.ONE);

  public CompInteger()
  {
    this.value = BigInteger.ZERO;
    this.status = NORM;
  }

  public CompInteger(int status) {
    this.value = BigInteger.ZERO;
    this.status = status;
  }

  public CompInteger(BigInteger val) {
    this.value = val;
    this.status = NORM;
  }

  public CompInteger(long val) {
    this.value = new BigInteger(String.valueOf(val));
    this.status = NORM;
  }

  public int getStatus() {
    return this.status;
  }

  public BigInteger getValue() {
    return this.value;
  }

  // Add support for program arithmetic operations
  public CompInteger add(CompInteger val) {
    if(this.status == NORM) {
      if(val.status == NORM) {
        return new CompInteger(this.value.add(val.value));
      } else {
        return new CompInteger(val.status);
      }
    } else if(this.status == NINF) {
      if(val.status == NORM || val.status == NINF) {
        return new CompInteger(NINF);
      } else {
        return new CompInteger(NAN);
      }
    } else if(this.status == PINF) {
      if(val.status == NORM || val.status == PINF) {
        return new CompInteger(PINF);
      } else {
        return new CompInteger(NAN);
      }
    } else {
      return new CompInteger(NAN);
    }
  }

  public CompInteger abs() {
    if(this.status == NORM) {
      return new CompInteger(this.value.abs());
    } else if(this.status == PINF || this.status == NINF) {
      return new CompInteger(PINF);
    } else {
      return new CompInteger(NAN);
    }
  }

  public int signum() {
    if(this.status == NORM) {
      return this.value.signum();
    } else if(this.status == NINF) {
      return -1;
    } else if(this.status == PINF) {
      return 1;
    } else {
      throw new RuntimeException("NaN has no signal!");
    }
  }

  public CompInteger divide(CompInteger val) {
    if(this.status == NORM) {
      if(val.status == NAN) {
        return new CompInteger(NAN);
      } else if(val.status == NINF || val.status == PINF) {
        return new CompInteger(BigInteger.ZERO);
      } else {
        if(val.value.compareTo(BigInteger.ZERO) == 0) {
          return new CompInteger(NAN);
        } else {
          return new CompInteger(this.value.divide(val.value));
        }
      }
    } else if(this.status == NINF) {
      if(val.status == PINF || val.status == NINF || val.status == NAN) {
        return new CompInteger(NAN);
      } else {
        switch(val.value.compareTo(BigInteger.ZERO)) {
        case 0:
          return new CompInteger(NAN);
        case -1:
          return new CompInteger(PINF);
        default:
          return new CompInteger(NINF);
        }
      }
    } else if(this.status == PINF) {
      if(val.status == PINF || val.status == NINF || val.status == NAN) {
        return new CompInteger(NAN);
      } else {
        switch(val.value.compareTo(BigInteger.ZERO)) {
        case 0:
          return new CompInteger(NAN);
        case 1:
          return new CompInteger(PINF);
        default:
          return new CompInteger(NINF);
        }
      }
    } else {
      return new CompInteger(NAN);
    }
  }

  public CompInteger max(CompInteger val) {
    if(this.status == NORM) {
      if(val.status == NORM) {
        return new CompInteger(this.value.max(val.value));
      } else if(val.status == PINF) {
        return new CompInteger(PINF);
      } else {
        // a normal constant compares with NaN. NaN's value is undetermined
        return new CompInteger(this.value);
      }
    } else if(this.status == NINF) {
      if(val.status == NORM) {
        return new CompInteger(val.value);
      } else if(val.status == PINF) {
        return new CompInteger(PINF);
      } else {
        return new CompInteger(NINF);
      }
    } else if(this.status == PINF) {
      return new CompInteger(PINF);
    } else {
      if(val.status == NORM) {
        return new CompInteger(val.value);
      } else {
        return new CompInteger(val.status);
      }
    }
  }

  public CompInteger min(CompInteger val) {
    if(this.status == NORM) {
      if(val.status == NORM) {
        return new CompInteger(this.value.min(val.value));
      } else if(val.status == NINF) {
        return new CompInteger(NINF);
      } else {
        return new CompInteger(this.value);
      }
    } else if(this.status == NINF) {
      return new CompInteger(NINF);
    } else if(this.status == PINF) {
      if(val.status == NORM) {
        return new CompInteger(val.value);
      } else if(val.status == NINF) {
        return new CompInteger(NINF);
      } else {
        return new CompInteger(PINF);
      }
    } else {
      if(val.status == NORM) {
        return new CompInteger(val.value);
      } else {
        return new CompInteger(val.status);
      }
    }
  }

  /**
   * mod function always returns a non-negative integer, different from remainder function
   */
  public CompInteger mod(CompInteger val) {
    if(this.status == NORM) {
      if(val.status == NORM) {
        if(val.value.compareTo(BigInteger.ZERO) == 0) {
          return new CompInteger(NAN);
        } else {
          return new CompInteger(this.value.mod(val.value));
        }
      } else if(val.status == NINF || val.status == PINF) {
        return new CompInteger(this.value);
      } else {
        return new CompInteger(NAN);
      }
    } else {
      return new CompInteger(NAN);
    }
  }

  public CompInteger multiply(CompInteger val) {
    if(this.status == NORM) {
      int sig = this.value.signum();
      if(val.status == NORM) {
        return new CompInteger(this.value.multiply(val.value));
      } else if(val.status == NINF) {
        if(sig == 1) {
          return new CompInteger(NINF);
        } else if(sig == 0) {
          return new CompInteger(BigInteger.ZERO);
        } else {
          return new CompInteger(PINF);
        }
      } else if(val.status == PINF) {
        if(sig == 1) {
          return new CompInteger(PINF);
        } else if(sig == 0) {
          return new CompInteger(BigInteger.ZERO);
        } else {
          return new CompInteger(NINF);
        }
      } else {
        if(sig == 0) {
          return new CompInteger(BigInteger.ZERO);
        } else {
          return new CompInteger(NAN);
        }
      }
    } else if(this.status == NINF) {
      if(val.status == NORM) {
        int sig = val.value.signum();
        if(sig == 0) {
          return new CompInteger(BigInteger.ZERO);
        } else if(sig == 1) {
          return new CompInteger(NINF);
        } else {
          return new CompInteger(PINF);
        }
      } else if(val.status == NINF) {
        return new CompInteger(PINF);
      } else if(val.status == PINF) {
        return new CompInteger(NINF);
      } else {
        return new CompInteger(NAN);
      }
    } else if(this.status == PINF) {
      if(val.status == NORM) {
        int sig = val.value.signum();
        if(sig == 0) {
          return new CompInteger(BigInteger.ZERO);
        } else if(sig == 1) {
          return new CompInteger(PINF);
        } else {
          return new CompInteger(NINF);
        }
      } else if(val.status == NINF) {
        return new CompInteger(NINF);
      } else if(val.status == PINF) {
        return new CompInteger(PINF);
      } else {
        return new CompInteger(NAN);
      }
    } else {
      if(val.status == NORM && val.value.signum() == 0) {
        return new CompInteger(BigInteger.ZERO);
      } else {
        return new CompInteger(NAN);
      }
    }
  }

  public CompInteger negate() {
    if(this.status == NORM) {
      return new CompInteger(this.value.negate());
    } else if(this.status == NINF) {
      return new CompInteger(PINF);
    } else if(this.status == PINF) {
      return new CompInteger(NINF);
    } else {
      return new CompInteger(NAN);
    }
  }

  /**
   *  Different from mod function, this function is equivalent to % operator
   *  negative results are permitted
   */
  public CompInteger remainder(CompInteger val) {
    if(this.status == NORM) {
      if(val.status == NORM) {
        if(val.value.signum() == 0) {
          return new CompInteger(NAN);
        } else {
          return new CompInteger(this.value.remainder(val.value));
        }
      } else if(val.status == PINF) {
        return new CompInteger(this.value);
      } else if(val.status == NINF) {
        return new CompInteger(NINF);
      } else {
        return new CompInteger(NAN);
      }
    } else {
      return new CompInteger(NAN);
    }
  }

  public CompInteger subtract(CompInteger val) {
    if(this.status == NORM) {
      if(val.status == NORM) {
        return new CompInteger(this.value.subtract(val.value));
      } else if(val.status == NINF) {
        return new CompInteger(PINF);
      } else if(val.status == PINF) {
        return new CompInteger(NINF);
      } else {
        return new CompInteger(NAN);
      }
    } else if(this.status == NINF) {
      if(val.status == NORM || val.status == PINF) {
        return new CompInteger(NINF);
      } else {
        return new CompInteger(NAN);
      }
    } else if(this.status == PINF) {
      if(val.status == NORM || val.status == NINF) {
        return new CompInteger(PINF);
      } else {
        return new CompInteger(NAN);
      }
    } else {
      return new CompInteger(NAN);
    }
  }

  public CompInteger shiftLeft(int n) {
    // in C, if n is negative, the behavior of shift operation is undefined
    // TODO: an inappropriate bitwise shift may produce a result exceeding the capacity of BigInteger
    if(this.status == NORM) {
      try {
        CompInteger output = new CompInteger(this.value.shiftLeft(n));
        return output;
      } catch(ArithmeticException ex) {
        if(this.value.signum() > 0) {
          return CompInteger.infpos;
        } else {
          // signum() == 0 is impossible, otherwise exception should not be triggered
          return CompInteger.infneg;
        }
      }
    } else {
      return new CompInteger(this.status);
    }
  }

  public CompInteger shiftRight(int n) {
    if(this.status == NORM) {
      try {
        CompInteger output = new CompInteger(this.value.shiftRight(n));
        return output;
      } catch(ArithmeticException ex) {
        if(this.value.signum() > 0) {
          return CompInteger.infpos;
        } else {
          return CompInteger.infneg;
        }
      }
    } else {
      return new CompInteger(this.status);
    }
  }

  /**
   * returns null if the value cannot fit INT type (out-of-range)
   * @return value
   */
  public Integer intValue() {
    if(this.status == NORM) {
      BigInteger val = this.value;
      BigInteger int_max = BigInteger.valueOf(Integer.MAX_VALUE);
      BigInteger int_min = BigInteger.valueOf(Integer.MIN_VALUE);
      if(val.compareTo(int_max) <= 0 && val.compareTo(int_min) >= 0) {
        return val.intValue();
      } else {
        return null;
      }
    } else {
      return null;
    }
  }

  @Override
  public String toString() {
    if(this.status == NORM) {
      return this.value.toString();
    } else if(this.status == NINF) {
      return new String("-inf");
    } else if(this.status == PINF) {
      return new String("+inf");
    } else {
      return new String("NaN");
    }
  }

  @Override
  public int compareTo(CompInteger other) {
    if(this.status == NINF) {
      if(other.status == NINF) {
        return 0;
      } else if(other.status == NAN) {
        throw new RuntimeException("NaN is not comparable to any other values.");
      } else {
        return -1;
      }
    } else if(this.status == PINF) {
      if(other.status == PINF) {
        return 0;
      } else if(other.status == NAN) {
        throw new RuntimeException("NaN is not comparable to any other values.");
      } else {
        return 1;
      }
    } else if(this.status == NAN) {
      throw new RuntimeException("NaN is not comparable to any other values.");
    } else {
      // status == 0
      if(other.status == NINF) {
        return 1;
      } else if(other.status == PINF) {
        return -1;
      } else if(other.status == NAN) {
        throw new RuntimeException("NaN is not comparable to any other values.");
      } else {
        return (this.value.compareTo(other.value));
      }
    }
  }

  public int compareTo(BigInteger bigint) {
    if(this.status == NINF) {
      return -1;
    } else if(this.status == PINF) {
      return 1;
    } else if(this.status == NAN) {
      throw new RuntimeException("NaN is not comparable to any other values.");
    } else {
      return this.value.compareTo(bigint);
    }
  }

  /**
   * The equals function is different from compareTo function:
   * In compareTo function two integers equal in absint level;
   * However, this function is used to match integer objects.
   */
  @Override
  public boolean equals(Object other) {
    if(other != null && getClass().equals(other.getClass())) {
      CompInteger another = (CompInteger)other;
      if(this.status != another.status) {
        return false;
      }
      if(this.status == NORM) {
        return this.value.equals(another.value);
      }
      return true;
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    // TODO Auto-generated method stub
    return super.hashCode();
  }

}