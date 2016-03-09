package org.tsinghua.cxcfan;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.cdt.core.dom.ast.IASTArraySubscriptExpression;
import org.eclipse.cdt.core.dom.ast.IASTBinaryExpression;
import org.eclipse.cdt.core.dom.ast.IASTCastExpression;
import org.eclipse.cdt.core.dom.ast.IASTConditionalExpression;
import org.eclipse.cdt.core.dom.ast.IASTExpression;
import org.eclipse.cdt.core.dom.ast.IASTFieldReference;
import org.eclipse.cdt.core.dom.ast.IASTFileLocation;
import org.eclipse.cdt.core.dom.ast.IASTFunctionCallExpression;
import org.eclipse.cdt.core.dom.ast.IASTFunctionDefinition;
import org.eclipse.cdt.core.dom.ast.IASTIdExpression;
import org.eclipse.cdt.core.dom.ast.IASTLiteralExpression;
import org.eclipse.cdt.core.dom.ast.IASTNode;
import org.eclipse.cdt.core.dom.ast.IASTUnaryExpression;
import org.sosy_lab.cpachecker.cfa.types.c.CNumericTypes;
import org.sosy_lab.cpachecker.cpa.range.IntType;

public class MutableASTNode {

	private IASTNode astNode;
	// we should have children.size() == template.size() + 1
	private MutableASTNode parent;
	private List<MutableASTNode> children;
	private List<String> template;
	
	private IntType nodeType;
	private boolean typeLock;
	
	public MutableASTNode(IASTNode node) {
		this.astNode = node;
		this.parent = null;
		this.children = new ArrayList<>();
		this.template = new ArrayList<>();
		
		this.nodeType = IntType.UNKNOWN;
		this.typeLock = false;
	}
	
	public void makeLeafNode() {
		template.add(astNode.getRawSignature());
	}
	
	public void addChild(MutableASTNode[] nodes) {
		// developer is responsible for correct sequence and number
		if(nodes.length == 0) {
			throw new AssertionError("Use makeLeafNode method instead!");
		}
		String rawString = astNode.getRawSignature();
		IASTFileLocation totalLoc = astNode.getFileLocation();
		int basePos = totalLoc.getNodeOffset();
		int startPos = 0, endPos = 0;
		for(int i = 0; i < nodes.length; i++) {
			IASTFileLocation subLoc = nodes[i].astNode.getFileLocation();
			endPos = subLoc.getNodeOffset() - basePos;
			try {
				template.add(rawString.substring(startPos, endPos));
			} catch(Exception ex) {
				ex.printStackTrace();
			}
			startPos = endPos + subLoc.getNodeLength();
			// don't forget add this child
			children.add(nodes[i]);
			nodes[i].parent = this;
		}
		endPos = totalLoc.getNodeLength();
		template.add(rawString.substring(startPos, endPos));
		// post-condition check
		assert (template.size() == children.size() + 1) : "inconsistent node!";
	}
	
	public MutableASTNode getParent() {
		return this.parent;
	}
	
	public int getChildrenSize() {
		return this.children.size();
	}
	
	public int getTemplateSize() {
		return this.template.size();
	}
	
	public String getTemplateString(int index) {
		if(index < 0 || index > template.size()) {
			throw new AssertionError("Invalid visiting index!");
		}
		return template.get(index);
	}
	
	public void setTemplateString(int index, String content) {
		if(index < 0 || index >= template.size()) {
			throw new AssertionError("Invalid visiting index!");
		}
		template.set(index, content);
	}
	
	public IntType getNodeType() {
		return this.nodeType;
	}
	
	public void setNodeType(IntType type) {
		this.nodeType = type;
	}
	
	public MutableASTNode getChild(int index) {
		if(index < 0 || index >= this.children.size()) {
			throw new AssertionError("Invalid visiting index!");
		}
		return this.children.get(index);
	}
	
	public String synthesize() {
		String result = template.get(0);
		for(int i = 1; i <= children.size(); i++) {
			result = result.concat(children.get(i - 1).synthesize()).concat(template.get(i));
		}
		return result;
	}
	
	public IASTNode getWrappedNode() {
		return this.astNode;
	}
	
	public int locateChild(MutableASTNode cand) {
		IASTFileLocation candLoc = cand.astNode.getFileLocation();
		int childrenNum = children.size();
		for(int i = 0; i < childrenNum; i++) {
			IASTFileLocation childLoc = children.get(i).astNode.getFileLocation();
			if(isLocationEqual(candLoc, childLoc)) {
				return i;
			}
		}
		// -1 means no ast node matches
		return -1;
	}
	
	private boolean isLocationEqual(IASTFileLocation loc1, IASTFileLocation loc2) {
		if(loc1.getNodeLength() == loc2.getNodeLength() && loc1.getNodeOffset() == loc2.getNodeOffset() && loc1.getFileName().equals(loc2.getFileName())) {
			return true;
		}
		return false;
	}
	
	public static MutableASTNode createMutableASTFromIASTNode(IASTNode node) {
		MutableASTNode mnode = new MutableASTNode(node);
		IASTNode[] children = node.getChildren();
		if(children.length == 0) {
			mnode.makeLeafNode();
			return mnode;
		}
		List<MutableASTNode> childList = new ArrayList<>();
		for(int i = 0; i < children.length; i++) {
			if(children[i].getFileLocation() != null && children[i].getFileLocation().getNodeLength() > 0) {
				// there are some strange cases --- may be CDT issue
				MutableASTNode childMNode = createMutableASTFromIASTNode(children[i]);
				childList.add(childMNode);
			}
		}
		MutableASTNode[] childArray = childList.toArray(new MutableASTNode[childList.size()]);
		mnode.addChild(childArray);
		return mnode;
	}
	
	public void updateNodeType(Map<String, IntType> varType, String funcName) {
		// If this is function definition, then we can record the function name
		// for generating qualified name of identifier
		String func = funcName;
		if(astNode instanceof IASTFunctionDefinition) {
			func = ((IASTFunctionDefinition)astNode).getDeclarator().getName().getRawSignature();
		}
		
		// FIRST, we should update the type of children
		for(int i = 0; i < children.size(); i++) {
			children.get(i).updateNodeType(varType, func);
		}
		// If the type of current node is locked, do nothing
		if(typeLock == true) {
			return;
		}
		// update the type based on different expression kinds
		if(!(astNode instanceof IASTExpression)) {
			return;
		}
		if(astNode instanceof IASTArraySubscriptExpression) {
			nodeType = IntType.interpret(((IASTArraySubscriptExpression) astNode).getExpressionType());
		} else if(astNode instanceof IASTBinaryExpression) {
			int optr = ((IASTBinaryExpression) astNode).getOperator();
			switch(optr) {
			// (1) assignment expressions
			case IASTBinaryExpression.op_assign:
			case IASTBinaryExpression.op_binaryAndAssign:
			case IASTBinaryExpression.op_binaryOrAssign:
			case IASTBinaryExpression.op_binaryXorAssign:
			case IASTBinaryExpression.op_plusAssign:
			case IASTBinaryExpression.op_minusAssign:
			case IASTBinaryExpression.op_multiplyAssign:
			case IASTBinaryExpression.op_divideAssign:
			case IASTBinaryExpression.op_moduloAssign:
			case IASTBinaryExpression.op_shiftLeftAssign:
			case IASTBinaryExpression.op_shiftRightAssign:
				nodeType = children.get(0).nodeType;
				break;
			// (2) arithmetic expressions
			case IASTBinaryExpression.op_plus:
			case IASTBinaryExpression.op_minus:
			case IASTBinaryExpression.op_multiply:
			case IASTBinaryExpression.op_divide:
			case IASTBinaryExpression.op_modulo:
			case IASTBinaryExpression.op_binaryAnd:
			case IASTBinaryExpression.op_binaryOr:
			case IASTBinaryExpression.op_binaryXor:
				nodeType = children.get(0).nodeType.mergeWith(children.get(1).nodeType);
				break;
			// (3) rational expressions
			case IASTBinaryExpression.op_equals:
			case IASTBinaryExpression.op_notequals:
			case IASTBinaryExpression.op_greaterEqual:
			case IASTBinaryExpression.op_greaterThan:
			case IASTBinaryExpression.op_lessEqual:
			case IASTBinaryExpression.op_lessThan:
			case IASTBinaryExpression.op_logicalAnd:
			case IASTBinaryExpression.op_logicalOr:
				nodeType = new IntType(CNumericTypes.INT);
				break;
			// (4) bit-shift operation
			case IASTBinaryExpression.op_shiftLeft:
			case IASTBinaryExpression.op_shiftRight:
				nodeType = children.get(0).nodeType.promoteInt();
				break;
			default:
				nodeType = IntType.UNKNOWN;
			}
		} else if(astNode instanceof IASTUnaryExpression) {
			int optr = ((IASTUnaryExpression) astNode).getOperator();
			switch(optr) {
			case IASTUnaryExpression.op_bracketedPrimary:
			case IASTUnaryExpression.op_prefixDecr:
			case IASTUnaryExpression.op_prefixIncr:
			case IASTUnaryExpression.op_postFixDecr:
			case IASTUnaryExpression.op_postFixIncr:
				nodeType = children.get(0).nodeType;
				break;
			case IASTUnaryExpression.op_plus:
			case IASTUnaryExpression.op_minus:
			case IASTUnaryExpression.op_tilde:
				nodeType = children.get(0).nodeType.promoteInt();
				break;
			case IASTUnaryExpression.op_not:
				nodeType = new IntType(CNumericTypes.INT);
				break;
			case IASTUnaryExpression.op_sizeof:
			case IASTUnaryExpression.op_alignOf:
				nodeType = IntType.SIZET;
				break;
			case IASTUnaryExpression.op_amper:
				nodeType = IntType.UNKNOWN;
				break;
			case IASTUnaryExpression.op_star: {
				nodeType = IntType.interpret(((IASTUnaryExpression) astNode).getExpressionType());
				break;
			}
			default:
				nodeType = IntType.UNKNOWN;
			}
		} else if(astNode instanceof IASTConditionalExpression) {
			IntType mergeType = children.get(1).nodeType.mergeWith(children.get(2).nodeType);
			mergeType = mergeType.promoteInt();
			nodeType = mergeType;
		} else if(astNode instanceof IASTFieldReference) {
			nodeType = IntType.interpret(((IASTFieldReference) astNode).getExpressionType());
		} else if(astNode instanceof IASTFunctionCallExpression) {
			nodeType = IntType.interpret(((IASTFunctionCallExpression) astNode).getExpressionType());
		} else if(astNode instanceof IASTIdExpression) {
			// we should obtain the updated type from variable table
			String idName = ((IASTIdExpression)astNode).getName().getRawSignature();
			if(func.length() > 0) {
				idName = func.concat("::").concat(idName);
			}
			IntType idType = varType.get(idName);
			if(idType != null) {
				nodeType = idType;
			} else {
				// this should not happen
				nodeType = IntType.UNKNOWN;
			}
		} else if(astNode instanceof IASTLiteralExpression) {
			nodeType = IntType.interpret(((IASTLiteralExpression) astNode).getExpressionType());
		} else if(astNode instanceof IASTCastExpression) {
			nodeType = IntType.interpret(((IASTCastExpression) astNode).getExpressionType());
		} else {
			// unknown expression type
			nodeType = IntType.UNKNOWN;
		}
	}
	
	public void assertType(FixSolution solution) {
		// classify FixSolution by its type
		int fixmode = solution.getFixMode();
		switch(fixmode) {
		case FixSolution.SPECIFIER: {
			// only cast expression should be addressed
			if(astNode instanceof IASTCastExpression) {
				nodeType = solution.getBaseType();
				typeLock = true;
			}
			break;
		}
		case FixSolution.SANITYCHK: {
			// nothing to do, since we want raw type information
			break;
		}
		case FixSolution.CONVERSION: {
			// two cases: numerical value and pointer
			if(solution.isInt()) {
				nodeType = solution.getBaseType();
				typeLock = true;
			} else if(solution.isPointer()) {
				IntType baseType = solution.getBaseType();
				int refLevel = solution.getRefLevel();
				MutableASTNode schNode = this;
				while(refLevel > 0 && schNode != null) {
					schNode = schNode.parent;
					IASTNode wrappedSchNode = schNode.astNode;
					if(wrappedSchNode instanceof IASTUnaryExpression) {
						if(((IASTUnaryExpression) wrappedSchNode).getOperator() == IASTUnaryExpression.op_star) {
							refLevel--;
						}
					}
				}
				// if reference level reduces to 0, we should specify the type of current expression by force
				schNode.nodeType = baseType;
				schNode.typeLock = true;
			}
			break;
		}
		default:
			// nothing to do because it is exceptional
		}
	}
	
}
