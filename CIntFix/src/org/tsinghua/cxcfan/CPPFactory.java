package org.tsinghua.cxcfan;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import org.eclipse.cdt.core.dom.ast.IASTBinaryExpression;
import org.eclipse.cdt.core.dom.ast.IASTFunctionDefinition;
import org.eclipse.cdt.core.dom.ast.IASTNode;
import org.eclipse.cdt.core.dom.ast.IASTReturnStatement;
import org.eclipse.cdt.core.dom.ast.IASTTranslationUnit;
import org.eclipse.cdt.core.dom.ast.IASTUnaryExpression;
import org.eclipse.cdt.core.dom.ast.gnu.c.GCCLanguage;
import org.eclipse.cdt.core.parser.DefaultLogService;
import org.eclipse.cdt.core.parser.FileContent;
import org.eclipse.cdt.core.parser.IParserLogService;
import org.eclipse.cdt.core.parser.IScannerInfo;
import org.eclipse.cdt.core.parser.IncludeFileContentProvider;
import org.eclipse.cdt.core.parser.ScannerInfo;

public class CPPFactory {

	private String tuName;
	
	private static final String retVarNameForMain = "__INTFIX_MAINRET";
	private static final String retStatementLabel = "__retpos_main";
	
	public CPPFactory(String tuName) {
		this.tuName = tuName;
	}
	
	public void process() {
		
		File tuFile = new File(tuName);
		if(!tuFile.exists()) {
			throw new AssertionError("Failed to find the translation unit file: " + tuName);
		}
		
		try {
			FileContent content = FileContent.createForExternalFileLocation(tuName);
			IScannerInfo scanner = new ScannerInfo();
			IParserLogService log = new DefaultLogService();
			IncludeFileContentProvider provider = IncludeFileContentProvider.getEmptyFilesProvider();
			IASTTranslationUnit unit = GCCLanguage.getDefault().getASTTranslationUnit(content, scanner, provider, null, 8, log);
			MutableASTNode mast = MutableASTNode.createMutableASTFromIASTNode(unit);
			
			// traverse this structure and make changes to program text!
			eliminateOperators(mast);
			
			// we find that if main function has multiple return branches, analysis result cannot be correctly output
			sequentializeRetForMain(mast);
			
			// write the transformed code back to the file
			// MODE: override mode
			String backupTu = tuName;
			BufferedWriter bw = new BufferedWriter(new FileWriter(backupTu));
			bw.write(mast.synthesize());
			bw.flush();
			bw.close();
			
		} catch(Exception ex) {
			System.err.println("Failed to load and process specified translation unit: " + tuName);
			ex.printStackTrace();
		}
		
	}
	
	private void sequentializeRetForMain(MutableASTNode mast) {
		// (1) find the AST node corresponding to main function
		MutableASTNode mainFunc = findMainFunctionNode(mast);
		// (2) insert declaration of temporary variable for return values
		int childrenNum = mainFunc.getChildrenSize();
		MutableASTNode funcBodyNode = mainFunc.getChild(childrenNum - 1);
		if(funcBodyNode.getChildrenSize() > 0) {
			String marginalText = funcBodyNode.getTemplateString(0);
			if(marginalText.length() < 1) {
				throw new AssertionError("Illegal function body -- missing curly braces.");
			}
			// collect empty characters
			int endPos = 1;
			while(endPos < marginalText.length()) {
				if(!isEmptyCharacter(marginalText.charAt(endPos))) {
					break;
				}
				endPos++;
			}
			String spacePrefix = marginalText.substring(1, endPos);
			String newDeclStr = spacePrefix.concat("int ").concat(retVarNameForMain).concat(" = 0;");
			marginalText = insertTextInSpecifiedPosition(marginalText, newDeclStr, 1);
			funcBodyNode.setTemplateString(0, marginalText);
			// also, insert the return statement at the end of function body
			int lastTemplateIdx = funcBodyNode.getTemplateSize() - 1;
			String tailText = funcBodyNode.getTemplateString(lastTemplateIdx);
			String newRetStr = spacePrefix.concat(retStatementLabel).concat(": ").concat("return ").concat(retVarNameForMain).concat(";");
			tailText = insertTextInSpecifiedPosition(tailText, newRetStr, 0);
			funcBodyNode.setTemplateString(lastTemplateIdx, tailText);
		}
		// (3) scan for return statement, rewrite them with assignment along with a goto statement
		replaceReturnWithAssignment(funcBodyNode);
	}
	
	private void replaceReturnWithAssignment(MutableASTNode node) {
		IASTNode wrappedNode = node.getWrappedNode();
		if(wrappedNode instanceof IASTReturnStatement) {
			if(node.getChildrenSize() < 1) {
				throw new AssertionError("Invalid return statement in main function!");
			}
			String headText = node.getTemplateString(0);
			int returnPos = headText.indexOf("return");
			if(returnPos == -1) {
				throw new AssertionError("Incomplete return statement -- missing return keyword.");
			}
			headText = headText.substring(0, returnPos).concat(retVarNameForMain).concat(" = ");
			node.setTemplateString(0, headText);
			String tailText = node.getTemplateString(1);
			// find the semicolon at the end of code line
			int endPos = 0;
			while(endPos < tailText.length()) {
				if(tailText.charAt(endPos) == ';') {
					break;
				}
				endPos++;
			}
			if(endPos != tailText.length()) {
				// normally insert goto statement behind the semicolon
				
				tailText = insertTextInSpecifiedPosition(tailText, " goto ".concat(retStatementLabel).concat(";"), endPos + 1);
				node.setTemplateString(1, tailText);
			} else {
				// unexpected, we cannot find a semicolon!
				throw new AssertionError("Incomplete return statement -- missing semicolon.");
			}
			return;
		}
		// it is unnecessary to modify any other AST nodes
		int childNum = node.getChildrenSize();
		for(int i = 0; i < childNum; i++) {
			replaceReturnWithAssignment(node.getChild(i));
		}
		// nothing to do for current node
		return;
	}
	
	private boolean isEmptyCharacter(char ch) {
		if(ch == '\f' || ch == '\r' || ch == '\t' || ch == '\n' || ch == ' ') {
			return true;
		}
		return false;
	}
	
	private String insertTextInSpecifiedPosition(String origText, String segment, int index) {
		if(index < 0 || index > origText.length()) {
			throw new AssertionError("Failed to insert text in the specified position!");
		}
		return origText.substring(0, index).concat(segment).concat(origText.substring(index));
	}
	
	private MutableASTNode findMainFunctionNode(MutableASTNode node) {
		IASTNode wrappedNode = node.getWrappedNode();
		if(wrappedNode instanceof IASTFunctionDefinition) {
			// when visiting function definition node, this function should always terminate
			String funcName = node.getChild(1).getChild(0).getWrappedNode().getRawSignature();
			if(funcName.equals("main")) {
				return node;
			} else {
				return null;
			}
		}
		// for other cases, traverse children of current node
		MutableASTNode resultNode;
		for(int i = 0; i < node.getChildrenSize(); i++) {
			resultNode = findMainFunctionNode(node.getChild(i));
			if(resultNode != null) {
				return resultNode;
			}
		}
		// none of children contains AST node of main function
		return null;
	}
	
	private void eliminateOperators(MutableASTNode mast) {
		IASTNode wrappedNode = mast.getWrappedNode();
		if(wrappedNode instanceof IASTBinaryExpression) {
			eliminateOperators(mast.getChild(0));
			eliminateOperators(mast.getChild(1));
			int optr = ((IASTBinaryExpression) wrappedNode).getOperator();
			switch(optr) {
			case IASTBinaryExpression.op_binaryAndAssign: {
				String op1Str = mast.getChild(0).synthesize();
				mast.setTemplateString(0, op1Str.concat(" = ("));
				mast.setTemplateString(1, " & (");
				mast.setTemplateString(2, "))");
				return;
			}
			case IASTBinaryExpression.op_binaryOrAssign: {
				String op1Str = mast.getChild(0).synthesize();
				mast.setTemplateString(0, op1Str.concat(" = ("));
				mast.setTemplateString(1, " | (");
				mast.setTemplateString(2, "))");
				return;
			}
			case IASTBinaryExpression.op_binaryXorAssign: {
				String op1Str = mast.getChild(0).synthesize();
				mast.setTemplateString(0, op1Str.concat(" = ("));
				mast.setTemplateString(1, " ^ (");
				mast.setTemplateString(2, "))");
				return;
			}
			case IASTBinaryExpression.op_plusAssign: {
				String op1Str = mast.getChild(0).synthesize();
				mast.setTemplateString(0, op1Str.concat(" = ("));
				mast.setTemplateString(1, " + (");
				mast.setTemplateString(2, "))");
				return;
			}
			case IASTBinaryExpression.op_minusAssign: {
				String op1Str = mast.getChild(0).synthesize();
				mast.setTemplateString(0, op1Str.concat(" = ("));
				mast.setTemplateString(1, " - (");
				mast.setTemplateString(2, "))");
				return;
			}
			case IASTBinaryExpression.op_multiplyAssign: {
				String op1Str = mast.getChild(0).synthesize();
				mast.setTemplateString(0, op1Str.concat(" = ("));
				mast.setTemplateString(1, " * (");
				mast.setTemplateString(2, "))");
				return;
			}
			case IASTBinaryExpression.op_divideAssign: {
				String op1Str = mast.getChild(0).synthesize();
				mast.setTemplateString(0, op1Str.concat(" = ("));
				mast.setTemplateString(1, " / (");
				mast.setTemplateString(2, "))");
				return;
			}
			case IASTBinaryExpression.op_moduloAssign: {
				String op1Str = mast.getChild(0).synthesize();
				mast.setTemplateString(0, op1Str.concat(" = ("));
				mast.setTemplateString(1, " % (");
				mast.setTemplateString(2, "))");
				return;
			}
			case IASTBinaryExpression.op_shiftLeftAssign: {
				String op1Str = mast.getChild(0).synthesize();
				mast.setTemplateString(0, op1Str.concat(" = ("));
				mast.setTemplateString(1, " << (");
				mast.setTemplateString(2, "))");
				return;
			}
			case IASTBinaryExpression.op_shiftRightAssign: {
				String op1Str = mast.getChild(0).synthesize();
				mast.setTemplateString(0, op1Str.concat(" = ("));
				mast.setTemplateString(1, " >> (");
				mast.setTemplateString(2, "))");
				return;
			}
			default: {
				// nothing should be changed
				return;
			}
			}
		} else if(wrappedNode instanceof IASTUnaryExpression) {
			eliminateOperators(mast.getChild(0));
			int optr = ((IASTUnaryExpression) wrappedNode).getOperator();
			switch(optr) {
			case IASTUnaryExpression.op_postFixIncr:
			case IASTUnaryExpression.op_prefixIncr: {
				String opStr = mast.getChild(0).synthesize();
				mast.setTemplateString(0, "(".concat(opStr).concat(" = "));
				mast.setTemplateString(1, " + 1".concat(")"));
				return;
			}
			case IASTUnaryExpression.op_postFixDecr:
			case IASTUnaryExpression.op_prefixDecr: {
				String opStr = mast.getChild(0).synthesize();
				mast.setTemplateString(0, "(".concat(opStr).concat(" = "));
				mast.setTemplateString(1, " - 1".concat(")"));
				return;
			}
			case IASTUnaryExpression.op_not: {
				mast.setTemplateString(0, "((");
				mast.setTemplateString(1, " ) == 0)");
				return;
			}
			default: {
				// nothing should be changed
				return;
			}
			}
		} else {
			int childNum = mast.getChildrenSize();
			for(int i = 0; i < childNum; i++) {
				eliminateOperators(mast.getChild(i));
			}
			// nothing to do for this node
			return;
		}
	}
	
}
