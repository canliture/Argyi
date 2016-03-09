package org.tsinghua.cxcfan;

import java.io.File;

public class IntErrorFix {
	
	/**
	 * In the future version, this is a customizable parameter.
	 */
	private static String fileNameWithoutExt = "/home/cxcfan/Research/IntFix/metadata/";
	/**
	 * This is the location of CPAchecker.
	 * We can invoke CPAchecker by a shell script
	 */
	private static String cpacheckerLocation = "/home/cxcfan/Research/IntFix/cpachecker/scripts/cpa.sh";
	/**
	 * This is temporary. In the future version, the file name should not be fixed in program.
	 */
	private static String tuDir = false ? "/home/cxcfan/Research/IntFix/Benchmark/Juliet/testcases/CWE680_Integer_Overflow_to_Buffer_Overflow/" :
		"/home/cxcfan/Research/IntFix/Benchmark/bench/";
	
	private static String sh = "bash";
	private static int heapSize = 4000;
	
	public static void main(String[] args) {
		
		long preprocessTime = 0;
		long cpaTime = 0;
		long fixTime = 0;
		
		long startTime = 0, endTime = 0;
		
		long fixNum = 0;
		
		// TODO: in order to process multiple files in a batch, we can traverse all files ending with ".cil.i" and process them.
		File workDir = new File(tuDir);
		if(!workDir.exists()) {
			System.err.println("Failed to locate the target folder.");
			System.exit(1);
		}
		File[] files = workDir.listFiles((File file) -> {
			if(file == null) return false;
			if(file.isDirectory()) return false;
			return file.getName().endsWith(".cil.i");
		});
		for(File tu : files) {
			String tuName = tu.getAbsolutePath();
			System.out.println("Processing: " + tuName);
			
			// STEP 1: eliminate all compound assignment and self-increment/decrement operations
			startTime = System.currentTimeMillis();
			CPPFactory factory = new CPPFactory(tuName);
			factory.process();
			endTime = System.currentTimeMillis();
			preprocessTime += (endTime - startTime);
			System.out.println("Phase 1 (code preprocessing): completed!");
			
			// STEP 2: perform static analysis using CPAchecker
			startTime = System.currentTimeMillis();
			try {
				ProcessBuilder pb = new ProcessBuilder(sh, cpacheckerLocation, "-rangeAnalysis", tuName, "-heap", String.valueOf(heapSize).concat("M"));
				pb.redirectErrorStream(true);
				Process proc = pb.start();
				if(proc != null) {
					proc.waitFor();
					proc.destroy();
				}
			} catch(Exception ex) {
				System.err.println("CPAchecker is not executed normally!");
				ex.printStackTrace();
			}
			endTime = System.currentTimeMillis();
			cpaTime += (endTime - startTime);
			System.out.println("Phase 2 (static analysis): completed!");
			
			// STEP 3: fix the original program
			startTime = System.currentTimeMillis();
			FixModule fixmod = new FixModule(tuName, fileNameWithoutExt);
			// run this fix module here
			fixNum = fixNum + fixmod.run();
			endTime = System.currentTimeMillis();
			fixTime += (endTime - startTime);
			System.out.println("Phase 3 (code fixing): completed!");
		}
		System.out.println("Complete fixing " + files.length + " files!");
		System.out.println("Total elapsed time: " + String.valueOf(preprocessTime + cpaTime + fixTime) + "ms.");
		System.out.println("    Preprocess: " + String.valueOf(preprocessTime) + " ms;");
		System.out.println("    CPAchecker: " + String.valueOf(cpaTime) + " ms;");
		System.out.println("        Fixing: " + String.valueOf(fixTime) + " ms.");
		System.out.println("Total fix performed: " + fixNum);
	}
	
}
