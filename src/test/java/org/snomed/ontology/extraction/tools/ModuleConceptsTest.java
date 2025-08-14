package org.snomed.ontology.extraction.tools;

import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.model.OWLClass;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class ModuleConceptsTest {

    @Test
    public void testModuleConceptCollection() throws IOException {
        // Create a test subset file with concepts that have different module IDs
        File subsetFile = File.createTempFile("test_module_subset", ".txt");
        subsetFile.deleteOnExit();
        
        try (FileWriter writer = new FileWriter(subsetFile)) {
            writer.write("131148009 |Bleeding (finding)|\n");
            writer.write("<<239161005 |Wound hemorrhage (finding)|\n");
            writer.write("16541001 |Yellow fever (disorder)|\n");
        }
        
        // Test parsing with tracking (without RF2 archive)
        Set<Long> inactiveConcepts = new HashSet<>();
        Set<Long> missingConcepts = new HashSet<>();
        
        Set<OWLClass> classes = InputSignatureHandler.readRefsetWithDescendantsAndTracking(
            subsetFile, null, inactiveConcepts, missingConcepts);
        
        // Should have 3 concepts (no RF2 archive, so no module detection)
        assertEquals(3, classes.size());
        assertEquals(0, inactiveConcepts.size());
        assertEquals(0, missingConcepts.size());
    }
    
    @Test
    public void testConceptInfoWithModuleId() {
        // Test the ConceptInfo class with module ID
        InputSignatureHandler.ConceptInfo concept = new InputSignatureHandler.ConceptInfo(
            12345L, "1", "900000000000207008");
        
        assertEquals(12345L, concept.conceptId);
        assertEquals("1", concept.active);
        assertEquals("900000000000207008", concept.moduleId);
        
        // Test with different module ID
        InputSignatureHandler.ConceptInfo concept2 = new InputSignatureHandler.ConceptInfo(
            67890L, "0", "900000000000441003");
        
        assertEquals(67890L, concept2.conceptId);
        assertEquals("0", concept2.active);
        assertEquals("900000000000441003", concept2.moduleId);
    }
}
