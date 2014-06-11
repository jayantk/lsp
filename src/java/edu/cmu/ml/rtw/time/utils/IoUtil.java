package edu.cmu.ml.rtw.time.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.zip.GZIPInputStream;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.io.filefilter.RegexFileFilter;

public class IoUtil {

	public static ArrayList<String> LoadFile(String fileName) {
		ArrayList<String> retList = new ArrayList<String>();

		try {
			BufferedReader bfr = getFileReader(fileName);			
			
			String line;
			while ((line = bfr.readLine()) != null) {
				// if (!retList.contains(line)) {
					retList.add(line);
				//}
			}
			bfr.close();
		} catch (IOException ioe) {
		    throw new RuntimeException(ioe);
		}

		// MessagePrinter.Print("Total " + retList.size()
		//		+ " entries loaded from " + fileName);
		return (retList);
	}
	
	public static String LoadFileInOneLine(String fileName) {
		StringBuffer sb = new StringBuffer();

		try {
			BufferedReader bfr = getFileReader(fileName);
			String line;
			while ((line = bfr.readLine()) != null) {
				// if (line.trim().length() == 0) { continue; }
				sb.append(line);
			}
			bfr.close();
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}

		return (sb.toString());
	}

	public static BufferedReader getFileReader(String fileName) throws IOException {
	  if (fileName.endsWith("gz")) {
	    return new BufferedReader(new InputStreamReader(
	        new GZIPInputStream(new FileInputStream(fileName))));
	  } else if (fileName.endsWith(".bz2")) {
	    return new BufferedReader(new InputStreamReader(
	        new BZip2CompressorInputStream(new FileInputStream(fileName))));

	  } else {
	    return new BufferedReader(new FileReader(fileName));
	  }
	}

	public static HashSet<String> LoadFieldFromFile(String fileName,
			String delim, int fieldIdx) {
		HashSet<String> retSet = new HashSet<String>();

		try {
			String[] files = fileName.split(",");
			
			for (String f : files) {
				BufferedReader bfr = new BufferedReader(new FileReader(f));
				String line;
				while ((line = bfr.readLine()) != null) {
				        // Skip blank lines.
 				        if (line.trim().length() == 0) {
					    continue;
					}
					String[] fields = line.split(delim);
					assert (fieldIdx < fields.length);
					if (!retSet.contains(fields[fieldIdx])) {
						retSet.add(fields[fieldIdx]);
					}
				}
				bfr.close();
			}
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}

		// MessagePrinter.Print("Total " + retSet.size() + " unique entries loaded from "
		//		+ fileName);
		return (retSet);
	}

	public static ArrayList<String> LoadFirstFieldFile(String fileName) {
		ArrayList<String> retList = new ArrayList<String>();

		try {
			BufferedReader bfr = new BufferedReader(new FileReader(fileName));
			String line;
			while ((line = bfr.readLine()) != null) {
				String[] fields = line.split("\t");
				if (!retList.contains(fields[0])) {
					retList.add(fields[0]);
				}
			}
			bfr.close();
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}

		// MessagePrinter.Print("Total " + retList.size()
		//		+ " entries loaded from " + fileName);
		return (retList);
	}
	
	public static ArrayList<String> GetFileList(String inputDir, String inputFileNamePat) {
		File dir = new File(inputDir);
		FileFilter fileFilter = new RegexFileFilter(inputFileNamePat);
		
		String[] dirList = dir.list();
		ArrayList<String> fileList = new ArrayList<String>();
		for (int di = 0; di < dirList.length; ++di) {
			if (dirList[di] == "." || dirList[di] == "..") { continue; }
			File d = new File(inputDir + "/" + dirList[di]);
			if (d == null) { continue; }
			if (d.getName().matches(inputFileNamePat)) {
				fileList.add(d.getAbsolutePath());
			}
			
			// File[] files = dir.listFiles(fileFilter);
			if (d.listFiles(fileFilter) == null) { continue; }
			for (File f : d.listFiles(fileFilter)) {
				fileList.add(f.getAbsolutePath());
			}
		}
		
		return (fileList);
	}

	/*
	 * public static RyanAlphabet LoadAlphabet(String fileName) { RyanAlphabet
	 * retAlpha = new RyanAlphabet();
	 * 
	 * try { BufferedReader bfr = new BufferedReader(new FileReader(fileName));
	 * String line; while ((line = bfr.readLine()) != null) { String[] fields =
	 * line.split("\t"); retAlpha.lookupIndex(fields[0], true); assert
	 * (retAlpha.lookupIndex(fields[0]) == Integer.parseInt(fields[1])); } }
	 * catch (IOException ioe) { ioe.printStackTrace(); }
	 * 
	 * MessagePrinter.Print("Total " + retAlpha.size() + " entries loaded from "
	 * + fileName); return (retAlpha); }
	 */
}
