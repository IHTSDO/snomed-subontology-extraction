package org.snomed.ontology.extraction.services;

import org.ihtsdo.otf.snomedboot.factory.ImpotentComponentFactory;
import org.snomed.ontology.extraction.utils.SCTIDUtils;
import org.snomed.otf.owltoolkit.constants.Concepts;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

/**
 * Caches RF2 information gathered in a single pass to avoid multiple RF2 reads.
 * This class holds concept activity status and module mappings for efficient lookup.
 */
public class RF2InformationCache extends ImpotentComponentFactory {
    
    private final Map<Long, Boolean> conceptActiveStateMap = new HashMap<>();
    private final Map<Long, Long> conceptToModuleMap = new HashMap<>();
    private final Set<Long> allConceptIds = new HashSet<>();
    private final Map<Long, String> refsetCodeAndFilename = new HashMap<>();
	private final Map<Long, Set<Long>> parentChildRelationships = new HashMap<>();
	private final Map<Long, Set<Long>> conceptAssociations = new HashMap<>();
	private final Map<String, List<Integer>> refsetFilenameComponentIndices = new HashMap<>();

    /**
     * Loads all RF2 information in a single pass and caches it for later use.
     * 
     * @param rf2SnapshotArchive The RF2 snapshot archive file
     * @throws IOException If there's an error reading the RF2 file
     */
    public void loadRF2Information(File rf2SnapshotArchive) throws IOException {
        System.out.println("Loading RF2 information cache...");
        
        RF2ExtractionService extractionService = new RF2ExtractionService();
        try {
			extractionService.extractAllInfo(new FileInputStream(rf2SnapshotArchive), this);
        } catch (Exception e) {
            throw new IOException("Error loading RF2 information: " + e.getMessage(), e);
        }
        
        System.out.println("RF2 information cache loaded: " + allConceptIds.size() + " concepts");
    }
    
    /**
     * Checks if a concept is active.
     * 
     * @param conceptId The concept ID to check
     * @return true if the concept is active, false if inactive or not found
     */
    public boolean isConceptActive(Long conceptId) {
        return conceptActiveStateMap.getOrDefault(conceptId, false);
    }
    
    /**
     * Gets the module ID for a concept.
     * 
     * @param conceptId The concept ID
     * @return The module ID, or null if the concept is not found
     */
    public Long getModuleId(Long conceptId) {
        return conceptToModuleMap.get(conceptId);
    }
    
    /**
     * Checks if a concept exists in the RF2 archive.
     * 
     * @param conceptId The concept ID to check
     * @return true if the concept exists, false otherwise
     */
    public boolean conceptExists(Long conceptId) {
        return allConceptIds.contains(conceptId);
    }

	public Set<Long> getConceptDescendants(Long conceptId) {
		Set<Long> all = new HashSet<>();
		doGetConceptDescendants(conceptId, all);
		return all;
	}

	public void doGetConceptDescendants(Long conceptId, Set<Long> all) {
		Set<Long> children = parentChildRelationships.get(conceptId);
		if (children != null) {
			for (Long child : children) {
				all.add(child);
				doGetConceptDescendants(child, all);
			}
		}
	}

    /**
     * Gets all module IDs for a set of concepts.
     *
     * @param conceptIds The set of concept IDs
     * @return Set of module IDs
     */
    public Set<Long> getModuleIds(Set<Long> conceptIds) {
        Set<Long> moduleIds = new HashSet<>();
        for (Long conceptId : conceptIds) {
            Long moduleId = getModuleId(conceptId);
            if (moduleId != null) {
                moduleIds.add(moduleId);
            }
        }
        return moduleIds;
    }

	public boolean isRefset(Long concept) {
		return refsetCodeAndFilename.containsKey(concept);
	}

	public String getRefsetFilename(Long concept) {
		return refsetCodeAndFilename.get(concept);
	}

	public Set<Long> getConceptAssociations(Long concept) {
		return conceptAssociations.getOrDefault(concept, Collections.emptySet());
	}

	@Override
	public void newConceptState(String conceptId, String effectiveTime, String active, String moduleId, String definitionStatusId) {
		long conceptIdL = Long.parseLong(conceptId);
		allConceptIds.add(conceptIdL);
		conceptActiveStateMap.put(conceptIdL, "1".equals(active));
		conceptToModuleMap.put(conceptIdL, Long.parseLong(moduleId));
	}

	@Override
	public void newRelationshipState(String id, String effectiveTime, String active, String moduleId, String sourceId, String destinationId, String relationshipGroup, String typeId, String characteristicTypeId, String modifierId) {
		if ("1".equals(active) && Concepts.IS_A.equals(typeId) && Concepts.INFERRED_RELATIONSHIP.equals(characteristicTypeId)) {
			// Pass parent (destinationId) and child (sourceId).
			parentChildRelationships.computeIfAbsent(Long.parseLong(destinationId), a -> new HashSet<>()).add(Long.parseLong(sourceId));
		}
	}

	@Override
	public void newReferenceSetMemberState(String filename, String[] fieldNames, String id, String effectiveTime, String active, String moduleId, String refsetId, String referencedComponentId, String... otherValues) {
		refsetCodeAndFilename.put(Long.parseLong(refsetId), filename);

		if (SCTIDUtils.isConceptId(referencedComponentId)) {
			// Save associations between any two concepts using all "c" component type fields
			collectConceptAssociations(filename, referencedComponentId, otherValues);
		}
	}

	private void collectConceptAssociations(String filename, String referencedComponentId, String[] otherValues) {
		long referencedComponentIdL = Long.parseLong(referencedComponentId);

		// A list of integers representing all index positions for the character "c"
		List<Integer> componentPositions = refsetFilenameComponentIndices.computeIfAbsent(filename, theFilename -> {
			// der2_cissccRefset_MRCMAttributeDomainSnapshot_INT_20250801
			// der2_cRefset_AssociationSnapshot_INT_20250801
			List<Integer> cPositions = new ArrayList<>();
			String fields = filename.split("_")[1].replace("Refset", "");
			for (int i = 0; i < fields.length(); i++) {
				if (fields.charAt(i) == 'c') {
					cPositions.add(i);
				}
			}
			return cPositions;
		});

		for (Integer componentPosition : componentPositions) {
			String otherComponent = otherValues[componentPosition];
			if (SCTIDUtils.isConceptId(otherComponent)) {
				conceptAssociations.computeIfAbsent(referencedComponentIdL, i -> new HashSet<>()).add(Long.parseLong(otherComponent));
			}
		}
	}
}
