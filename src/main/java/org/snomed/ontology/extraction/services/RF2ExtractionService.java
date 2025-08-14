package org.snomed.ontology.extraction.services;

import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.Pair;
import org.apache.commons.io.FileUtils;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.ihtsdo.otf.snomedboot.ReleaseImporter;
import org.ihtsdo.otf.snomedboot.factory.LoadingProfile;
import org.snomed.ontology.extraction.tools.InputSignatureHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Set;
import java.util.function.Consumer;

public class RF2ExtractionService {

	private final Logger logger = LoggerFactory.getLogger(getClass());
	public final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");

	private static final LoadingProfile LOADING_PROFILE = LoadingProfile.complete
			.withoutInactiveConcepts()
			.withoutInactiveDescriptions()
			.withoutInactiveRelationships()
			.withoutInactiveRefsetMembers();

	private static final LoadingProfile CONCEPTS_ONLY_LOADING_PROFILE = LoadingProfile.light
			.withInactiveConcepts()
			.withoutDescriptions()
			.withoutTextDefinitions()
			.withoutRelationships()
			.withoutAnyRefsets();

	private static final LoadingProfile INACTIVE_CONCEPTS_LOADING_PROFILE = LoadingProfile.complete
			.withInactiveConcepts()
			.withoutInactiveDescriptions()
			.withInactiveRelationships()
			.withoutInactiveRefsetMembers();

	public void extractConcepts(InputStream rf2SnapshotArchive, Set<Long> conceptIds, File outputDirectory)
			throws ReleaseImportException, IOException {
		logger.info("Extracting {} concepts from RF2.", conceptIds.size());
		ReleaseImporter releaseImporter = new ReleaseImporter();
		String dateString = dateFormat.format(new Date());
		try (RF2ExtractionWriter extractionWriter = new RF2ExtractionWriter(conceptIds, dateString, outputDirectory)) {
			releaseImporter.loadEffectiveSnapshotReleaseFileStreams(Collections.singleton(rf2SnapshotArchive), LOADING_PROFILE, extractionWriter, false);
		}
		logger.info("Extraction complete.");
	}

	public void extractParentChildRelationships(InputStream rf2SnapshotArchive, Consumer<Pair<Long, Long>> parentChildPairConsumer) throws ReleaseImportException {
		logger.info("Extracting inferred hierarchy from RF2 to supplement subset input.");
		ReleaseImporter releaseImporter = new ReleaseImporter();
		LoadingProfile relationshipOnlyLoadingProfile = LoadingProfile.light
				.withoutConcepts()
				.withoutDescriptions()
				.withoutTextDefinitions()
				.withoutAnyRefsets();
		releaseImporter.loadEffectiveSnapshotReleaseFileStreams(Collections.singleton(rf2SnapshotArchive), relationshipOnlyLoadingProfile,
				new RelationshipComponentFactory(parentChildPairConsumer), false);
		logger.info("Inferred hierarchy extraction complete.");
	}

	public void extractConceptsOnly(InputStream rf2SnapshotArchive, Consumer<InputSignatureHandler.ConceptInfo> conceptInfoConsumer) throws ReleaseImportException {
		logger.info("Extracting all concepts (including inactive) from RF2 for validation.");
		ReleaseImporter releaseImporter = new ReleaseImporter();
		releaseImporter.loadEffectiveSnapshotReleaseFileStreams(Collections.singleton(rf2SnapshotArchive), CONCEPTS_ONLY_LOADING_PROFILE,
				new ConceptInfoComponentFactory(conceptInfoConsumer), false);
		logger.info("All concepts extraction complete.");
	}

	public void extractInactiveConcepts(InputStream rf2SnapshotArchive, Set<Long> inactiveConceptIds, File outputDirectory)
			throws ReleaseImportException, IOException {
		logger.info("Extracting {} inactive concepts from RF2.", inactiveConceptIds.size());
		ReleaseImporter releaseImporter = new ReleaseImporter();
		String dateString = dateFormat.format(new Date());
		try (RF2ExtractionWriter extractionWriter = new RF2ExtractionWriter(inactiveConceptIds, dateString, outputDirectory)) {
			releaseImporter.loadEffectiveSnapshotReleaseFileStreams(Collections.singleton(rf2SnapshotArchive), INACTIVE_CONCEPTS_LOADING_PROFILE, extractionWriter, false);
		}
		logger.info("Inactive concepts extraction complete.");
	}

	public void appendInactiveConcepts(InputStream rf2SnapshotArchive, Set<Long> inactiveConceptIds, File rf2Directory)
			throws ReleaseImportException, IOException {
		logger.info("Appending {} inactive concepts to existing RF2 files.", inactiveConceptIds.size());
		ReleaseImporter releaseImporter = new ReleaseImporter();
		try (RF2AppendingWriter appendingWriter = new RF2AppendingWriter(inactiveConceptIds, rf2Directory)) {
			releaseImporter.loadEffectiveSnapshotReleaseFileStreams(Collections.singleton(rf2SnapshotArchive), INACTIVE_CONCEPTS_LOADING_PROFILE, appendingWriter, false);
		}
		logger.info("Inactive concepts appending complete.");
	}

	public static void main(String[] args) throws IOException, ReleaseImportException {
		File outputDirectory = new File("rf2-extract");
		FileUtils.deleteDirectory(outputDirectory);
		new RF2ExtractionService().extractConcepts(
				new FileInputStream("../../release/SnomedCT_InternationalRF2_PRODUCTION_20200731T120000Z.zip"),
				Sets.newHashSet(404684003L, 195967001L), outputDirectory);
	}
}
