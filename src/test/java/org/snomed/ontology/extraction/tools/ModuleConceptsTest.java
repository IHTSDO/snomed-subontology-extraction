package org.snomed.ontology.extraction.tools;

import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.model.OWLClass;
import org.snomed.ontology.extraction.services.RF2InformationCache;
import org.snomed.otf.owltoolkit.constants.Concepts;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ModuleConceptsTest {

    @Test
    void testModuleConceptCollection() throws IOException {
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

		RF2InformationCache rf2Cache = new RF2InformationCache();
		rf2Cache.newConceptState("131148009", "20250801", "1", "900000000000207008", Concepts.PRIMITIVE);
		rf2Cache.newConceptState("239161005", "20250801", "1", "900000000000207008", Concepts.PRIMITIVE);
		rf2Cache.newConceptState("16541001", "20250801", "1", "900000000000207008", Concepts.PRIMITIVE);

		Set<OWLClass> classes = InputSignatureHandler.readRefsetWithDescendantsAndTracking(
            subsetFile, rf2Cache, inactiveConcepts, missingConcepts);
        
        // Should have 3 concepts (no RF2 archive, so no module detection)
        assertEquals(3, classes.size());
        assertEquals(0, inactiveConcepts.size());
        assertEquals(0, missingConcepts.size());
    }
    
}
