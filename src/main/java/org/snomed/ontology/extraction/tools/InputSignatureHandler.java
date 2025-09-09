package org.snomed.ontology.extraction.tools;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.snomed.ontology.extraction.classification.OntologyReasoningService;
import org.snomed.ontology.extraction.services.RF2InformationCache;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

public class InputSignatureHandler {

	private static final String SNOMED_IRI_STRING = "http://snomed.info/id/";
	private static final String DESCENDANT_FLAG = "<<";
	private final OWLDataFactory owlDataFactory;

	public InputSignatureHandler() {
		owlDataFactory = OWLManager.createOWLOntologyManager().getOWLDataFactory();
	}

	public Set<OWLClass> extractRefsetClassesFromDescendents(OWLOntology inputOntology, OWLClass rootClass, boolean excludePrimitives) {
		OntologyReasoningService service = new OntologyReasoningService(inputOntology);
		service.classifyOntology();
		Set<OWLClass> conceptsInRefset = new HashSet<>();

		conceptsInRefset.add(rootClass);
		for(OWLClass childCls:service.getDescendants(rootClass)) {
			if(!childCls.toString().equals("owl:Nothing")) {
				if (excludePrimitives) {
					if (!service.isPrimitive(childCls)) {
						System.out.println("Class: " + childCls);
						conceptsInRefset.add(childCls);
					}
					continue;
				}
				conceptsInRefset.add(childCls);
			}
		}
		System.out.println("Total classes in refset: " + conceptsInRefset);

		return conceptsInRefset;
	}

	public void printRefset(Set<OWLEntity> entitiesInRefset, String outputFilePath) throws IOException {
		try (BufferedWriter writer = new BufferedWriter (new OutputStreamWriter(new FileOutputStream(outputFilePath), StandardCharsets.UTF_8))) {
			for (OWLEntity ent : entitiesInRefset) {
				System.out.println("cls: " + ent.toString());
				String entID = ent.toString().replaceAll("[<>]", "");
				writer.write(entID.replace(SNOMED_IRI_STRING, ""));
				writer.newLine();
			}
		}
	}

	public Set<OWLClass> readRefset(File refsetFile) {
		if(refsetFile.getName().endsWith(".json")) {
			return readRefsetJson(refsetFile);
		}
		return readRefsetTxt(refsetFile);
	}

	public Set<OWLClass> readRefsetWithDescendantsAndTracking(File refsetFile,
			RF2InformationCache rf2Cache,
			Set<Long> inactiveConcepts, Set<Long> missingConcepts) {

		Set<OWLClass> owlClasses;
		if (refsetFile.getName().endsWith(".json")) {
			owlClasses = readRefsetJson(refsetFile);
		} else {
			owlClasses = readRefsetTxtWithDescendantsAndTracking(refsetFile, rf2Cache, inactiveConcepts, missingConcepts);
		}
		addMetadataConcept(owlClasses);
		return owlClasses;

	}

	public Set<OWLClass> addMetadataConcept(Set<OWLClass> existingSet) {
		// Add metadata concepts
		addMetadataConcepts(existingSet,
				"138875005 |SNOMED CT Concept (SNOMED RT+CTV3)|",
				"900000000000441003 |SNOMED CT Model Component (metadata)|",
				"106237007 |Linkage concept (linkage concept)|",
				"246061005 |Attribute (attribute)|",
				"116680003 |Is a (attribute)|",
				"410662002 |Concept model attribute (attribute)|",
				"900000000000443000 |Module (core metadata concept)|",

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

		return null;
	}

	private Set<OWLClass> readRefsetJson(File refsetFile) {
		Set<OWLClass> classes = new HashSet<>();
		try (BufferedReader br = new BufferedReader(new FileReader(refsetFile))) {
			String inLine;
			br.readLine();
			while ((inLine = br.readLine()) != null) {
				// process the line.
				System.out.println("Adding class: " + inLine + " to input");
				classes.add(getOwlClass(inLine));
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return classes;
	}

	private Set<OWLClass> readRefsetTxt(File refsetFile) {
		Set<OWLClass> classes = new HashSet<>();
		try (BufferedReader br = new BufferedReader(new FileReader(refsetFile))) {
			String inLine;
			while ((inLine = br.readLine()) != null) {
				// process the line, remove whitespace
				if(inLine.matches(".*\\d+.*")) {
					inLine = inLine.replaceAll("[\\s\\p{Z}]+", "").trim();
					classes.add(getOwlClass(inLine));
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println(classes.size() + " identifiers read from input subset.");
		return classes;
	}

	private OWLClass getOwlClass(String conceptCode) {
		return owlDataFactory.getOWLClass(IRI.create(SNOMED_IRI_STRING + conceptCode));
	}

	private Set<OWLClass> readRefsetTxtWithDescendantsAndTracking(File refsetFile,
			RF2InformationCache rf2Cache,
			Set<Long> inactiveConcepts, Set<Long> missingConcepts) {

		Set<OWLClass> classes = new HashSet<>();

		Set<Long> allRequestedConcepts = new HashSet<>();
		Set<Long> directConcepts = new HashSet<>();
		Set<Long> conceptsWithDescendants = new HashSet<>();
		readSubset(refsetFile, allRequestedConcepts, directConcepts, conceptsWithDescendants);

		Set<Long> allRequiredConcepts = new HashSet<>(allRequestedConcepts);

		// If RF2 cache is provided, and we have concepts with descendants, load hierarchy
		if (rf2Cache != null && !conceptsWithDescendants.isEmpty()) {
			Set<Long> descendants = loadDescendantsFromRF2Cache(rf2Cache, conceptsWithDescendants);
			allRequiredConcepts.addAll(descendants);
		} else if (!conceptsWithDescendants.isEmpty()) {
			// If no RF2 cache but we have descendant flags, just add the root concepts
			System.out.println("Warning: RF2 information cache not provided, adding only root concepts for descendant flags");
		}

		// Check for inactive/missing concepts using RF2 cache
		if (rf2Cache != null) {
			checkForInactiveAndMissingConcepts(rf2Cache, allRequestedConcepts, inactiveConcepts, missingConcepts);
		}

		allRequiredConcepts.removeAll(missingConcepts);

		if (rf2Cache != null) {
			// Add concepts required through refsets, for example the historical association refsets
			Set<Long> associatedConcepts = loadAssociatedConceptsFromRF2Cache(rf2Cache, allRequiredConcepts);
			allRequiredConcepts.addAll(associatedConcepts);
		}

		for (Long conceptId : allRequiredConcepts) {
			classes.add(owlDataFactory.getOWLClass(IRI.create(SNOMED_IRI_STRING + conceptId)));
		}

		System.out.println(classes.size() + " identifiers read from input subset.");
		return classes;
	}

	private void readSubset(File refsetFile, Set<Long> allRequestedConcepts, Set<Long> directConcepts, Set<Long> conceptsWithDescendants) {
		try (BufferedReader br = new BufferedReader(new FileReader(refsetFile))) {
			String inLine;
			while ((inLine = br.readLine()) != null) {
				// process the line, remove whitespace
				if(inLine.matches(".*\\d+.*")) {
					inLine = inLine.trim();
					readSubsetLine(inLine, allRequestedConcepts, directConcepts, conceptsWithDescendants);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void readSubsetLine(String inLine, Set<Long> allRequestedConcepts, Set<Long> directConcepts, Set<Long> conceptsWithDescendants) {
		// Check if line contains the descendant flag
		if (inLine.contains(DESCENDANT_FLAG)) {
			// Extract concept ID from line with flag, handling optional terms
			String conceptId = extractConceptIdFromLine(inLine, DESCENDANT_FLAG);
			if (conceptId != null && conceptId.matches("\\d+")) {
				Long conceptIdLong = Long.parseLong(conceptId);
				conceptsWithDescendants.add(conceptIdLong);
				allRequestedConcepts.add(conceptIdLong);
				System.out.println("Adding concept with descendants: " + conceptId);
			}
		} else {
			// Regular concept ID, handling optional terms
			String conceptId = extractConceptIdFromLine(inLine, null);
			if (conceptId != null && conceptId.matches("\\d+")) {
				Long conceptIdLong = Long.parseLong(conceptId);
				directConcepts.add(conceptIdLong);
				allRequestedConcepts.add(conceptIdLong);
				System.out.println("Adding direct concept: " + conceptId);
			}
		}
	}

	/**
	 * Extracts concept ID from a line that may contain optional terms in the format "conceptId |term|"
	 *
	 * @param line The input line to parse
	 * @param flag Optional flag to remove from the line (e.g., "<<")
	 * @return The concept ID as a string, or null if not found
	 */
	private String extractConceptIdFromLine(String line, String flag) {
		// Remove the flag if present
		if (flag != null && line.contains(flag)) {
			line = line.replaceAll(".*" + flag + "\\s*", "").trim();
		}

		// Handle lines with optional terms in format "conceptId |term|"
		if (line.contains("|")) {
			// Extract the part before the first pipe
			String beforePipe = line.split("\\|")[0].trim();
			if (beforePipe.matches("\\d+")) {
				return beforePipe;
			}
		}

		// Handle lines without terms - extract just the concept ID
		String conceptId = line.replaceAll("[\\s\\p{Z}]+", "").trim();
		if (conceptId.matches("\\d+")) {
			return conceptId;
		}

		return null;
	}

	private void checkForInactiveAndMissingConcepts(RF2InformationCache rf2Cache,
			Set<Long> requestedConcepts, Set<Long> inactiveConcepts, Set<Long> missingConcepts) {
		// Use the cached RF2 information for efficient lookup
		for (Long conceptId : requestedConcepts) {
			if (!rf2Cache.conceptExists(conceptId)) {
				missingConcepts.add(conceptId);
				System.out.println("Warning: Concept " + conceptId + " not found in RF2 archive");
			} else if (!rf2Cache.isConceptActive(conceptId)) {
				inactiveConcepts.add(conceptId);
				System.out.println("Warning: Concept " + conceptId + " is inactive in RF2 archive");
			}
		}
	}

	private Set<Long> loadDescendantsFromRF2Cache(RF2InformationCache rf2Cache, Set<Long> rootConcepts) {
		Set<Long> all = new HashSet<>(rootConcepts);
		for (Long rootConcept : rootConcepts) {
			all.addAll(rf2Cache.getConceptDescendants(rootConcept));
		}
		return all;
	}

	private Set<Long> loadAssociatedConceptsFromRF2Cache(RF2InformationCache rf2Cache, Set<Long> sourceConcepts) {
		Set<Long> associated = new HashSet<>();
		Set<Long> newAssociations;
		do {
			newAssociations = new HashSet<>();
			for (Long concept : sourceConcepts) {
				newAssociations.addAll(rf2Cache.getConceptAssociations(concept));
			}
			associated.addAll(newAssociations);
			sourceConcepts = newAssociations;
		} while (!newAssociations.isEmpty());
		return associated;
	}

	private void addMetadataConcepts(Set<OWLClass> owlClasses, String... conceptIdTerm) {
		int meta = 0;
		for (String idTerm : conceptIdTerm) {
			String code = idTerm.substring(0, idTerm.indexOf(" "));
			owlClasses.add(getOwlClass(code));
			meta++;
		}
		System.out.printf("Added %s metadata concepts.%n", meta);
	}

	// Hacky test method
	public static void main(String[] args) throws OWLOntologyCreationException, IOException {
		String inputPath = "snomed-files/";
		File inputOntologyFile = new File(inputPath + "sct-injury.owl");

		OWLOntologyManager man = OWLManager.createOWLOntologyManager();
		OWLOntology inputOntology = man.loadOntologyFromOntologyDocument(inputOntologyFile);
		Set<OWLClass> conceptsToInclude = new HashSet<>();
		InputSignatureHandler handler = new InputSignatureHandler();
		conceptsToInclude.add(handler.getOwlClass("417163006"));

		Set<OWLClass> classes = new HashSet<>();
		for(OWLClass cls:conceptsToInclude) {
			classes.addAll(handler.extractRefsetClassesFromDescendents(inputOntology, cls, false));
		}

		String outputFilePath = "refsets/injury/injury_refset.txt";
		handler.printRefset(new HashSet<>(classes), outputFilePath);
	}
}
