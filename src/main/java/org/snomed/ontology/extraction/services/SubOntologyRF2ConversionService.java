package org.snomed.ontology.extraction.services;

import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.snomed.ontology.extraction.writers.OWLtoRF2Service;
import org.snomed.ontology.extraction.writers.RF2Printer;
import org.snomed.otf.owltoolkit.conversion.ConversionException;

import java.io.*;
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
		printRelationshipRF2(nnfOntology, outputDirectory);

		extractConceptAndDescriptionRF2(entitiesInSubontologyAndNNFs, outputDirectory, sourceFile);

		//Extract OWLRefset rf2 file (and TextDefinitions file) from authoring definitions
		computeOWLRefsetAndTextDefinitions(outputDirectory);
	}

	private static void extractConceptAndDescriptionRF2(Set<OWLEntity> entitiesToExtract, File outputDirectory, File backgroundFile) throws IOException, ReleaseImportException {
		Set<Long> entityIDs = new HashSet<>();
		System.out.println("Extracting background RF2 information for entities in subontology.");
		System.out.println("Storing in " + new File(outputDirectory, "RF2"));
		for (OWLEntity ent : entitiesToExtract) {
			Long id = Long.parseLong(ent.toString().replaceFirst(IRI_PREFIX, "").replaceAll("[<>]", ""));
			entityIDs.add(id);
		}

		// Add metadata concepts
		addMetadataConcepts(entityIDs,
		 		"138875005 |SNOMED CT Concept (SNOMED RT+CTV3)|",
				"900000000000441003 |SNOMED CT Model Component (metadata)|",
				"106237007 |Linkage concept (linkage concept)|",
				"246061005 |Attribute (attribute)|",
				"116680003 |Is a (attribute)|",
				"410662002 |Concept model attribute (attribute)|",

				// << 900000000000444006 |Definition status|
				"900000000000444006 |Definition status (core metadata concept)|",
				"900000000000074008 |Not sufficiently defined by necessary conditions definition status (core metadata concept)|",
				"900000000000073002 |Sufficiently defined by necessary conditions definition status (core metadata concept)|",

				// << 900000000000446008 |Description type|
				"900000000000446008 |Description type (core metadata concept)|",
				"900000000000003001 |Fully specified name (core metadata concept)|",
				"900000000000550004 |Definition (core metadata concept)|",
				"900000000000013009 |Synonym (core metadata concept)|",

				 // << 900000000000447004 |Case significance|
				"900000000000447004 |Case significance (core metadata concept)|",
				"900000000000448009 |Entire term case insensitive (core metadata concept)|",
				"900000000000020002 |Only initial character case insensitive (core metadata concept)|",
				"900000000000017005 |Entire term case sensitive (core metadata concept)|",

				 // << 900000000000449001 |Characteristic type|
				"900000000000449001 |Characteristic type (core metadata concept)|",
				"900000000000006009 |Defining relationship (core metadata concept)|",
				"900000000000010007 |Stated relationship (core metadata concept)|",
				"900000000000011006 |Inferred relationship (core metadata concept)|",
				"900000000000225001 |Qualifying relationship (core metadata concept)|",
				"900000000000227009 |Additional relationship (core metadata concept)|",

				// << 900000000000450001 |Modifier|
				"900000000000450001 |Modifier (core metadata concept)|",
				"900000000000451002 |Existential restriction modifier (core metadata concept)|",
				"900000000000452009 |Universal restriction modifier (core metadata concept)|",

				// <<900000000000511003 |Acceptability|
				"900000000000511003 |Acceptability (foundation metadata concept)|",
				"900000000000548007 |Preferred (foundation metadata concept)|",
				"900000000000549004 |Acceptable (foundation metadata concept)|"
				);

		File subontologyRF2 = new File(outputDirectory, "RF2");
		new RF2ExtractionService().extractConcepts(new FileInputStream(backgroundFile), entityIDs, subontologyRF2);
	}

	private static void addMetadataConcepts(Set<Long> entityIDs, String... conceptIdTerm) {
		for (String idTerm : conceptIdTerm) {
			entityIDs.add(Long.parseLong(idTerm.substring(0, idTerm.indexOf(" "))));
		}
	}

	private static void printRelationshipRF2(OWLOntology nnfOntology, File outputDirectory) throws IOException, ConversionException {
		RF2Printer printer = new RF2Printer(outputDirectory);
		printer.printRelationshipRF2Files(nnfOntology);
	}

	private static void computeOWLRefsetAndTextDefinitions(File outputDirectory) throws IOException, OWLException {
		OWLtoRF2Service owlToRF2Converter = new OWLtoRF2Service();
		try (InputStream owlFileStreamAuthoring = new BufferedInputStream(new FileInputStream(new File(outputDirectory, "subOntology.owl")))) {
			File rf2ZipAuthoring = new File(outputDirectory, OWLRefsetRF2Filename + ".zip");
			owlToRF2Converter.writeToRF2(owlFileStreamAuthoring, new FileOutputStream(rf2ZipAuthoring), new Date());
		}
	}

}
