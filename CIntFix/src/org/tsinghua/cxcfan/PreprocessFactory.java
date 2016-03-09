package org.tsinghua.cxcfan;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.cdt.core.dom.ast.IASTNode;
import org.eclipse.cdt.core.dom.ast.IASTTranslationUnit;
import org.eclipse.cdt.core.dom.ast.gnu.c.GCCLanguage;
import org.eclipse.cdt.core.parser.DefaultLogService;
import org.eclipse.cdt.core.parser.FileContent;
import org.eclipse.cdt.core.parser.IParserLogService;
import org.eclipse.cdt.core.parser.IScannerInfo;
import org.eclipse.cdt.core.parser.IncludeFileContentProvider;
import org.eclipse.cdt.core.parser.ScannerInfo;
import org.json.JSONArray;
import org.json.JSONObject;

public class PreprocessFactory {
	
	private String inputJSON;
	private static String prefix = "__INTFIX_";
	
	public PreprocessFactory(String inputFile) {
		inputJSON = inputFile;
	}
	
	public void perform() {
		
		// FIRST, read JSON file
		File jsonFile = new File(inputJSON);
		if(!jsonFile.exists()) {
			System.err.println("File " + inputJSON + " is not a valid file!");
			System.exit(1);
		}
		
		ArrayList<String> fileContent = new ArrayList<String>();
		try {
			BufferedReader breader = new BufferedReader(new FileReader(inputJSON));
			String readLine = "";
			while(readLine != null) {
				readLine = breader.readLine();
				fileContent.add(readLine);
			}
			breader.close();
		} catch(IOException ex) {
			ex.printStackTrace();
		}
		
		// SECOND, parse JSON file
		String jsonString = String.join("\n", fileContent);
		// build JSON array directly from this file
		JSONArray jarr = new JSONArray(jsonString);
		// group arrays by their file names
		Map<String, JSONArray> jarrByFileName = groupByFileName(jarr);
		
		// THIRD, modify each C file
		for (String currentFile : jarrByFileName.keySet()) {
			// read the code by line
			ArrayList<String> code = new ArrayList<String>();
			try {
				BufferedReader codeReader = new BufferedReader(new FileReader(currentFile));
				String readLine = "";
				while(readLine != null) {
					readLine = codeReader.readLine();
					code.add(readLine);
				}
				int lastInd = code.size() - 1;
				code.remove(lastInd); // eliminate the last NULL
				codeReader.close();
			} catch(IOException ex) {
				ex.printStackTrace();
			}
			
			// change the code
			JSONArray currentArray = jarrByFileName.get(currentFile);
			for(int index = 0; index < currentArray.length(); index++) {
				JSONObject currentObj = currentArray.getJSONObject(index);
				int beginLine = currentObj.getInt("beginln");
				int endLine = currentObj.getInt("endln");
				String varName = currentObj.getString("varname");
				String newToken = currentObj.getString("newtoken");
				
				// Read corresponding line and change it
				changeCode(code, beginLine, endLine, varName, newToken);
			}
			
			// output into a new file
			File file = new File(currentFile);
			String newFileName = backUpFile(currentFile);
			File nFile = new File(newFileName);
			if(file.exists() && !nFile.exists()) {
				file.renameTo(nFile);
				try {
					BufferedWriter bout = new BufferedWriter(new FileWriter(currentFile));
					for(int i = 0; i < code.size(); i++) {
						String line = code.get(i);
						bout.write(line);
						bout.newLine();
					}
					bout.flush();
					bout.close();
				} catch(IOException ex) {
					ex.printStackTrace();
				}
			} else {
				System.err.println("Invalid file names");
				System.exit(1);
			}
		}
	}
	
	private String backUpFile(String currentFileName) {
		int dotIndex = currentFileName.lastIndexOf('.');
		String extName = currentFileName.substring(dotIndex);
		String remain = currentFileName.substring(0, dotIndex);
		return remain + ".backup" + extName;
	}
	
	private void changeCode(ArrayList<String> codeSet, int startLine, int endLine, String varName, String newToken) {
		// Find the type of variable named varName and replace it with new type "newToken"
		if(newToken.equals("unknown")) {
			// stay unchanged
			return;
		}
		// Line number starts from 1
		// acquire complete code which possibly locates in multiple lines
		String code = "";
		ArrayList<Integer> beginTable = new ArrayList<Integer>();
		ArrayList<Integer> endTable = new ArrayList<Integer>();
		for(int index = startLine - 1; index < endLine; index++) {
			String lineCode = codeSet.get(index);
			String lineCodeTrimmed = lineCode.trim();
			// store correspondence info for modification
			int leftPos = lineCode.indexOf(lineCodeTrimmed);
			int rightPos = leftPos + lineCodeTrimmed.length() - 1;
			beginTable.add(leftPos);
			endTable.add(rightPos);
			// use trimmed code for parsing
			code = code.concat(lineCodeTrimmed);
		}
		
		boolean isFuncDef = false;
		// FIRST, check if we are trying to modify the signature of a function
		isFuncDef = isFunctionDefinition(code);
		if(isFuncDef) {
			// SECOND, rewrite code for CDT parser
			char lastChar = code.charAt(code.length() - 1);
			if(lastChar == ')') {
				// add a semicolon at tail
				code = code.concat(";");
			} else if(lastChar == '{') {
				code = code.substring(0, code.length() - 1);
				code = code.concat(";");
			} else {
				// unexpected case
				System.err.println("illegal C syntax!");
				System.exit(1);
			}
			
		} 
		// otherwise, code ends with semicolon, it is a simple declaration statement
		try {
			IASTTranslationUnit transUnit = parseSingleStatement(code);
			
			// THIRD, try to find the position of type specifier
			IASTNode varNode = findASTNode(transUnit, varName);
			int nodeOffset = varNode.getFileLocation().getNodeOffset();
			int nodeLength = varNode.getFileLocation().getNodeLength();
			
			// FOURTH, modify the name of formal parameter
			String newId = prefix + varName;
			// TODO: how to write changed code back to codeSet?
			int totalLength = 0;
			for(int index = startLine - 1; index < endLine; index++) {
				int indexInOrigin = index - startLine + 1;
				int thisBegin = beginTable.get(indexInOrigin);
				int thisEnd = endTable.get(indexInOrigin);
				totalLength += (thisEnd - thisBegin + 1);
				if(nodeOffset < totalLength) {
					// modified token is on this line
					int realBegin = thisEnd - (totalLength - 1 - nodeOffset);
					String thisCode = codeSet.get(index);
					String newCode = thisCode.substring(0, realBegin) + newId + thisCode.substring(realBegin + nodeLength);
					codeSet.set(index, newCode);
				}
			}
			
			// FIFTH, insert a declaration statement at the top of this function
			int insertIndex = endLine;
			while(insertIndex < codeSet.size()) {
				// find the first line of this function
				String codeLine = codeSet.get(insertIndex);
				if(isEntityLine(codeLine)) {
					// in order to keep indention, we record spaces before this line
					String trimmedCodeLine = codeLine.trim();
					String blankString = codeLine.substring(0, codeLine.indexOf(trimmedCodeLine));
					// OK, generate a casting statement
					String castStmt = newToken + " " + varName + " = (" + newToken + ")" + newId + ";\n";
					String newCodeLine = blankString + castStmt + codeLine;
					// store and quit
					codeSet.set(insertIndex, newCodeLine);
					break;
				}
				insertIndex++;
			}
			
		} catch(Exception ex) {
			ex.printStackTrace();
		}
		
	}
	
	private boolean isEntityLine(String line) {
		String cleanLine = line.trim();
		if(cleanLine.equals("{") || cleanLine.equals("")) {
			return false;
		} else {
			return true;
		}
	}
	
	private IASTNode findASTNode(IASTNode node, String varName) {
		// check if it is the node corresponding to varName
		String content = node.getRawSignature();
		if(!content.contains(varName)) {
			return null;
		}
		if(content.equals(varName)) {
			return node;
		}
		// otherwise, varName can corresponds to one of subnodes
		IASTNode[] children = node.getChildren();
		for(IASTNode child : children) {
			IASTNode result = findASTNode(child, varName);
			if(result != null) {
				return result;
			}
		}
		// loop ends but no node is found
		return null;
	}
	
	private IASTTranslationUnit parseSingleStatement(String statement) throws Exception {
		FileContent content = FileContent.create("", statement.toCharArray());
		IScannerInfo scanner = new ScannerInfo();
		IParserLogService log = new DefaultLogService();
		IncludeFileContentProvider provider = IncludeFileContentProvider.getEmptyFilesProvider();
		return GCCLanguage.getDefault().getASTTranslationUnit(content, scanner, provider, null, 8, log);
	}
	
	private boolean isFunctionDefinition(String fragment) {
		String temp = fragment.trim();
		char lastChar = temp.charAt(temp.length() - 1);
		if(lastChar == ';') {
			return false;
		} else {
			return true;
		}
	}
	
	private Map<String, JSONArray> groupByFileName(JSONArray jarr) {
		Map<String, JSONArray> jmap = new HashMap<String, JSONArray>();
		
		for(int index = 0; index < jarr.length(); index++) {
			JSONObject currentObj = jarr.getJSONObject(index);
			String fileName = currentObj.getString("filename");
			assert(fileName.length() > 0); // abnormal if file name is empty
			if(!jmap.containsKey(fileName)) {
				JSONArray subArr = new JSONArray();
				subArr.put(currentObj);
				jmap.put(fileName, subArr);
			} else {
				jmap.get(fileName).put(currentObj);
			}
		}
		
		// After organizing objects, return the array of JSONArray
		return jmap;
	}
	
}
