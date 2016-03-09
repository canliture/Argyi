package org.tsinghua.cxcfan;

import org.sosy_lab.cpachecker.cpa.range.IntType;

public class FixSolution {

	// FixSolution depicts the concrete method of fixing program syntactic
	// elements. It should contain necessary components in original FixGuide
	// data structure.
	
	private boolean intFlag;
	private boolean ptrFlag;
	private int fixmode;
	private IntType baseType;
	// these fields are valid only if ptrFlag is set 
	private String target;
	private int refLevel;
	
	public static final int SPECIFIER = 1;
	public static final int SANITYCHK = 2;
	public static final int CONVERSION = 3;
	
	private static final String nullstr = "$NULLSTR$";
	
	public FixSolution(boolean intFlag, boolean ptrFlag, int fixmode, String typeString, String target, int refLevel) {
		this.fixmode = checkValidMode(fixmode);
		this.baseType = IntType.fromOBJString(typeString);
		this.intFlag = intFlag;
		this.ptrFlag = ptrFlag;
		this.target = target.equals(nullstr) ? null : target;
		this.refLevel = refLevel;
	}
	
	private FixSolution(boolean intFlag, boolean ptrFlag, int fixmode, IntType type, String target, int refLevel) {
		this.fixmode = checkValidMode(fixmode);
		this.baseType = type;
		this.intFlag = intFlag;
		this.ptrFlag = ptrFlag;
		this.target = target.equals(nullstr) ? null : target;
		this.refLevel = refLevel;
	}
	
	public FixSolution(boolean intFlag, boolean ptrFlag, int fixmode, IntType type) {
		this.fixmode = checkValidMode(fixmode);
		this.intFlag = intFlag;
		this.ptrFlag = ptrFlag;
		this.baseType = type;
		// for integer values, target is set to empty string and reference level is set to -1
		this.target = null;
		this.refLevel = -1;
	}
	
	private int checkValidMode(int mode) {
		if(mode != SPECIFIER && mode != SANITYCHK && mode != CONVERSION) {
			throw new AssertionError("Invalid fix mode!");
		}
		return mode;
	}
	
	public int getFixMode() {
		return this.fixmode;
	}
	
	public String getBaseTypeCanonicalSpecifier() {
		return this.baseType.toString();
	}
	
	public String getBaseTypeOBJString() {
		return this.baseType.toOBJString();
	}
	
	public IntType getBaseType() {
		return this.baseType;
	}
	
	public int getRefLevel() {
		return this.refLevel;
	}
	
	public String getTarget() {
		if(this.refLevel <= 0) {
			return null;
		}
		return this.target;
	}
	
	public void setBaseType(IntType type) {
		this.baseType = type;
	}
	
	public boolean isInt() {
		return this.intFlag;
	}
	
	public boolean isPointer() {
		return this.ptrFlag;
	}
	
	public FixSolution merge(FixSolution other) {
		if(this.intFlag == other.intFlag && this.ptrFlag == other.ptrFlag && this.fixmode == other.fixmode && this.refLevel == other.refLevel) {
			IntType newType = this.baseType.mergeWith(other.baseType);
			// we should also merge signedness
			if(this.baseType.getSign() || other.baseType.getSign()) {
				if(!newType.getSign()) {
					newType = newType.flipSign();
				}
			}
			if(this.target == null && other.target == null) {
				return new FixSolution(intFlag, ptrFlag, fixmode, newType, nullstr, refLevel);
			} else if(this.target != null && this.target.equals(other.target)) {
				return new FixSolution(intFlag, ptrFlag, fixmode, newType, target, refLevel);
			} else {
				return this;
			}
		}
		// otherwise just return itself
		return this;
	}
}
