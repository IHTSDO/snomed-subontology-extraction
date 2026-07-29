package org.snomed.ontology.extraction.services;

import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.snomed.ontology.extraction.writers.OWLtoRF2Service;
import org.snomed.ontology.extraction.writers.RF2Printer;
import org.snomed.otf.owltoolkit.conversion.ConversionException;

import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/*
Converted to RF2 as follows, making use of the SNOMED OWL Toolkit RF2 conversion methods
Procedure is somewhat different, since subontology extraction utilises information from both the source ontology and the extracted subontology
		: All RF2 files except Relationships file - extracted from OWLtoRF2 of subontology
		: Concept RF2 - from source ontology, together with the signature of the subontology
		: Description RF2 - " "
		: Relationships RF2 - extracted from NNF file
		: OWLRefset RF2 - from subontology
		: Text Definitions RF2 - " "
 */
public class SubOntologyRF2ConversionService {

	public static final String IRI_PREFIX = "http://snomed.info/id/";
	private static final String OWLRefsetRF2Filename = "debug_OWLRefset";
	public static final Integer SCTID_GENERATION_NAMESPACE = 1000003;
	public static final String TEST_SUBONTOLOGY_MODULE_CONCEPT = "31000003106";

	public static void convertSubOntologytoRF2(OWLOntology subOntology, OWLOntology nnfOntology, Set<Long> inactiveConcepts, File outputDirectory,
			File sourceFile, RF2InformationCache rf2Cache, String effectiveTime) throws ReleaseImportException, IOException, OWLException, ConversionException {

		//Extract the concept and description RF2 files, based on the source ontology (includes all entities in subontology)
		Set<OWLEntity> entitiesInSubontologyAndNNFs = new HashSet<>();
		entitiesInSubontologyAndNNFs.addAll(subOntology.getClassesInSignature());
		entitiesInSubontologyAndNNFs.addAll(subOntology.getObjectPropertiesInSignature());
		entitiesInSubontologyAndNNFs.addAll(subOntology.getDataPropertiesInSignature());
		entitiesInSubontologyAndNNFs.addAll(nnfOntology.getClassesInSignature());
		entitiesInSubontologyAndNNFs.addAll(nnfOntology.getObjectPropertiesInSignature());
		entitiesInSubontologyAndNNFs.addAll(nnfOntology.getDataPropertiesInSignature());

		/*
		Set<OWLClass> inSubOntNotNNF = subOntology.getClassesInSignature();
		inSubOntNotNNF.removeAll(nnfOntology.getClassesInSignature());
		Set<OWLClass> inNNFNotSubOnt = nnfOntology.getClassesInSignature();
		inNNFNotSubOnt.removeAll(subOntology.getClassesInSignature());
		 */

		OWLOntologyManager man = OWLManager.createOWLOntologyManager();
		OWLDataFactory df = man.getOWLDataFactory();

		entitiesInSubontologyAndNNFs.remove(df.getOWLThing());
		entitiesInSubontologyAndNNFs.remove(df.getOWLNothing());

		//Extract relationship rf2 file from nnfs
		printRelationshipRF2(nnfOntology, outputDirectory, effectiveTime);

		extractConceptAndDescriptionRF2(entitiesInSubontologyAndNNFs, inactiveConcepts, outputDirectory, sourceFile, rf2Cache, effectiveTime);

		//Extract OWLRefset rf2 file (and TextDefinitions file) from authoring definitions
		computeOWLRefsetAndTextDefinitions(outputDirectory, effectiveTime);
	}

	private static void extractConceptAndDescriptionRF2(Set<OWLEntity> entitiesToExtract, Set<Long> inactiveConcepts, File outputDirectory, File sourceRF2File, RF2InformationCache rf2Cache, String effectiveTime) throws IOException, ReleaseImportException {
		Set<Long> entityIDs = new HashSet<>();
		System.out.println("Extracting background RF2 information for entities in subontology.");
		System.out.println("Storing in " + new File(outputDirectory, "RF2"));
		for (OWLEntity ent : entitiesToExtract) {
			Long id = Long.parseLong(ent.toString().replaceFirst(IRI_PREFIX, "").replaceAll("[<>]", ""));
			entityIDs.add(id);
		}


		File subontologyRF2 = new File(outputDirectory, "RF2");
		Set<Long> allIds = new HashSet<>(entityIDs);
		allIds.addAll(collectModuleConcepts(entityIDs, rf2Cache));
		allIds.addAll(inactiveConcepts);
		allIds.add(Long.parseLong(TEST_SUBONTOLOGY_MODULE_CONCEPT));
		Map<Long, String> refsetsToInclude = new HashMap<>();
		for (Long concept : allIds) {
			if (rf2Cache.isRefset(concept)) {
				refsetsToInclude.put(concept, rf2Cache.getRefsetFilename(concept));
			}
		}
		new RF2ExtractionService().extractConcepts(new FileInputStream(sourceRF2File), allIds, refsetsToInclude, subontologyRF2, effectiveTime);
	}

	private static void printRelationshipRF2(OWLOntology nnfOntology, File outputDirectory, String effectiveTime) throws IOException, ConversionException {
		RF2Printer printer = new RF2Printer(outputDirectory);
		printer.printRelationshipRF2Files(nnfOntology, effectiveTime);
	}

	private static void computeOWLRefsetAndTextDefinitions(File outputDirectory, String effectiveTime) throws IOException, OWLException {
		OWLtoRF2Service owlToRF2Converter = new OWLtoRF2Service();
		try (InputStream owlFileStreamAuthoring = new BufferedInputStream(new FileInputStream(new File(outputDirectory, "subOntology.owl")))) {
			File rf2ZipAuthoring = new File(outputDirectory, OWLRefsetRF2Filename + ".zip");
			owlToRF2Converter.writeToRF2(owlFileStreamAuthoring, new FileOutputStream(rf2ZipAuthoring), parseEffectiveTime(effectiveTime));
		}
	}

	private static Date parseEffectiveTime(String effectiveTime) {
		try {
			return new SimpleDateFormat("yyyyMMdd").parse(effectiveTime);
		} catch (ParseException e) {
			throw new IllegalArgumentException("Invalid effective time: " + effectiveTime, e);
		}
	}

	private static Set<Long> collectModuleConcepts(Set<Long> entityIDs, RF2InformationCache rf2Cache) {
		System.out.println("Collecting module IDs for all extracted concepts...");
		
		// Collect module IDs from all concepts being extracted
		Set<Long> allModuleIds = rf2Cache.getModuleIds(entityIDs);
		allModuleIds.addAll(rf2Cache.getModuleIds(allModuleIds));
		for (Long moduleId : allModuleIds) {
			System.out.println("Found module concept: " + moduleId);
		}
		
		// Remove module IDs that are already in the entity set
		allModuleIds.removeAll(entityIDs);
		return allModuleIds;
	}

}
