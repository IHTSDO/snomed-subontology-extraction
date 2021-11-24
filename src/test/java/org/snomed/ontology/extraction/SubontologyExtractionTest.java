package org.snomed.ontology.extraction;

import org.apache.commons.io.FileUtils;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.model.OWLException;
import org.snomed.ontology.extraction.exception.ReasonerException;
import org.snomed.otf.owltoolkit.conversion.ConversionException;
import org.snomed.otf.snomedboot.testutil.ZipUtil;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SubontologyExtractionTest {

	public static final String FINDING_SITE_ATTRIBUTE = "363698007";
	public static final String CLINICAL_FINDING = "404684003";
	public static final String CONCEPT_MODEL_OBJECT_ATTRIBUTE = "762705008";
	private SubontologyExtraction subontologyExtraction;

	@BeforeEach
	void setup() {
		subontologyExtraction = new SubontologyExtraction();
	}

	@Test
	void testOneSubClassAndOneSubProperty() throws ConversionException, OWLException, ReleaseImportException, IOException, ReasonerException {
		File tempOutputDirectory = Files.createTempDirectory(new Date().getTime() + "").toFile();
		tempOutputDirectory.deleteOnExit();
		File inputRF2Zip = ZipUtil.zipDirectoryRemovingCommentsAndBlankLines("src/test/resources/dummy-sct-snapshot");

		// Run extraction
		subontologyExtraction.run(("-source-ontology src/test/resources/dummy-sct-ontology.owl " +
				"-input-subset 816080008.txt " +
				"-output-rf2 " +
				"-output-path " + tempOutputDirectory.getAbsolutePath() + " " +
				"-rf2-snapshot-archive " + inputRF2Zip.getAbsolutePath()).split(" "));

		// OWL Ontology Assertions
		List<String> subontologyOwlLines = FileUtils.readLines(new File(tempOutputDirectory, "subOntology.owl"), StandardCharsets.UTF_8);
		assertTrue(subontologyOwlLines.contains("SubClassOf(<http://snomed.info/id/404684003> <http://snomed.info/id/138875005>)"),
				"Assert that Clinical finding is a subclass of the root concept.");
		assertTrue(subontologyOwlLines.contains("SubObjectPropertyOf(<http://snomed.info/id/363698007> <http://snomed.info/id/762705008>)"),
				"Assert that Finding site (attribute) is a subproperty of 762705008 |Concept model object attribute (attribute)|.");
		assertTrue(subontologyOwlLines.contains("Declaration(Class(<http://snomed.info/id/762705008>))"));
		assertTrue(subontologyOwlLines.contains("Declaration(ObjectProperty(<http://snomed.info/id/762705008>))"));

		// RF2 Relationship assertions
		File relationshipOutputFile = new File(tempOutputDirectory,
				String.format("sct2_Relationship_Snapshot_INT%s.txt", new SimpleDateFormat("yyyyMMdd").format(new Date())));
		assertTrue(relationshipOutputFile.isFile());
		Map<String, Set<String>> parents = extractParents(relationshipOutputFile);
		assertTrue(parents.containsKey(CLINICAL_FINDING));
		assertEquals("[138875005]", parents.get(CLINICAL_FINDING).toString());
		assertTrue(parents.containsKey(FINDING_SITE_ATTRIBUTE));
		assertEquals("[762705008]", parents.get(FINDING_SITE_ATTRIBUTE).toString());
		assertTrue(parents.containsKey(CONCEPT_MODEL_OBJECT_ATTRIBUTE));
		assertEquals("[410662002]", parents.get(CONCEPT_MODEL_OBJECT_ATTRIBUTE).toString());
		assertTrue(parents.containsKey("410662002"));
		assertEquals("[900000000000441003]", parents.get("410662002").toString());
		assertTrue(parents.containsKey("900000000000441003"));
		assertEquals("[138875005]", parents.get("900000000000441003").toString());
	}

	private Map<String, Set<String>> extractParents(File relationshipsFile) throws IOException {
		Map<String, Set<String>> parents = new HashMap<>();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(relationshipsFile)))) {
			String header = reader.readLine();
			String line;
			while ((line = reader.readLine()) != null) {
				String[] values = line.split("\\t");
				// id	effectiveTime	active	moduleId	sourceId	destinationId	relationshipGroup	typeId	characteristicTypeId	modifierId
				// 0	1				2		3			4			5				6					7		8						9
				if ("116680003".equals(values[7])) {
					String sourceId = values[4];
					String destinationId = values[5];
					parents.computeIfAbsent(sourceId, key -> new HashSet<>()).add(destinationId);
				}
			}
		}
		return parents;
	}

}
