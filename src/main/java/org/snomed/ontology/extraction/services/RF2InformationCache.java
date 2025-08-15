package org.snomed.ontology.extraction.services;

import org.ihtsdo.otf.snomedboot.factory.ImpotentComponentFactory;
import org.snomed.otf.owltoolkit.constants.Concepts;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Caches RF2 information gathered in a single pass to avoid multiple RF2 reads.
 * This class holds concept activity status and module mappings for efficient lookup.
 */
public class RF2InformationCache extends ImpotentComponentFactory {
    
    private final Map<Long, Boolean> conceptActiveStateMap = new HashMap<>();
    private final Map<Long, Long> conceptToModuleMap = new HashMap<>();
    private final Set<Long> allConceptIds = new HashSet<>();
//    private final Map<Long, Path> refsetNameAndPath = new HashMap<>();
	private final Map<Long, Set<Long>> parentChildRelationships = new HashMap<>();

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

//	@Override
//	public void newReferenceSetMemberState(Path rf2FilePath, String[] fieldNames, String id, String effectiveTime, String active, String moduleId, String refsetId, String referencedComponentId, String... otherValues) {
//		refsetNameAndPath.put(Long.parseLong(refsetId), rf2FilePath);
//	}

}
