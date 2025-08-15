package org.snomed.ontology.extraction.manualtests;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.apache.commons.io.FileUtils;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.ihtsdo.otf.snomedboot.ReleaseImporter;
import org.ihtsdo.otf.snomedboot.factory.ImpotentComponentFactory;
import org.ihtsdo.otf.snomedboot.factory.LoadingProfile;
import org.snomed.otf.owltoolkit.conversion.AxiomRelationshipConversionService;
import org.snomed.otf.owltoolkit.conversion.ConversionException;
import org.snomed.otf.owltoolkit.domain.AxiomRepresentation;
import org.snomed.otf.owltoolkit.domain.Relationship;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class SubontologyAxiomIntegrityChecker {

	public static void main(String[] args) throws Exception {
		new SubontologyAxiomIntegrityChecker().run(args[0], args[1]);
	}

	private final Map<Long, Set<Long>> inSubsetMissingRelatedConcept = new Long2ObjectOpenHashMap<>();
	private final Map<Long, Set<Long>> outsideSubsetMissingRelatedConcept = new Long2ObjectOpenHashMap<>();

	// Subset is the input signature for the extraction process that was run.
	private void run(String subsetFilepath, String terminologyDirectoryPath) throws IOException, ReleaseImportException, ConversionException {
		AxiomRelationshipConversionService axiomRelationshipConversionService = new AxiomRelationshipConversionService(Collections.emptySet());
		Set<Long> subset = getSubset(subsetFilepath);

		Map<Long, List<AxiomRepresentation>> conceptAxioms = new Long2ObjectOpenHashMap<>();
		List<ConversionException> thrownExceptions = new ArrayList<>();
		new ReleaseImporter().loadSnapshotReleaseFiles(terminologyDirectoryPath, LoadingProfile.complete, new ImpotentComponentFactory() {
			@Override
			public void newReferenceSetMemberState(String filename, String[] fieldNames, String id, String effectiveTime, String active, String moduleId, String refsetId, String referencedComponentId, String... otherValues) {
				if ("1".equals(active) && refsetId.equals("733073007")) {
					String owlExpression = otherValues[0];
					try {
						AxiomRepresentation axiomRepresentation = axiomRelationshipConversionService.convertAxiomToRelationships(owlExpression);
						if (axiomRepresentation != null) {
							conceptAxioms.computeIfAbsent(Long.parseLong(referencedComponentId), i -> new ArrayList<>()).add(axiomRepresentation);
						}
					} catch (ConversionException e) {
						e.printStackTrace();
						thrownExceptions.add(e);
					}
				}
			}
		}, false);
		if (!thrownExceptions.isEmpty()) {
			throw thrownExceptions.get(0);
		}

		Set<Long> allConcepts = new LongOpenHashSet(conceptAxioms.keySet());

		// Test
		for (Map.Entry<Long, List<AxiomRepresentation>> entry : conceptAxioms.entrySet()) {
			Long conceptId = entry.getKey();
			for (AxiomRepresentation axiom : entry.getValue()) {
				Map<Integer, List<Relationship>> rightHandSideRelationships = axiom.getRightHandSideRelationships();
				if (rightHandSideRelationships != null) {
					for (List<Relationship> relationshipList : rightHandSideRelationships.values()) {
						for (Relationship relationship : relationshipList) {
							if (relationship.getTypeId() != 116680003 && relationship.getValue() == null) {// 116680003 |Is a (attribute)|
								checkConceptExists(relationship.getTypeId(), conceptId, allConcepts, subset);
								checkConceptExists(relationship.getDestinationId(), conceptId, allConcepts, subset);
							}
						}
					}
				}
			}
		}

		System.out.printf("Checked %s concepts.%n", allConcepts.size());
		System.out.println();
		System.out.printf("Found %s missing concepts in axioms of concepts within the subset.%n", inSubsetMissingRelatedConcept.size());
		printExamples(inSubsetMissingRelatedConcept);
		System.out.println();
		System.out.printf("Found %s missing concepts in axioms of supporting concepts.%n", outsideSubsetMissingRelatedConcept.size());
		printExamples(outsideSubsetMissingRelatedConcept);
		System.out.println();
	}

	private void printExamples(Map<Long, Set<Long>> inSubsetMissingRelatedConcept) {
		if (!inSubsetMissingRelatedConcept.isEmpty()) {
			System.out.println(" examples:");
			Iterator<Map.Entry<Long, Set<Long>>> iterator = inSubsetMissingRelatedConcept.entrySet().iterator();
			for (int i = 0; i < 5 && i < inSubsetMissingRelatedConcept.size(); i++) {
				Map.Entry<Long, Set<Long>> next = iterator.next();
				for (Long aLong : next.getValue()) {
					System.out.printf("  %s -> %s%n", aLong, next.getKey());
				}
			}
		}
	}

	private void checkConceptExists(long typeId, Long conceptId, Set<Long> allConcepts, Set<Long> subset) {
		if (!allConcepts.contains(typeId)) {
			if (subset.contains(conceptId)) {
				inSubsetMissingRelatedConcept.computeIfAbsent(typeId, i -> new HashSet<>()).add(conceptId);
			} else {
				outsideSubsetMissingRelatedConcept.computeIfAbsent(typeId, i -> new HashSet<>()).add(conceptId);
			}
		}
	}

	private Set<Long> getSubset(String subsetFilepath) throws IOException {
		File refsetFile = new File(subsetFilepath);
		List<String> strings = FileUtils.readLines(refsetFile, StandardCharsets.UTF_8);
		return new LongOpenHashSet(strings.stream().map(Long::parseLong).collect(Collectors.toList()));
	}

}
