package org.snomed.ontology.extraction.tools;

import it.unimi.dsi.fastutil.Pair;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.snomed.ontology.extraction.classification.OntologyReasoningService;
import org.snomed.ontology.extraction.exception.ReasonerException;
import org.snomed.ontology.extraction.services.RF2ExtractionService;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public abstract class InputSignatureHandler {

	private static final String snomedIRIString = "http://snomed.info/id/";
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
		Charset UTF_8_CHARSET = StandardCharsets.UTF_8;
		BufferedWriter writer = new BufferedWriter (new OutputStreamWriter(new FileOutputStream(outputFilePath), UTF_8_CHARSET));
		for(OWLEntity ent:entitiesInRefset) {
			System.out.println("cls: " + ent.toString());
			String entID = ent.toString().replaceAll("[<>]", "");
			writer.write(entID.replace("http://snomed.info/id/",""));
			writer.newLine();
		}
		writer.close();
	}

	public static Set<OWLClass> readRefset(File refsetFile) {
		if(refsetFile.getName().endsWith(".json")) {
			return readRefsetJson(refsetFile);
		}
		return readRefsetTxt(refsetFile);
	}

	/**
	 * Reads a subset file that may contain concept IDs with a "<<" flag to include descendants.
	 * When RF2 snapshot archive is provided, uses the hierarchy to find descendants of flagged concepts.
	 * 
	 * @param refsetFile The subset file to read
	 * @param rf2SnapshotArchive Optional RF2 snapshot archive for hierarchy lookup
	 * @return Set of OWL classes representing the concepts to include
	 */
	public static Set<OWLClass> readRefsetWithDescendants(File refsetFile, File rf2SnapshotArchive) {
		if(refsetFile.getName().endsWith(".json")) {
			return readRefsetJson(refsetFile);
		}
		return readRefsetTxtWithDescendants(refsetFile, rf2SnapshotArchive);
	}

	/**
	 * Reads a subset file and tracks inactive/missing concepts for RF2 output.
	 * 
	 * @param refsetFile The subset file to read
	 * @param rf2SnapshotArchive Optional RF2 snapshot archive for hierarchy lookup
	 * @param inactiveConcepts Output set to collect inactive concept IDs
	 * @param missingConcepts Output set to collect missing concept IDs
	 * @return Set of OWL classes representing the concepts to include
	 */
	public static Set<OWLClass> readRefsetWithDescendantsAndTracking(File refsetFile, File rf2SnapshotArchive, 
			Set<Long> inactiveConcepts, Set<Long> missingConcepts) {
		if(refsetFile.getName().endsWith(".json")) {
			return readRefsetJson(refsetFile);
		}
		return readRefsetTxtWithDescendantsAndTracking(refsetFile, rf2SnapshotArchive, inactiveConcepts, missingConcepts);
	}

	private static Set<OWLClass> readRefsetJson(File refsetFile) {
		OWLDataFactory df = OWLManager.createOWLOntologyManager().getOWLDataFactory();
		Set<OWLClass> classes = new HashSet<OWLClass>();
		try (BufferedReader br = new BufferedReader(new FileReader(refsetFile))) {
			String inLine = "";
			br.readLine();
			while ((inLine = br.readLine()) != null) {
				// process the line.
				System.out.println("Adding class: " + inLine + " to input");
				classes.add(df.getOWLClass(IRI.create(snomedIRIString + inLine)));
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
			String inLine = "";
			//br.readLine();
			while ((inLine = br.readLine()) != null) {
				// process the line, remove whitespace
				if(inLine.matches(".*\\d+.*")) {
					inLine = inLine.replaceAll("[\\s\\p{Z}]+", "").trim();
//					System.out.println("Adding class: " + inLine + " to input");
					//if()
					classes.add(df.getOWLClass(IRI.create(snomedIRIString + inLine)));
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println(classes.size() + " identifiers read from input subset.");
		return classes;
	}

	private static Set<OWLClass> readRefsetTxtWithDescendants(File refsetFile, File rf2SnapshotArchive) {
		Set<Long> inactiveConcepts = new HashSet<>();
		Set<Long> missingConcepts = new HashSet<>();
		return readRefsetTxtWithDescendantsAndTracking(refsetFile, rf2SnapshotArchive, inactiveConcepts, missingConcepts);
	}

	private static Set<OWLClass> readRefsetTxtWithDescendantsAndTracking(File refsetFile, File rf2SnapshotArchive, 
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
			classes.add(df.getOWLClass(IRI.create(snomedIRIString + conceptId)));
		}
		
		// If RF2 archive is provided and we have concepts with descendants, load hierarchy
		if (rf2SnapshotArchive != null && !conceptsWithDescendants.isEmpty()) {
			Set<Long> allDescendants = loadDescendantsFromRF2(rf2SnapshotArchive, conceptsWithDescendants);
			for (Long conceptId : allDescendants) {
				classes.add(df.getOWLClass(IRI.create(snomedIRIString + conceptId)));
			}
		} else if (!conceptsWithDescendants.isEmpty()) {
			// If no RF2 archive but we have descendant flags, just add the root concepts
			System.out.println("Warning: RF2 snapshot archive not provided, adding only root concepts for descendant flags");
			for (Long conceptId : conceptsWithDescendants) {
				classes.add(df.getOWLClass(IRI.create(snomedIRIString + conceptId)));
			}
		}
		
		// Check for inactive/missing concepts if RF2 archive is provided
		if (rf2SnapshotArchive != null) {
			checkForInactiveAndMissingConcepts(rf2SnapshotArchive, allRequestedConcepts, inactiveConcepts, missingConcepts);
		}
		
		System.out.println(classes.size() + " identifiers read from input subset.");
		return classes;
	}

	private static Set<Long> loadDescendantsFromRF2(File rf2SnapshotArchive, Set<Long> rootConcepts) {
		Set<Long> allDescendants = new HashSet<>();
		Map<Long, Set<Long>> parentChildMap = new HashMap<>();
		
		try {
			RF2ExtractionService extractionService = new RF2ExtractionService();
			
			// Load parent-child relationships from RF2
			Consumer<Pair<Long, Long>> parentChildPairConsumer = pair -> {
				Long parent = pair.left();
				Long child = pair.right();
				parentChildMap.computeIfAbsent(parent, k -> new HashSet<>()).add(child);
			};
			
			extractionService.extractParentChildRelationships(
				new FileInputStream(rf2SnapshotArchive), 
				parentChildPairConsumer
			);
			
			// Find all descendants for each root concept
			for (Long rootConcept : rootConcepts) {
				Set<Long> descendants = findDescendants(rootConcept, parentChildMap);
				allDescendants.addAll(descendants);
				System.out.println("Found " + descendants.size() + " descendants for concept " + rootConcept);
			}
			
		} catch (Exception e) {
			System.err.println("Error loading descendants from RF2: " + e.getMessage());
			e.printStackTrace();
			// Fallback: just add the root concepts
			allDescendants.addAll(rootConcepts);
		}
		
		return allDescendants;
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

	private static Set<Long> findDescendants(Long rootConcept, Map<Long, Set<Long>> parentChildMap) {
		Set<Long> descendants = new HashSet<>();
		Set<Long> toProcess = new HashSet<>();
		toProcess.add(rootConcept);
		
		while (!toProcess.isEmpty()) {
			Long current = toProcess.iterator().next();
			toProcess.remove(current);
			
			Set<Long> children = parentChildMap.get(current);
			if (children != null) {
				for (Long child : children) {
					if (!descendants.contains(child)) {
						descendants.add(child);
						toProcess.add(child);
					}
				}
			}
		}
		
		return descendants;
	}

	/**
	 * Checks for inactive and missing concepts in the RF2 archive
	 * 
	 * @param rf2SnapshotArchive The RF2 snapshot archive to check
	 * @param requestedConcepts Set of concept IDs requested in the subset
	 * @param inactiveConcepts Output set to collect inactive concept IDs
	 * @param missingConcepts Output set to collect missing concept IDs
	 */
	private static void checkForInactiveAndMissingConcepts(File rf2SnapshotArchive, Set<Long> requestedConcepts, 
			Set<Long> inactiveConcepts, Set<Long> missingConcepts) {
		try {
			Set<Long> foundConcepts = new HashSet<>();
			Set<Long> inactiveFoundConcepts = new HashSet<>();
			
			// Create a component factory to collect concept information
			Consumer<ConceptInfo> conceptInfoConsumer = conceptInfo -> {
				if (requestedConcepts.contains(conceptInfo.conceptId)) {
					foundConcepts.add(conceptInfo.conceptId);
					if ("0".equals(conceptInfo.active)) {
						inactiveFoundConcepts.add(conceptInfo.conceptId);
					}
				}
			};
			
			// Load all concepts from RF2 (including inactive ones)
			RF2ExtractionService extractionService = new RF2ExtractionService();
			extractionService.extractConceptsOnly(
				new FileInputStream(rf2SnapshotArchive), 
				conceptInfoConsumer
			);
			
			// Determine missing and inactive concepts
			for (Long conceptId : requestedConcepts) {
				if (!foundConcepts.contains(conceptId)) {
					missingConcepts.add(conceptId);
					System.out.println("Warning: Concept " + conceptId + " not found in RF2 archive");
				} else if (inactiveFoundConcepts.contains(conceptId)) {
					inactiveConcepts.add(conceptId);
					System.out.println("Warning: Concept " + conceptId + " is inactive in RF2 archive");
				}
			}
			
		} catch (Exception e) {
			System.err.println("Error checking for inactive/missing concepts: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Simple data class to hold concept information
	 */
	public static class ConceptInfo {
		public final Long conceptId;
		public final String active;
		public final String moduleId;
		
		public ConceptInfo(Long conceptId, String active, String moduleId) {
			this.conceptId = conceptId;
			this.active = active;
			this.moduleId = moduleId;
		}
	}

	public static Set<OWLClass> readClassesNonSCTFile(File signatureFile, String inputIRI) {
		OWLDataFactory df = OWLManager.createOWLOntologyManager().getOWLDataFactory();
		Set<OWLClass> classes = new HashSet<>();
		try (BufferedReader br = new BufferedReader(new FileReader(signatureFile))) {
			String inLine = "";
			br.readLine();
			while ((inLine = br.readLine()) != null) {
				// process the line.
				System.out.println("Adding class: " + inLine + " to input");
				//if()
				classes.add(df.getOWLClass(IRI.create(inputIRI + inLine)));
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return classes;
	}

	public static void main(String[] args) throws OWLOntologyCreationException, ReasonerException, IOException {
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
