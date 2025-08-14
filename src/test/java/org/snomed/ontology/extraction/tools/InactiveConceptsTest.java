package org.snomed.ontology.extraction.tools;

import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.model.OWLClass;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class InactiveConceptsTest {

    @Test
    public void testInactiveConceptDetection() throws IOException {
        // Create a test subset file with some concepts
        File subsetFile = File.createTempFile("test_inactive_subset", ".txt");
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
        
        // Should have 3 concepts (no RF2 archive, so no inactive/missing detection)
        assertEquals(3, classes.size());
        assertEquals(0, inactiveConcepts.size());
        assertEquals(0, missingConcepts.size());
    }
    
    @Test
    public void testConceptInfoClass() {
        // Test the ConceptInfo class
        InputSignatureHandler.ConceptInfo activeConcept = new InputSignatureHandler.ConceptInfo(12345L, "1");
        InputSignatureHandler.ConceptInfo inactiveConcept = new InputSignatureHandler.ConceptInfo(67890L, "0");
        
        assertEquals(12345L, activeConcept.conceptId);
        assertEquals("1", activeConcept.active);
        assertEquals(67890L, inactiveConcept.conceptId);
        assertEquals("0", inactiveConcept.active);
    }
}
