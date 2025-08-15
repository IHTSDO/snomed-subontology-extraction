package org.snomed.ontology.extraction.services;

import com.google.common.collect.Sets;
import org.apache.commons.io.FileUtils;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.ihtsdo.otf.snomedboot.ReleaseImporter;
import org.ihtsdo.otf.snomedboot.factory.ComponentFactory;
import org.ihtsdo.otf.snomedboot.factory.LoadingProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.Set;

public class RF2ExtractionService {

	private final Logger logger = LoggerFactory.getLogger(getClass());
	public final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");

	public void extractAllInfo(FileInputStream rf2SnapshotArchive, ComponentFactory componentFactory) throws ReleaseImportException {
		logger.info("Extracting hierarchy from RF2 to supplement subset input.");
		ReleaseImporter releaseImporter = new ReleaseImporter();
		LoadingProfile customLoadingProfile = LoadingProfile.light
				.withInactiveConcepts()
				.withoutDescriptions()
				.withoutTextDefinitions()
				.withAllRefsets();
		releaseImporter.loadEffectiveSnapshotReleaseFileStreams(Collections.singleton(rf2SnapshotArchive), customLoadingProfile, componentFactory, false);
		logger.info("Inferred hierarchy extraction complete.");
	}

	public void extractConcepts(InputStream rf2SnapshotArchive, Set<Long> conceptIds, Map<Long, String> refsetsToInclude, File outputDirectory)
			throws ReleaseImportException, IOException {
		logger.info("Extracting {} concepts from RF2.", conceptIds.size());
		ReleaseImporter releaseImporter = new ReleaseImporter();
		String dateString = dateFormat.format(new Date());
		try (RF2ExtractionWriter extractionWriter = new RF2ExtractionWriter(conceptIds, refsetsToInclude, dateString, outputDirectory)) {
			releaseImporter.loadEffectiveSnapshotReleaseFileStreams(Collections.singleton(rf2SnapshotArchive), LoadingProfile.complete, extractionWriter, false);
		}
		logger.info("Extraction complete.");
	}

	public static void main(String[] args) throws IOException, ReleaseImportException {
		File outputDirectory = new File("rf2-extract");
		FileUtils.deleteDirectory(outputDirectory);
		new RF2ExtractionService().extractConcepts(
				new FileInputStream("../../release/SnomedCT_InternationalRF2_PRODUCTION_20200731T120000Z.zip"),
				Sets.newHashSet(404684003L, 195967001L), Collections.emptyMap(), outputDirectory);
	}
}
