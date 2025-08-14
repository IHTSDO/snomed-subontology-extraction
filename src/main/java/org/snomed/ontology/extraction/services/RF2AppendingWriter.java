package org.snomed.ontology.extraction.services;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.ihtsdo.otf.snomedboot.factory.ImpotentComponentFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static java.lang.Long.parseLong;

public class RF2AppendingWriter extends ImpotentComponentFactory implements AutoCloseable {

	public static final String TAB = "\t";

	private final Set<Long> conceptIds;
	private final Set<Long> descriptionIds;

	private final List<Writer> writers;
	private final BufferedWriter conceptWriter;
	private final BufferedWriter descriptionWriter;
	private final BufferedWriter textDefWriter;
	private final BufferedWriter languageReferenceSetWriter;
	private final BufferedWriter owlAxiomWriter;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public RF2AppendingWriter(Set<Long> conceptIds, File rf2Directory) throws IOException {
		this.conceptIds = new LongOpenHashSet(conceptIds);
		this.descriptionIds = Collections.synchronizedSet(new LongOpenHashSet());

		File snapshotDir = new File(rf2Directory, "Snapshot");
		File terminologyDir = new File(snapshotDir, "Terminology");
		File refsetDir = new File(snapshotDir, "Refset");
		File langRefsetDir = new File(refsetDir, "Language");

		writers = new ArrayList<>();

		// Find existing RF2 files to append to
		conceptWriter = findAndOpenRF2File(terminologyDir, "sct2_Concept_Snapshot_INT_");
		descriptionWriter = findAndOpenRF2File(terminologyDir, "sct2_Description_Snapshot-en_INT_");
		textDefWriter = findAndOpenRF2File(terminologyDir, "sct2_TextDefinition_Snapshot-en_INT_");
		languageReferenceSetWriter = findAndOpenRF2File(langRefsetDir, "der2_cRefset_LanguageSnapshot-en_INT_");
		owlAxiomWriter = findAndOpenRF2File(terminologyDir, "sct2_sRefset_OWLExpressionSnapshot_INT_");
	}

	private BufferedWriter findAndOpenRF2File(File directory, String filenamePrefix) throws IOException {
		File[] files = directory.listFiles((dir, name) -> name.startsWith(filenamePrefix) && name.endsWith(".txt"));
		if (files == null || files.length == 0) {
			logger.warn("No existing RF2 file found for prefix: " + filenamePrefix);
			return null;
		}
		
		// Use the first file found (should be the one created by active concept extraction)
		File file = files[0];
		logger.info("Appending to existing RF2 file: " + file.getName());
		
		// Open file in append mode
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
			new FileOutputStream(file, true), StandardCharsets.UTF_8));
		writers.add(writer);
		return writer;
	}

	@Override
	public void newConceptState(String conceptId, String effectiveTime, String active, String moduleId, String definitionStatusId) {
		if (conceptIds.contains(parseLong(conceptId)) && conceptWriter != null) {
			try {
				conceptWriter.write(String.join(TAB, conceptId, effectiveTime, active, moduleId, definitionStatusId));
				conceptWriter.write("\r");
				conceptWriter.newLine();
			} catch (IOException e) {
				throw new RuntimeException("Failed to write to concept file.", e);
			}
		}
	}

	@Override
	public void newDescriptionState(String id, String effectiveTime, String active, String moduleId, String conceptId, String languageCode, String typeId, String term, String caseSignificanceId) {
		if (conceptIds.contains(parseLong(conceptId))) {
			descriptionIds.add(parseLong(id));
			try {
				BufferedWriter writer = this.descriptionWriter;
				if (typeId.equals("900000000000550004")) {// 900000000000550004 | Definition (core metadata concept) |
					writer = textDefWriter;
				}
				if (writer != null) {
					writer.write(String.join(TAB, id, effectiveTime, active, moduleId, conceptId, languageCode, typeId, term, caseSignificanceId));
					writer.write("\r");
					writer.newLine();
				}
			} catch (IOException e) {
				throw new RuntimeException("Failed to write to description file.", e);
			}
		}
	}

	@Override
	public void newReferenceSetMemberState(String[] fieldNames, String id, String effectiveTime, String active, String moduleId, String refsetId, String referencedComponentId, String... otherValues) {
		if (fieldNames.length == 7 && fieldNames[6].equals("acceptabilityId")) {
			if (descriptionIds.contains(parseLong(referencedComponentId)) && languageReferenceSetWriter != null) {
				try {
					languageReferenceSetWriter.write(String.join(TAB, id, effectiveTime, active, moduleId, refsetId, referencedComponentId, otherValues[0]));
					languageReferenceSetWriter.write("\r");
					languageReferenceSetWriter.newLine();
				} catch (IOException e) {
					throw new RuntimeException("Failed to write to language refset file.", e);
				}
			}
		}
		if (fieldNames.length == 7 && fieldNames[6].equals("owlExpression")) {
			if (conceptIds.contains(parseLong(referencedComponentId)) && owlAxiomWriter != null) {
				try {
					owlAxiomWriter.write(String.join(TAB, id, effectiveTime, active, moduleId, refsetId, referencedComponentId, otherValues[0]));
					owlAxiomWriter.write("\r");
					owlAxiomWriter.newLine();
				} catch (IOException e) {
					throw new RuntimeException("Failed to write to OWL axiom refset file.", e);
				}
			}
		}
	}

	@Override
	public void close() throws IOException {
		List<IOException> exceptions = new ArrayList<>();
		for (Writer writer : writers) {
			try {
				writer.close();// Also flushes.
			} catch (IOException e) {
				logger.error("Failed to close append file writer. {}", e.getMessage());
				exceptions.add(e);
			}
		}
		if (!exceptions.isEmpty()) {
			throw exceptions.iterator().next();
		}
	}
}
