package org.snomed.ontology.extraction.services;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.ihtsdo.otf.snomedboot.factory.ImpotentComponentFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static java.lang.Long.parseLong;

public class RF2ExtractionWriter extends ImpotentComponentFactory implements AutoCloseable {

	public static final String TAB = "\t";

	private final Set<Long> conceptIds;
	private final Set<Long> descriptionIds;
	private final Map<Long, String> refsetsToInclude;

	private final List<Writer> writers;
	private final BufferedWriter conceptWriter;
	private final BufferedWriter descriptionWriter;
	private final BufferedWriter textDefWriter;
	private final BufferedWriter languageReferenceSetWriter;
	private final BufferedWriter owlAxiomWriter;
	private final Map<Long, String> refsetName;
	private final Map<String, BufferedWriter> refsetWriters;
	private final File refsetDir;
	private final String dateString;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public RF2ExtractionWriter(Set<Long> conceptIds, Map<Long, String> refsetsToInclude, String dateString, File outputDirectory) throws IOException {
		this.conceptIds = new LongOpenHashSet(conceptIds);
		this.descriptionIds = Collections.synchronizedSet(new LongOpenHashSet());
		this.refsetsToInclude = refsetsToInclude;
		refsetName = new HashMap<>();
		refsetWriters = new HashMap<>();
		this.dateString = dateString;

		File snapshotDir = new File(outputDirectory, "Snapshot");
		createDirectoryOrThrow(snapshotDir);

		File terminologyDir = new File(snapshotDir, "Terminology");
		createDirectoryOrThrow(terminologyDir);

		refsetDir = new File(snapshotDir, "Refset");
		createDirectoryOrThrow(refsetDir);

		File langRefsetDir = new File(refsetDir, "Language");
		createDirectoryOrThrow(langRefsetDir);

		writers = new ArrayList<>();

		conceptWriter = newRF2Writer(
				terminologyDir,
				String.format("sct2_Concept_Snapshot_INT_%s.txt", dateString),
				String.join(TAB, "id", "effectiveTime", "active", "moduleId", "definitionStatusId"));

		descriptionWriter = newRF2Writer(
				terminologyDir,
				String.format("sct2_Description_Snapshot-en_INT_%s.txt", dateString),
				String.join(TAB, "id", "effectiveTime", "active", "moduleId", "conceptId", "languageCode", "typeId", "term", "caseSignificanceId"));

		textDefWriter = newRF2Writer(
				terminologyDir,
				String.format("sct2_TextDefinition_Snapshot-en_INT_%s.txt", dateString),
				String.join(TAB, "id", "effectiveTime", "active", "moduleId", "conceptId", "languageCode", "typeId", "term", "caseSignificanceId"));

		languageReferenceSetWriter = newRF2Writer(
				langRefsetDir,
				String.format("der2_cRefset_LanguageSnapshot-en_INT_%s.txt", dateString),
				String.join(TAB, "id", "effectiveTime", "active", "moduleId", "refsetId", "referencedComponentId", "acceptabilityId"));

		owlAxiomWriter = newRF2Writer(
				terminologyDir,
				String.format("sct2_sRefset_OWLExpressionSnapshot_INT_%s.txt", dateString),
				String.join(TAB, "id", "effectiveTime", "active", "moduleId", "refsetId", "referencedComponentId", "owlExpression"));
	}

	private void createDirectoryOrThrow(File snapshotDir) throws IOException {
		if (!snapshotDir.exists() && !snapshotDir.mkdirs()) {
			throw new IOException("Failed to create directory " + snapshotDir.getPath());
		}
	}

	private BufferedWriter getCreateRefsetWriter(long refsetIdL, String originalFilename, String[] fieldNames) {
		String filename = refsetName.computeIfAbsent(refsetIdL, i -> {
			String filenamePart = originalFilename.substring(0, originalFilename.lastIndexOf("_"));
			return "%s_%s.txt".formatted(filenamePart, dateString);
		});
		return refsetWriters.computeIfAbsent(filename, i -> {
			try {
				return newRF2Writer(refsetDir, filename, String.join(TAB, fieldNames));
			} catch (IOException e) {
				throw new RuntimeException("Failed to create refset writer.", e);
			}
		});
	}

	private BufferedWriter newRF2Writer(File terminologyDir, String filename, String header) throws IOException {
		final BufferedWriter fileWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(terminologyDir, filename)), StandardCharsets.UTF_8));
		writers.add(fileWriter);
		// Write header
		fileWriter.write(header);
		newline(fileWriter);
		return fileWriter;
	}

	@Override
	public void newConceptState(String conceptId, String effectiveTime, String active, String moduleId, String definitionStatusId) {
		if (conceptIds.contains(parseLong(conceptId))) {
			try {
				conceptWriter.write(String.join(TAB, conceptId, effectiveTime, active, moduleId, definitionStatusId));
				newline(conceptWriter);
			} catch (IOException e) {
				// Ugly but the interface does not allow throwing a checked exception
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
				writer.write(String.join(TAB, id, effectiveTime, active, moduleId, conceptId, languageCode, typeId, term, caseSignificanceId));
				newline(writer);
			} catch (IOException e) {
				throw new RuntimeException("Failed to write to description file.", e);
			}
		}
	}

	@Override
	public void newReferenceSetMemberState(String filename, String[] fieldNames, String id, String effectiveTime, String active, String moduleId, String refsetId, String referencedComponentId, String... otherValues) {
		if (fieldNames.length == 7 && fieldNames[6].equals("acceptabilityId")) {
			if (descriptionIds.contains(parseLong(referencedComponentId))) {
				try {
					languageReferenceSetWriter.write(String.join(TAB, id, effectiveTime, active, moduleId, refsetId, referencedComponentId, otherValues[0]));
					newline(languageReferenceSetWriter);
				} catch (IOException e) {
					throw new RuntimeException("Failed to write to language refset file.", e);
				}
			}
		} else if (fieldNames.length == 7 && fieldNames[6].equals("owlExpression")) {
			if (conceptIds.contains(parseLong(referencedComponentId))) {
				try {
					owlAxiomWriter.write(String.join(TAB, id, effectiveTime, active, moduleId, refsetId, referencedComponentId, otherValues[0]));
					newline(owlAxiomWriter);
				} catch (IOException e) {
					throw new RuntimeException("Failed to write to OWL axiom refset file.", e);
				}
			}
		} else {
			long refsetIdL = parseLong(refsetId);
			if (refsetsToInclude.containsKey(refsetIdL)) {
				try {
					BufferedWriter refsetWriter = getCreateRefsetWriter(refsetIdL, refsetsToInclude.get(refsetIdL), fieldNames);
					refsetWriter.write(String.join(TAB, id, effectiveTime, active, moduleId, refsetId, referencedComponentId, otherValues[0]));
					newline(refsetWriter);
				} catch (IOException e) {
					throw new RuntimeException("Failed to write refset file.", e);
				}
			}
		}
	}

	private void newline(BufferedWriter languageReferenceSetWriter) throws IOException {
		languageReferenceSetWriter.write("\r");
		languageReferenceSetWriter.newLine();
	}

	@Override
	public void close() throws IOException {
		List<IOException> exceptions = new ArrayList<>();
		for (Writer writer : writers) {
			try {
				writer.close();// Also flushes.
			} catch (IOException e) {
				logger.error("Failed to close extract file writer. {}", e.getMessage());
				exceptions.add(e);
			}
		}
		if (!exceptions.isEmpty()) {
			throw exceptions.iterator().next();
		}
	}
}
