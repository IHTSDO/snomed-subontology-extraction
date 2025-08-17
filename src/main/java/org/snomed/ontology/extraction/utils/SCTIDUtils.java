package org.snomed.ontology.extraction.utils;

import com.google.common.base.Strings;

import java.util.regex.Pattern;

public class SCTIDUtils {

	public static final Pattern SCTID_PATTERN = Pattern.compile("\\d{6,18}");
	private static final String PARTITION_PART2_CONCEPT = "0";

	public static boolean isConceptId(String sctid) {
		return sctid != null && SCTID_PATTERN.matcher(sctid).matches() && PARTITION_PART2_CONCEPT.equals(getPartitionIdPart(sctid));
	}

	private static String getPartitionIdPart(String sctid) {
		if (!Strings.isNullOrEmpty(sctid) && sctid.length() > 4) {
			return sctid.substring(sctid.length() - 2, sctid.length() - 1);
		}
		return null;
	}
}
