package org.snomed.ontology.extraction.utils;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class MainMethodUtils {

	private static final SimpleDateFormat EFFECTIVE_TIME_FORMAT = new SimpleDateFormat("yyyyMMdd");

	private static Runnable printHelp = () -> {};

	public static boolean isFlag(String paramName, List<String> args) {
		return args.contains(paramName);
	}

	public static String getParameterValue(String paramName, List<String> args, String defaultValue) {
		String value = getParameterValue(paramName, args);
		return value != null ? value : defaultValue;
	}

	public static String getParameterValue(String paramName, List<String> args) {
		if (args.contains(paramName)) {
			int valueIndex = args.indexOf(paramName) + 1;
			assertTrue("Expecting a value with parameter " + paramName, valueIndex < args.size());
			return args.get(valueIndex);
		}
		return null;
	}

	public static String getRequiredParameterValue(String paramName, List<String> args) {
		assertTrue("Expecting parameter " + paramName, args.contains(paramName));
		return getParameterValue(paramName, args);
	}

	public static String getEffectiveTimeParameter(String paramName, List<String> args) {
		String value = getParameterValue(paramName, args);
		if (value == null) {
			return EFFECTIVE_TIME_FORMAT.format(new Date());
		}
		assertTrue("Expecting " + paramName + " in yyyyMMdd format.", isValidEffectiveTime(value));
		return value;
	}

	private static boolean isValidEffectiveTime(String value) {
		if (value.length() != 8) {
			return false;
		}
		try {
			EFFECTIVE_TIME_FORMAT.setLenient(false);
			EFFECTIVE_TIME_FORMAT.parse(value);
			return true;
		} catch (ParseException e) {
			return false;
		}
	}

	public static File getFile(String filename) {
		if (filename == null) {
			return null;
		}
		final File file = new File(filename);
		assertTrue(file.getAbsolutePath() + " should be a file.", file.isFile());
		return file;
	}

	public static void assertTrue(String message, boolean bool) {
		if (!bool) {
			System.out.println();
			System.err.println(message);
			System.out.println();
			printHelp.run();
			throw new IllegalArgumentException();
		}
	}

	public static String pad(String argHelp) {
		StringBuilder argHelpBuilder = new StringBuilder(" " + argHelp);
		while (argHelpBuilder.length() < 40) {
			argHelpBuilder.append(" ");
		}
		return argHelpBuilder.toString();
	}

	public static void setPrintHelp(Runnable printHelp) {
		MainMethodUtils.printHelp = printHelp;
	}
}
