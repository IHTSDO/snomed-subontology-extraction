package org.snomed.ontology.extraction.services;

import org.apache.commons.io.FileUtils;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.snomed.ontology.extraction.writers.OWLtoRF2Service;
import org.snomed.ontology.extraction.writers.RF2Printer;
import org.snomed.otf.owltoolkit.conversion.ConversionException;

import java.io.*;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

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
	/*
	public SubOntologyRF2Converter(String outputPath, String backgroundFilePath) {
		this.outputPath = outputPath;
		this.backgroundFilePath = backgroundFilePath;
	}
	 */

	//TODO: reduce redundancy in file extraction, automatically construct RF2 zip file
	public static void convertSubOntologytoRF2(OWLOntology subOntology, OWLOntology nnfOntology, File outputDirectory, File sourceFile) throws ReleaseImportException, IOException,
			OWLException, ConversionException {
		//Extract the concept and description RF2 files, based on the source ontology (includes all entities in subontology)
		Set<OWLEntity> entitiesInSubontologyAndNNFs = new HashSet<>();
		entitiesInSubontologyAndNNFs.addAll(subOntology.getClassesInSignature());
		entitiesInSubontologyAndNNFs.addAll(subOntology.getObjectPropertiesInSignature());
		entitiesInSubontologyAndNNFs.addAll(nnfOntology.getClassesInSignature());
		entitiesInSubontologyAndNNFs.addAll(nnfOntology.getObjectPropertiesInSignature());

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
		printRelationshipRF2(nnfOntology, outputDirectory);

		extractConceptAndDescriptionRF2(entitiesInSubontologyAndNNFs, outputDirectory, sourceFile);

		//Extract OWLRefset rf2 file (and TextDefinitions file) from authoring definitions
		computeOWLRefsetAndTextDefinitions(outputDirectory);
	}

	private static void extractConceptAndDescriptionRF2(Set<OWLEntity> entitiesToExtract, File outputDirectory, File backgroundFile) throws IOException, ReleaseImportException {
		Set<Long> entityIDs = new HashSet<>();
		System.out.println("Extracting background RF2 information for entities in subontology.");
		System.out.println("Storing in " + new File(outputDirectory, "RF2"));
		for(OWLEntity ent:entitiesToExtract) {
			Long id = Long.parseLong(ent.toString().replaceFirst(IRI_PREFIX, "").replaceAll("[<>]", ""));
			entityIDs.add(id);
		}

		//TODO: add metadata concepts manually for now, improve later. -- needed?
		entityIDs.addAll(Arrays.asList(Long.parseLong("116680003"), Long.parseLong("410662002"), Long.parseLong("900000000000441003"), Long.parseLong("138875005")));

		File subontologyRF2 = new File(outputDirectory, "RF2");
		new RF2ExtractionService().extractConcepts(new FileInputStream(backgroundFile), entityIDs, subontologyRF2);
	}

	private static void printRelationshipRF2(OWLOntology nnfOntology, File outputDirectory) throws IOException, ConversionException {
		RF2Printer printer = new RF2Printer(outputDirectory);
		printer.printRelationshipRF2File(nnfOntology);
	}

	private static void computeOWLRefsetAndTextDefinitions(File outputDirectory) throws IOException, OWLException {
		OWLtoRF2Service owlToRF2Converter = new OWLtoRF2Service();
		try (InputStream owlFileStreamAuthoring = new BufferedInputStream(new FileInputStream(new File(outputDirectory, "subOntology.owl")))) {
			File rf2ZipAuthoring = new File(outputDirectory, OWLRefsetRF2Filename + ".zip");
			owlToRF2Converter.writeToRF2(owlFileStreamAuthoring, new FileOutputStream(rf2ZipAuthoring), new Date());
		}
	}

}
