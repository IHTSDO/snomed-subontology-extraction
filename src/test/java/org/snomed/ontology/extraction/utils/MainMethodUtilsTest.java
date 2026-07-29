package org.snomed.ontology.extraction.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MainMethodUtilsTest {

	@Test
	void resolveEffectiveTimeUsesExportDateWhenBlank() {
		assertEquals("20250901", MainMethodUtils.resolveEffectiveTime(null, "20250901"));
		assertEquals("20250901", MainMethodUtils.resolveEffectiveTime("", "20250901"));
	}

	@Test
	void resolveEffectiveTimePreservesExistingValue() {
		assertEquals("20170131", MainMethodUtils.resolveEffectiveTime("20170131", "20250901"));
	}

}
