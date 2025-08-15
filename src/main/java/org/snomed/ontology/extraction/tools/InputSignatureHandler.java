package org.snomed.ontology.extraction.tools;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.snomed.ontology.extraction.classification.OntologyReasoningService;
import org.snomed.ontology.extraction.exception.ReasonerException;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

public abstract class InputSignatureHandler {

	private static final String SNOMED_IRI_STRING = "http://snomed.info/id/";
	private static final String DESCENDANT_FLAG = "<<";

	public static Set<OWLClass> extractRefsetClassesFromDescendents(OWLOntology inputOntology, OWLClass rootClass, boolean excludePrimitives) {
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

	public static void printRefset(Set<OWLEntity> entitiesInRefset, String outputFilePath) throws IOException {
		try (BufferedWriter writer = new BufferedWriter (new OutputStreamWriter(new FileOutputStream(outputFilePath), StandardCharsets.UTF_8))) {
			for (OWLEntity ent : entitiesInRefset) {
				System.out.println("cls: " + ent.toString());
				String entID = ent.toString().replaceAll("[<>]", "");
				writer.write(entID.replace(SNOMED_IRI_STRING, ""));
				writer.newLine();
			}
		}
	}

	public static Set<OWLClass> readRefset(File refsetFile) {
		if(refsetFile.getName().endsWith(".json")) {
			return readRefsetJson(refsetFile);
		}
		return readRefsetTxt(refsetFile);
	}

	public static Set<OWLClass> readRefsetWithDescendantsAndTracking(File refsetFile,
			org.snomed.ontology.extraction.services.RF2InformationCache rf2Cache, 
			Set<Long> inactiveConcepts, Set<Long> missingConcepts) {
		if(refsetFile.getName().endsWith(".json")) {
			return readRefsetJson(refsetFile);
		}
		return readRefsetTxtWithDescendantsAndTracking(refsetFile, rf2Cache, inactiveConcepts, missingConcepts);
	}

	private static Set<OWLClass> readRefsetJson(File refsetFile) {
		OWLDataFactory df = OWLManager.createOWLOntologyManager().getOWLDataFactory();
		Set<OWLClass> classes = new HashSet<>();
		try (BufferedReader br = new BufferedReader(new FileReader(refsetFile))) {
			String inLine;
			br.readLine();
			while ((inLine = br.readLine()) != null) {
				// process the line.
				System.out.println("Adding class: " + inLine + " to input");
				classes.add(df.getOWLClass(IRI.create(SNOMED_IRI_STRING + inLine)));
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return classes;
	}

	private static Set<OWLClass> readRefsetTxt(File refsetFile) {
		OWLDataFactory df = OWLManager.createOWLOntologyManager().getOWLDataFactory();
		Set<OWLClass> classes = new HashSet<>();
		try (BufferedReader br = new BufferedReader(new FileReader(refsetFile))) {
			String inLine;
			while ((inLine = br.readLine()) != null) {
				// process the line, remove whitespace
				if(inLine.matches(".*\\d+.*")) {
					inLine = inLine.replaceAll("[\\s\\p{Z}]+", "").trim();
					classes.add(df.getOWLClass(IRI.create(SNOMED_IRI_STRING + inLine)));
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println(classes.size() + " identifiers read from input subset.");
		return classes;
	}

	private static Set<OWLClass> readRefsetTxtWithDescendantsAndTracking(File refsetFile,
			org.snomed.ontology.extraction.services.RF2InformationCache rf2Cache, 
			Set<Long> inactiveConcepts, Set<Long> missingConcepts) {

		OWLDataFactory df = OWLManager.createOWLOntologyManager().getOWLDataFactory();
		Set<OWLClass> classes = new HashSet<>();
		Set<Long> conceptsWithDescendants = new HashSet<>();
		Set<Long> directConcepts = new HashSet<>();
		Set<Long> allRequestedConcepts = new HashSet<>();
		
		try (BufferedReader br = new BufferedReader(new FileReader(refsetFile))) {
			String inLine = "";
			while ((inLine = br.readLine()) != null) {
				// process the line, remove whitespace
				if(inLine.matches(".*\\d+.*")) {
					inLine = inLine.trim();
					
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
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		// Add direct concepts
		for (Long conceptId : directConcepts) {
			classes.add(df.getOWLClass(IRI.create(SNOMED_IRI_STRING + conceptId)));
		}
		
		// If RF2 cache is provided and we have concepts with descendants, load hierarchy
		if (rf2Cache != null && !conceptsWithDescendants.isEmpty()) {
			Set<Long> allDescendants = loadDescendantsFromRF2Cache(rf2Cache, conceptsWithDescendants);
			for (Long conceptId : allDescendants) {
				classes.add(df.getOWLClass(IRI.create(SNOMED_IRI_STRING + conceptId)));
			}
		} else if (!conceptsWithDescendants.isEmpty()) {
			// If no RF2 cache but we have descendant flags, just add the root concepts
			System.out.println("Warning: RF2 information cache not provided, adding only root concepts for descendant flags");
			for (Long conceptId : conceptsWithDescendants) {
				classes.add(df.getOWLClass(IRI.create(SNOMED_IRI_STRING + conceptId)));
			}
		}
		
		// Check for inactive/missing concepts using RF2 cache
		if (rf2Cache != null) {
			checkForInactiveAndMissingConcepts(rf2Cache, allRequestedConcepts, inactiveConcepts, missingConcepts);
		}
		
		System.out.println(classes.size() + " identifiers read from input subset.");
		return classes;
	}

	/**
	 * Extracts concept ID from a line that may contain optional terms in the format "conceptId |term|"
	 * 
	 * @param line The input line to parse
	 * @param flag Optional flag to remove from the line (e.g., "<<")
	 * @return The concept ID as a string, or null if not found
	 */
	private static String extractConceptIdFromLine(String line, String flag) {
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

	private static void checkForInactiveAndMissingConcepts(org.snomed.ontology.extraction.services.RF2InformationCache rf2Cache,
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

	private static Set<Long> loadDescendantsFromRF2Cache(org.snomed.ontology.extraction.services.RF2InformationCache rf2Cache, 
			Set<Long> rootConcepts) {
		// Note: Parent-child relationship data is not available in the current cache implementation
		// The cache only stores concept activity status and module mappings
		// For now, we'll return just the root concepts and log a warning
		System.out.println("Warning: Descendant loading not available - cache only contains concept activity and module data");
		return new HashSet<>(rootConcepts);
	}

	public static void main(String[] args) throws OWLOntologyCreationException, IOException {
		String inputPath = "E:/Users/warren/Documents/aPostdoc/SCT-files/";
		File inputOntologyFile = new File(inputPath + "sct-injury.owl");

		OWLOntologyManager man = OWLManager.createOWLOntologyManager();
		OWLDataFactory df = man.getOWLDataFactory();
		OWLOntology inputOntology = man.loadOntologyFromOntologyDocument(inputOntologyFile);
		String snomedIRIString = "http://snomed.info/id/";
		Set<OWLClass> conceptsToInclude = new HashSet<>();
		conceptsToInclude.add(df.getOWLClass(IRI.create(snomedIRIString + "417163006")));

		Set<OWLClass> classes = new HashSet<>();
		for(OWLClass cls:conceptsToInclude) {
			classes.addAll(InputSignatureHandler.extractRefsetClassesFromDescendents(inputOntology, cls, false));
		}

		String outputFilePath = "E:/Users/warren/Documents/aPostdoc/IAA-content-extraction/refsets/injury/injury_refset.txt";
		InputSignatureHandler.printRefset(new HashSet<>(classes), outputFilePath);
	}
}
