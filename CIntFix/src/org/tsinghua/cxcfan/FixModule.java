package org.tsinghua.cxcfan;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.cdt.core.dom.ast.IASTBinaryExpression;
import org.eclipse.cdt.core.dom.ast.IASTCastExpression;
import org.eclipse.cdt.core.dom.ast.IASTFileLocation;
import org.eclipse.cdt.core.dom.ast.IASTFunctionDefinition;
import org.eclipse.cdt.core.dom.ast.IASTNode;
import org.eclipse.cdt.core.dom.ast.IASTParameterDeclaration;
import org.eclipse.cdt.core.dom.ast.IASTSimpleDeclaration;
import org.eclipse.cdt.core.dom.ast.IASTTranslationUnit;
import org.eclipse.cdt.core.dom.ast.IASTUnaryExpression;
import org.eclipse.cdt.core.dom.ast.gnu.c.GCCLanguage;
import org.eclipse.cdt.core.parser.DefaultLogService;
import org.eclipse.cdt.core.parser.FileContent;
import org.eclipse.cdt.core.parser.IParserLogService;
import org.eclipse.cdt.core.parser.IScannerInfo;
import org.eclipse.cdt.core.parser.IncludeFileContentProvider;
import org.eclipse.cdt.core.parser.ScannerInfo;
import org.json.JSONArray;
import org.json.JSONObject;
import org.sosy_lab.cpachecker.cfa.ast.FileLocation;
import org.sosy_lab.cpachecker.cpa.range.IntType;
import org.sosy_lab.cpachecker.cpa.range.RangeTransferRelation;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

public class FixModule {

	/**
	 * The file name of translation unit to be fixed
	 */
	private String tuName;
	
	/**
	 * The folder of output metadata by range CPA
	 */
	private String fileNameWithoutExt;
	
	/**
	 * The following three names are files output by range CPA
	 */
	private static final String smt2 = "constraint.smt2";
	private static final String name2Locjson = "name2loc.json";
	private static final String loc2Guidejson = "loc2guide.json";
	
	/**
	 * Fixed field names in JSON
	 */
	private static final String vn = "varname";
	private static final String sl = "startline";
	private static final String el = "endline";
	private static final String fn = "filename";
	private static final String os = "offset";
	private static final String len = "length";
	//--------------------------------------------
	private static final String kind = "kind";
	private static final String method = "method";
	private static final String bt = "basetype";
	private static final String rl = "reflevel";
	private static final String tg = "target";
	
	// SMT solver: Z3
	private static final String z3 = "z3";
	
	/**
	 * The followings are format control things
	 */
	private static final String renamePrefix = "__intfix_repl_";
	private static final String checkPrefix = "__INTCHECK_";
	private static final String lshiftFuncName = "__INTLEFTSHIFT";
	private static final String rshiftFuncName = "__INTRIGHTSHIFT";
	private static final String convPattern = "(%s)";
	
	private static final String declarations = "typedef unsigned long size_t;\nextern int __INTCHECK_INT_S(long long signed int x);\nextern int __INTCHECK_INT_U(long long unsigned int x);\nextern unsigned int __INTCHECK_UINT_S(long long signed int x);\nextern unsigned int __INTCHECK_UINT_U(long long unsigned int x);\nextern short __INTCHECK_SHORT_S(long long signed int x);\nextern short __INTCHECK_SHORT_U(long long unsigned int x);\nextern unsigned short __INTCHECK_USHORT_S(long long signed int x);\nextern unsigned short __INTCHECK_USHORT_U(long long unsigned int x);\nextern signed char __INTCHECK_CHAR_S(long long signed int x);\nextern signed char __INTCHECK_CHAR_U(long long unsigned int x);\nextern unsigned char __INTCHECK_UCHAR_S(long long signed int x);\nextern unsigned char __INTCHECK_UCHAR_U(long long unsigned int x);\nextern long int __INTCHECK_LINT_S(long long signed int x);\nextern long int __INTCHECK_LINT_U(long long unsigned int x);\nextern long unsigned int __INTCHECK_ULINT_S(long long signed int x);\nextern long unsigned int __INTCHECK_ULINT_U(long long unsigned int x);\nextern long long int __INTCHECK_LLINT_S(long long signed int x);\nextern long long int __INTCHECK_LLINT_U(long long unsigned int x);\nextern long long unsigned int __INTCHECK_ULLINT_S(long long signed int x);\nextern long long unsigned int __INTCHECK_ULLINT_U(long long unsigned int x);\nextern size_t __INTCHECK_INDEX_S(long long signed int x);\nextern size_t __INTCHECK_INDEX_U(long long unsigned int x);\nextern long long unsigned int __INTLEFTSHIFT(long long unsigned int op1, long long unsigned int op2);\nextern long long unsigned int __INTRIGHTSHIFT(long long unsigned int op1, long long unsigned int op2);\n";
	
	public FixModule(String tuName, String fileNameWithoutExt) {
		this.tuName = tuName;
		this.fileNameWithoutExt = fileNameWithoutExt;
	}
	
	/**
	 * Run this fixing module. Each module should run only once
	 */
	public long run() {
		// 4 phases: (1) load two JSONs and generate the data structure
		//           (2) solve SMT constraint and parse the results
		//           (3) generate new fix guides based on constraint solving
		//           (4) fix the translation unit according to location and fix guide information
		
		// STEP 1: load JSONs
		String name2LocPath = fileNameWithoutExt + name2Locjson;
		Map<String, FileLocation> name2Loc = loadName2LocMapping(name2LocPath);
		String loc2GuidePath = fileNameWithoutExt + loc2Guidejson;
		Multimap<FileLocation, FixSolution> loc2Sol = loadLoc2GuideMapping(loc2GuidePath);
		
		// STEP 2: constraint solving and parsing solving results
		String smt2Path = fileNameWithoutExt + smt2;
		Map<String, IntType> solveResult = new HashMap<>();
		try {
			solveResult = typeConstraintSolving(smt2Path);
		} catch(IOException ex) {
			System.err.println("Failed to solve or parse type constraints!");
			ex.printStackTrace();
		}
		
		// STEP3: Generate FixSolution's for specifier alternation (from constraint solving)
		for(Entry<String, IntType> entry : solveResult.entrySet()) {
			String varName = entry.getKey();
			IntType targetType = entry.getValue();
			FileLocation loc = name2Loc.get(varName);
			if(loc == null) {
				System.err.println("Unexpected identifier: " + varName);
				// we ignore such case for robustness
				continue;
			}
			FixSolution sol = new FixSolution(true, false, FixSolution.SPECIFIER, targetType);
			// OK, add new information into loc2Sol mapping
			loc2Sol.put(loc, sol);
		}
		
		// STEP 3.5: merge values in multimap loc2Sol
		Multimap<FileLocation, FixSolution> refinedLoc2Sol = ArrayListMultimap.create();
		// for solution merge
		List<FixSolution> specifierList = new ArrayList<>();
		List<FixSolution> sanitychkList = new ArrayList<>();
		List<FixSolution> convList = new ArrayList<>();
		for(FileLocation keyLoc : loc2Sol.keySet()) {
			Collection<FixSolution> solutions = loc2Sol.get(keyLoc);
			List<FixSolution> solList = new ArrayList<>(solutions);
			if(solList.size() == 1) {
				refinedLoc2Sol.put(keyLoc, solList.get(0));
				continue;
			}
			// multiple fix solutions, we have to merge some of them
			// (1) Each category should have only one instance;
			// (2) SPECIFIER and SANITYCHK can co-exist. For example: func((short)value)
			// (3) Multiple SPECIFIER should be merged by picking the largest type
			// (4) Multiple SANITYCHK cannot occur
			// (5) Multiple CONVERSION should be merged as (3)
			specifierList.clear();
			sanitychkList.clear();
			convList.clear();
			for(int i = 0; i < solList.size(); i++) {
				FixSolution sol = solList.get(i);
				int fixmode = sol.getFixMode();
				switch(fixmode) {
				case FixSolution.SPECIFIER:
					specifierList.add(sol);
					break;
				case FixSolution.SANITYCHK:
					sanitychkList.add(sol);
					break;
				default:
					convList.add(sol);
				}
			}
			FixSolution mergedSpecifier = mergeSolutions(specifierList);
			FixSolution mergedSanitychk = mergeSolutions(sanitychkList);
			FixSolution mergedConv = mergeSolutions(convList);
			// conversion and specifier should not co-exist. In order to prevent double conversion,
			// we should keep the specifier solution only.
			if(mergedConv != null && mergedSpecifier != null) {
				IntType newType = mergedConv.getBaseType().mergeWith(mergedSpecifier.getBaseType());
				if(!newType.isNotOverlong()) {
					mergedSpecifier.setBaseType(newType);
					refinedLoc2Sol.put(keyLoc, mergedSpecifier);
				} else {
					refinedLoc2Sol.put(keyLoc, mergedSpecifier);
				}
			} else if(mergedConv != null) {
				// only conversion solution
				refinedLoc2Sol.put(keyLoc, mergedConv);
			} else if(mergedSpecifier != null) {
				// only specifier solution
				refinedLoc2Sol.put(keyLoc, mergedSpecifier);
			}
			if(mergedSanitychk != null) {
				refinedLoc2Sol.put(keyLoc, mergedSanitychk);
			}
		}
		
		// STEP 4: fix the translation unit
		String newTuName = tuName.substring(0, tuName.length() - 2) + ".fixed.i";
		// solveResult is necessary for fixing pointer error
		try {
			runFix(tuName, newTuName, refinedLoc2Sol, solveResult);
			return refinedLoc2Sol.size();
		} catch(Exception ex) {
			System.err.println("Failed to perform fix on translation unit!");
			ex.printStackTrace();
			return 0;
		}
	}
	
	private FixSolution mergeSolutions(List<FixSolution> solutions) {
		if(solutions.size() == 0) {
			return null;
		}
		FixSolution finalSol = solutions.get(0);
		for(int i = 1; i < solutions.size(); i++) {
			finalSol = finalSol.merge(solutions.get(i));
		}
		return finalSol;
	}
	
	private void runFix(String tuName, String newTuName, Multimap<FileLocation, FixSolution> loc2Sol, Map<String, IntType> varType) 
			throws Exception {
		// STEP 1: read the translation unit by lines
		File tuFile = new File(tuName);
		if(!tuFile.exists()) {
			throw new AssertionError(tuName + " is not a valid translation unit file.");
		}
		
		// STEP 2: load the translation unit file and construct MutableASTNode for rewriting
		FileContent content = FileContent.createForExternalFileLocation(tuName);
		IScannerInfo scanner = new ScannerInfo();
		IParserLogService log = new DefaultLogService();
		IncludeFileContentProvider provider = IncludeFileContentProvider.getEmptyFilesProvider();
		IASTTranslationUnit unit = GCCLanguage.getDefault().getASTTranslationUnit(content, scanner, provider, null, 8, log);
		MutableASTNode mast = MutableASTNode.createMutableASTFromIASTNode(unit);
		
		// STEP 3: mapping the FileLocation to corresponding MutableASTNode
		List<FileLocation> keylist = new ArrayList<>(loc2Sol.keySet());
		Collections.sort(keylist);
		List<FileLocation> shkeylist = new ArrayList<>(keylist);
		Map<FileLocation, MutableASTNode> loc2Mast = new HashMap<>();
		createFileLocationToMutableASTNodeMapping(mast, loc2Mast, shkeylist);
		// update type information at compile-time
		for(Entry<FileLocation, MutableASTNode> entry : loc2Mast.entrySet()) {
			FileLocation loc = entry.getKey();
			MutableASTNode node = entry.getValue();
			Collection<FixSolution> sols = loc2Sol.get(loc);
			List<FixSolution> solList = new ArrayList<>(sols);
			// multiple solutions?
			if(solList.size() == 1) {
				node.assertType(solList.get(0));
			} else if(solList.size() > 1) {
				if(solList.get(0).getFixMode() == FixSolution.SPECIFIER) {
					node.assertType(solList.get(0));
				} else {
					if(solList.get(1) == null) {
						System.out.println("Suprising!");
					}
					node.assertType(solList.get(1));
				}
			}
		}
		mast.updateNodeType(varType, "");
		
		// STEP 4: perform fix by traversing loc2Sol structure
		// now the shallow copy of keylist should be empty and keylist itself keeps untouched
		for(int idx = 0; idx < keylist.size(); idx++) {
			FileLocation loc = keylist.get(idx);		
			MutableASTNode ast = loc2Mast.get(loc);
			Collection<FixSolution> sols = loc2Sol.get(loc);
			for(FixSolution sol : sols) {
				int fixmode = sol.getFixMode();
				switch(fixmode) {
				case FixSolution.SPECIFIER: {
					// three cases: (1) declaration; (2) cast expression; (3) parameter in function definition
					String astString = ast.synthesize();
					if(astString.charAt(astString.length() - 1) == ';') {
						// this is a declaration statement
						if(ast.getChildrenSize() < 1) {
							throw new AssertionError("Invalid declaration AST node!");
						}
						MutableASTNode specifierNode = ast.getChild(0);
						while(specifierNode.getChildrenSize() > 0) {
							specifierNode = specifierNode.getChild(0);
						}
						// now specifierNode should be a leaf node
						String newTypeStr = replaceTypeInText(specifierNode.getTemplateString(0), sol.getBaseTypeCanonicalSpecifier());
						specifierNode.setTemplateString(0, newTypeStr);
						
						// CIL splits multiple declarators into multiple declarations
						if(ast.getParent().getParent() == null) {
							// this is a global declaration, it is necessary to scan other global declarations since one global variable
							// may declare for multiple times
							String declName = ast.getChild(1).getChild(0).synthesize();
							MutableASTNode rootNode = ast.getParent();
							int subNodeSize = rootNode.getChildrenSize();
							for(int i = 0; i < subNodeSize; i++) {
								MutableASTNode subNode = rootNode.getChild(i);
								if(subNode.getChildrenSize() < 2) {
									// for example: structure declaration
									continue;
								}
								IASTNode wrappedNode = subNode.getWrappedNode();
								if(wrappedNode instanceof IASTSimpleDeclaration) {
									String currentName = subNode.getChild(1).getChild(0).synthesize();
									if(declName.equals(currentName)) {
										// this declaration should be changed for consistency
										MutableASTNode thisSpecifierNode = subNode.getChild(0);
										while(thisSpecifierNode.getChildrenSize() > 0) {
											thisSpecifierNode = thisSpecifierNode.getChild(0);
										}
										String thisNewTypeStr = replaceTypeInText(thisSpecifierNode.getTemplateString(0), sol.getBaseTypeCanonicalSpecifier());
										thisSpecifierNode.setTemplateString(0, thisNewTypeStr);
									}
								}
							}
						}
						
					} else {
						// without the ending semicolon
						IASTNode wrappedNode = ast.getWrappedNode();
						if(wrappedNode instanceof IASTCastExpression) {
							// cast expression
							if(ast.getChildrenSize() < 2) {
								throw new AssertionError("Invalid cast expression node!");
							}
							MutableASTNode specifierNode = ast.getChild(0);
							while(specifierNode.getChildrenSize() > 0) {
								specifierNode = specifierNode.getChild(0);
							}
							// now specifierNode should be a leaf node
							String newTypeStr = replaceTypeInText(specifierNode.getTemplateString(0), sol.getBaseTypeCanonicalSpecifier());
							specifierNode.setTemplateString(0, newTypeStr);
						} else if(wrappedNode instanceof IASTParameterDeclaration) {
							// function parameter declaration
							if(ast.getChildrenSize() < 2) {
								throw new AssertionError("Invalid function parameter declaration!");
							}
							// what we should do now:
							// (1) change parameter name; (2) declare a new variable with the original name and new type at the beginning of the function body
							MutableASTNode declNode = ast.getChild(1);
							while(declNode.getChildrenSize() > 0) {
								declNode = declNode.getChild(0);
							}
							String oldName = declNode.getTemplateString(0);
							String newName = renamePrefix + oldName;
							declNode.setTemplateString(0, newName);
							
							MutableASTNode funcDefNode = ast.getParent().getParent();
							if(funcDefNode == null) {
								throw new AssertionError("Invalid function definition!");
							}
							assert (funcDefNode.getWrappedNode() instanceof IASTFunctionDefinition);
							int childrenNum = funcDefNode.getChildrenSize();
							MutableASTNode funcBodyNode = funcDefNode.getChild(childrenNum - 1);
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
								String newDeclStr = spacePrefix.concat(sol.getBaseTypeCanonicalSpecifier()).concat(" ").concat(oldName).concat(" = ").concat(newName).concat(";");
								marginalText = insertTextInSpecifiedPosition(marginalText, newDeclStr, 1);
								// write the updated code text back to MutableASTNode
								funcBodyNode.setTemplateString(0, marginalText);
							}
						} else {
							// unexpected case
							//System.err.println("unexpected case!");
						}
					}
					break;
				}
				case FixSolution.SANITYCHK: {
					// TODO: if the target expression is the left operand of an assignment, we should enclose the whole assignment
					//       instead of operand for normal compilation of program
					ast = elevateASTforAssignment(ast);
					int refLevel = sol.getRefLevel();
					if(refLevel == RangeTransferRelation.shiftLeftLevel) {
						// fix for << operation
						if(ast.getChildrenSize() < 2) {
							ast = removeBrackets(ast);
							if(ast.getChildrenSize() < 2) {
								throw new AssertionError("Invalid bit-shift expression!");
							}
						}
						String tempCodeSeg1 = String.format(convPattern, sol.getBaseTypeCanonicalSpecifier()).concat(lshiftFuncName).concat("(");
						ast.setTemplateString(0, tempCodeSeg1);
						ast.setTemplateString(1, ", ");
						ast.setTemplateString(2, ")");
					} else if(refLevel == RangeTransferRelation.shiftRightLevel) {
						// fix for >> operation
						if(ast.getChildrenSize() < 2) {
							ast = removeBrackets(ast);
							if(ast.getChildrenSize() < 2) {
								// still illegal after removing primary brackets
								throw new AssertionError("Invalid bit-shift expression!");
							}
						}
						String tempCodeSeg1 = String.format(convPattern, sol.getBaseTypeCanonicalSpecifier()).concat(rshiftFuncName).concat("(");
						ast.setTemplateString(0, tempCodeSeg1);
						ast.setTemplateString(1, ", ");
						ast.setTemplateString(2, ")");
					} else {
						// other sanity check fix
						String chkName = checkPrefix + sol.getBaseTypeOBJString();
						// the input expression can be signed or unsigned
						if(ast == null) {
							System.out.println("NoNONo!");
						}
						boolean signedness = ast.getNodeType().getSign();
						if(!signedness) {
							chkName = chkName.concat("_U");
						} else {
							chkName = chkName.concat("_S");
						}
						// insert sanity check enclosing this AST node
						int templateSize = ast.getTemplateSize();
						if(templateSize == 1) {
							String origCode = ast.getTemplateString(0);
							String newCode = chkName.concat("(").concat(origCode).concat(")");
							ast.setTemplateString(0, newCode);
						} else if(templateSize > 1) {
							String firstCode = ast.getTemplateString(0);
							String finalCode = ast.getTemplateString(ast.getTemplateSize() - 1);
							firstCode = insertTextInSpecifiedPosition(firstCode, chkName.concat("("), 0);
							finalCode = insertTextInSpecifiedPosition(finalCode, ")", finalCode.length());
							ast.setTemplateString(0, firstCode);
							ast.setTemplateString(ast.getTemplateSize() - 1, finalCode);
						}
					}
					break;
				}
				default: {
					// FixSolution.CONVERSION
					ast = elevateASTforAssignment(ast);
					if(sol.isInt()) {
						String convStr = String.format(convPattern, sol.getBaseTypeCanonicalSpecifier());
						int templateSize = ast.getTemplateSize();
						if(templateSize == 1) {
							String origCode = ast.getTemplateString(0);
							String newCode = convStr.concat("(").concat(origCode).concat(")");
							ast.setTemplateString(0, newCode);
						} else if(templateSize > 1) {
							String firstCode = ast.getTemplateString(0);
							String finalCode = ast.getTemplateString(ast.getTemplateSize() - 1);
							// FIXME: check the region to be enclosed by explicit cast
							String astString = ast.synthesize();
							if(astString.length() == loc.getNodeLength()) {
								// we should enclose the whole AST node
								firstCode = insertTextInSpecifiedPosition(firstCode, convStr.concat("("), 0);
								finalCode = insertTextInSpecifiedPosition(finalCode, ")", finalCode.length());
							} else {
								firstCode = insertTextInSpecifiedPosition(firstCode, convStr.concat("("), firstCode.length());
								finalCode = insertTextInSpecifiedPosition(finalCode, ")", 0);
							}
							ast.setTemplateString(0, firstCode);
							ast.setTemplateString(ast.getTemplateSize() - 1, finalCode);
						}
					} else if(sol.isPointer()) {
						String target = sol.getTarget();
						if(target == null) {
							throw new AssertionError("Invalid pointer target!");
						}
						IntType type = varType.get(target);
						if(type == null) {
							throw new AssertionError("Invalid pointer target!");
						}
						String convStr = type.toString().concat(" ");
						int refLevel = sol.getRefLevel();
						for(int i = 0; i < refLevel; i++) {
							convStr = convStr.concat("*");
						}
						// this should not happen, but we should prepare for this case
						if(convStr.isEmpty()) {
							// we should not insert an explicit conversion here
							continue;
						}
						
						convStr = String.format(convPattern, convStr);
						int templateSize = ast.getTemplateSize();
						if(templateSize == 1) {
							String origCode = ast.getTemplateString(0);
							String newCode = convStr.concat("(").concat(origCode).concat(")");
							ast.setTemplateString(0, newCode);
						} else if(templateSize > 1) {
							String firstCode = ast.getTemplateString(0);
							String finalCode = ast.getTemplateString(ast.getTemplateSize() - 1);
							firstCode = insertTextInSpecifiedPosition(firstCode, convStr.concat("("), firstCode.length());
							finalCode = insertTextInSpecifiedPosition(finalCode, ")", 0);
							ast.setTemplateString(0, firstCode);
							ast.setTemplateString(ast.getTemplateSize() - 1, finalCode);
						}
					}
					break;
				}
				} // switch statement
			} // multiple solutions
			// debug purpose only: output the fixed FileLocation here
			System.out.println("Fixed: " + loc.toString());
		}
		
		// write the fixed translation unit back to the file
		// NOTE: in order to support sanity check functions, we have to embed necessary declarations
		//       in target .i file
		BufferedWriter bw = new BufferedWriter(new FileWriter(newTuName));
		String completeContent = mast.synthesize();
		bw.write(declarations);
		bw.write(completeContent);
		bw.flush();
		bw.close();
		
		System.out.println("Fix complete! Result: " + newTuName);
	}
	
	private String insertTextInSpecifiedPosition(String origText, String segment, int index) {
		if(index < 0 || index > origText.length()) {
			throw new AssertionError("Failed to insert text in the specified position!");
		}
		return origText.substring(0, index).concat(segment).concat(origText.substring(index));
	}
	
	private MutableASTNode removeBrackets(MutableASTNode node) {
		if(node.getChildrenSize() != 1) {
			return node;
		}
		IASTNode wnode = node.getWrappedNode();
		if(wnode instanceof IASTUnaryExpression) {
			int optr = ((IASTUnaryExpression) wnode).getOperator();
			if(optr == IASTUnaryExpression.op_bracketedPrimary) {
				return removeBrackets(node.getChild(0));
			}
		}
		// otherwise, we just return this node
		return node;
	}
	
	private MutableASTNode elevateASTforAssignment(MutableASTNode node) {
		MutableASTNode parentNode = node.getParent();
		if(parentNode == null) {
			return node;
		}
		// parent node exists
		IASTNode wnode = parentNode.getWrappedNode();
		if(wnode instanceof IASTUnaryExpression) {
			int optr = ((IASTUnaryExpression) wnode).getOperator();
			if(optr == IASTUnaryExpression.op_bracketedPrimary) {
				return elevateASTforAssignment(parentNode);
			} else {
				return node;
			}
		} else if(wnode instanceof IASTBinaryExpression) {
			int optr = ((IASTBinaryExpression) wnode).getOperator();
			if(optr == IASTBinaryExpression.op_assign) {
				// when it is the left operand of assignment
				// check whether node is the left operand of wnode
				int locIdx = parentNode.locateChild(node);
				if(locIdx == 0) {
					// it is the left operand of assignment
					return parentNode;
				} else {
					return node;
				}
			}
			return node;
		} else {
			return node;
		}
	}
	
	private boolean isEmptyCharacter(char ch) {
		if(ch == '\f' || ch == '\r' || ch == '\t' || ch == '\n' || ch == ' ') {
			return true;
		}
		return false;
	}
	
	private String replaceTypeInText(String origTypeText, String newTypeText) {
		String procSpecStr = origTypeText;
		// prevent quantifiers
		procSpecStr = trimString(procSpecStr, "auto");
		procSpecStr = trimString(procSpecStr, "extern");
		procSpecStr = trimString(procSpecStr, "register");
		procSpecStr = trimString(procSpecStr, "static");
		procSpecStr = trimString(procSpecStr, "typedef");
		procSpecStr = trimString(procSpecStr, "const");
		procSpecStr = trimString(procSpecStr, "volatile");
		procSpecStr = trimString(procSpecStr, "restrict");
		procSpecStr = trimString(procSpecStr, "inline");
		procSpecStr = procSpecStr.trim();
		int specOffset = origTypeText.lastIndexOf(procSpecStr);
		if(specOffset == -1) {
			throw new AssertionError("Inconsistent type string!");
		}
		return origTypeText.substring(0, specOffset).concat(newTypeText);
	}
	
	private String trimString(String target, String trimmed) {
		int index = target.indexOf(trimmed);
		if(index == -1) {
			return target;
		}
		return (target.substring(0, index).concat(target.substring(index + trimmed.length())));
	}
	
	private void createFileLocationToMutableASTNodeMapping(MutableASTNode node, Map<FileLocation, MutableASTNode> loc2Mast, List<FileLocation> keylist) {
		// if keylist is empty, then no items are to be processed any more
		if(keylist.isEmpty()) {
			return;
		}
		IASTNode currentNode = node.getWrappedNode();
		IASTFileLocation currentLoc = currentNode.getFileLocation();
		FileLocation procedLoc = convertFileLocation(currentLoc);
		if(keylist.get(0).equals(procedLoc)) {
			keylist.remove(0);
			loc2Mast.put(procedLoc, node);
		}
		
		int childrenNum = node.getChildrenSize();
		for(int i = 0; i < childrenNum; i++) {
			createFileLocationToMutableASTNodeMapping(node.getChild(i), loc2Mast, keylist);
		}
		
		return;
	}
	
	private FileLocation convertFileLocation(IASTFileLocation loc) {
		int startLine = loc.getStartingLineNumber();
		int endLine = loc.getEndingLineNumber();
		int offset = loc.getNodeOffset();
		int length = loc.getNodeLength();
		String fileName = loc.getFileName();
		return new FileLocation(endLine, fileName, length, offset, startLine);
	}
	
	private Map<String, IntType> typeConstraintSolving(String filePath) throws IOException {
		Map<String, IntType> solve = new HashMap<>();
		File smt2File = new File(filePath);
		if(!smt2File.exists()) {
			throw new AssertionError(filePath + " is not a valid file.");
		}
		
		List<String> outputStr = new ArrayList<>();
		
		// solve Max-SMT problem using Z3opt
		System.out.println("Z3 starts working...");
		
		ProcessBuilder pb = new ProcessBuilder(z3, filePath);
		pb.redirectErrorStream(true);
		Process proc = pb.start();
		InputStreamReader isr = new InputStreamReader(proc.getInputStream());
		BufferedReader br = new BufferedReader(isr);
		String readLine = "";
		while((readLine = br.readLine()) != null) {
			outputStr.add(readLine);
		}
		
		try {
			if(br != null) {
				br.close();
			}
			if(isr != null) {
				isr.close();
			}
			if(proc != null) {
				proc.waitFor();
				proc.destroy();
			}
		} catch(InterruptedException ex) {
			System.err.println("Z3 is interrupted unexpectedly");
			ex.printStackTrace();
		}
		
		System.out.println("Z3 finished working!");
		
		String output = String.join("", outputStr);
		if(!output.startsWith("sat")) {
			throw new AssertionError("Exception on constraint solving.");
		}
		output = output.substring(3);
		
		// FIRST, extract the first Lisp S-expression
		String objStr = "";
		int brackCount = 0;
		for(int i = 0; i < output.length(); i++) {
			char c = output.charAt(i);
			if(c == '(') {
				brackCount++;
			} else if(c == ')') {
				brackCount--;
				if(brackCount == 0) {
					objStr = output.substring(0, i + 1);
					output = output.substring(i + 1);
					break;
				}
			}
		}
		
		// pattern matching and get the objective value
		Pattern objPat = Pattern.compile("\\(objectives(\\s+)\\((\\s*)(\\d+)(\\s*)\\)\\)");
		Matcher objMat = objPat.matcher(objStr);
		if(objMat.find()) {
			int value = Integer.valueOf(objMat.group(3));
			System.out.println("=====================================");
			System.out.println("The punishment value: " + value);
			System.out.println("(The lower this value is, the better)");
			System.out.println("=====================================");
		} else {
			throw new AssertionError("Failed to find the value of objective function!");
		}
		
		// SECOND, extract model interpretations from S-expression
		if(!output.startsWith("(model")) {
			throw new AssertionError("Failed to parse model for constraint");
		}
		
		output = output.substring(7, output.length() - 1).trim();
		List<String> exprList = new ArrayList<>();
		int brackLevel = 0;
		int startIdx = 0, endIdx = 0;
		for(int i = 0; i < output.length(); i++) {
			char c = output.charAt(i);
			if(c == '(') {
				if(brackLevel == 0) {
					startIdx = i;
				}
				brackLevel++;
			} else if(c == ')') {
				brackLevel--;
				if(brackLevel == 0) {
					endIdx = i;
					exprList.add(output.substring(startIdx, endIdx + 1));
				}
			}
		}
		
		// THIRD, interpret each S-expression by pattern matching
		Pattern funPat = Pattern.compile("\\(define-fun(\\s+)(\\S+)(\\s+)\\(\\)(\\s+)I(\\s+)(\\S+)\\)");
		for(String expr : exprList) {
			Matcher funMat = funPat.matcher(expr);
			if(funMat.find()) {
				String varName = funMat.group(2);
				// we should restore qualified names with "::" splitter
				varName = varName.replace("!!", "::");
				String typeStr = funMat.group(6);
				IntType type = IntType.fromOBJString(typeStr);
				solve.put(varName, type);
			} else {
				throw new AssertionError("Failed to find a valid model for fixing.");
			}
		}
		
		return solve;
	}
	
	private Multimap<FileLocation, FixSolution> loadLoc2GuideMapping(String filePath) {
		Multimap<FileLocation, FixSolution> loc2Sol = ArrayListMultimap.create();
		File loc2GuideFile = new File(filePath);
		if(!loc2GuideFile.exists()) {
			throw new AssertionError(filePath + " is not a valid file.");
		}
		
		List<String> contents = new ArrayList<>();
		try {
			BufferedReader br = new BufferedReader(new FileReader(filePath));
			String readLine = "";
			while((readLine = br.readLine()) != null) {
				contents.add(readLine);
			}
			br.close();
		} catch(IOException ex) {
			System.err.println("Error in loading JSON metadata.");
			ex.printStackTrace();
		}
		
		String JSONstr = String.join("\n", contents);
		JSONArray jloc2Guide = new JSONArray(JSONstr);
		
		for(int idx = 0; idx < jloc2Guide.length(); idx++) {
			JSONObject obj = jloc2Guide.getJSONObject(idx);
			// load field values
			int startLine = obj.getInt(sl);
			int endLine = obj.getInt(el);
			String fileName = obj.getString(fn);
			int nodeOffset = obj.getInt(os);
			int nodeLength = obj.getInt(len);
			FileLocation loc = new FileLocation(endLine, fileName, nodeLength, nodeOffset, startLine);
			// -------------------------------
			String datatype = obj.getString(kind);
			String methodOfGuide = obj.getString(method);
			String btOfGuide = obj.getString(bt);
			int refLevel = obj.getInt(rl);
			String tgOfGuide = obj.getString(tg);
			boolean intFlag = false, ptrFlag = false;
			if(datatype.equals("int")) {
				intFlag = true;
			} else if(datatype.equals("ptr")) {
				ptrFlag = true;
			}
			// these two flags should not have the same status
			assert (intFlag ^ ptrFlag);
			int methodLevel = methodOfGuide.equals("check") ? FixSolution.SANITYCHK : FixSolution.CONVERSION;
			FixSolution sol = new FixSolution(intFlag, ptrFlag, methodLevel, btOfGuide, tgOfGuide, refLevel);
			loc2Sol.put(loc, sol);
		}
		
		return loc2Sol;
	}
	
	private Map<String, FileLocation> loadName2LocMapping(String filePath) {
		Map<String, FileLocation> name2Loc = new HashMap<>();
		File name2LocFile = new File(filePath);
		if(!name2LocFile.exists()) {
			throw new AssertionError(filePath + " is not a valid file.");
		}
		
		List<String> contents = new ArrayList<>();
		try {
			BufferedReader br = new BufferedReader(new FileReader(filePath));
			String readLine = "";
			while((readLine = br.readLine()) != null) {
				contents.add(readLine);
			}
			br.close();
		} catch(IOException ex) {
			System.err.println("Error in loading JSON metadata.");
			ex.printStackTrace();
		}
		
		String JSONstr = String.join("\n", contents);
		JSONArray jname2Loc = new JSONArray(JSONstr);
		
		for(int idx = 0; idx < jname2Loc.length(); idx++) {
			JSONObject obj = jname2Loc.getJSONObject(idx);
			// load field values
			String varName = obj.getString(vn);
			int startLine = obj.getInt(sl);
			int endLine = obj.getInt(el);
			String fileName = obj.getString(fn);
			int nodeOffset = obj.getInt(os);
			int nodeLength = obj.getInt(len);
			// reconstruct FileLocation object
			FileLocation loc = new FileLocation(endLine, fileName, nodeLength, nodeOffset, startLine);
			name2Loc.put(varName, loc);
		}
		
		return name2Loc;
	}
	
}
