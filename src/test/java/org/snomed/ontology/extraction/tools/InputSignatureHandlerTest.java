package org.snomed.ontology.extraction.tools;

import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.apibinding.OWLManager;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class InputSignatureHandlerTest {

    @Test
    public void testReadRefsetWithDescendants() throws IOException {
        // Create a temporary test file
        File tempFile = File.createTempFile("test_subset", ".txt");
        tempFile.deleteOnExit();
        
        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write("131148009\n");
            writer.write("<<239161005\n");
            writer.write("16541001\n");
            writer.write("<<123456789\n");
        }
        
        // Test parsing without RF2 archive (should just add root concepts)
        Set<OWLClass> classes = InputSignatureHandler.readRefset(tempFile);
        
        // Should have 4 concepts (readRefset doesn't handle << flag, so all are treated as direct concepts)
        assertEquals(4, classes.size());
        
        // Verify the concepts are present
        boolean has131148009 = false;
        boolean has239161005 = false;
        boolean has16541001 = false;
        boolean has123456789 = false;
        
        for (OWLClass cls : classes) {
            String iri = cls.getIRI().toString();
            if (iri.contains("131148009")) has131148009 = true;
            if (iri.contains("239161005")) has239161005 = true;
            if (iri.contains("16541001")) has16541001 = true;
            if (iri.contains("123456789")) has123456789 = true;
        }
        
        assertTrue(has131148009, "Should contain concept 131148009");
        assertTrue(has239161005, "Should contain concept 239161005");
        assertTrue(has16541001, "Should contain concept 16541001");
        assertTrue(has123456789, "Should contain concept 123456789");
    }
    
    @Test
    public void testReadRefsetWithWhitespace() throws IOException {
        // Create a temporary test file with whitespace around the flag
        File tempFile = File.createTempFile("test_subset_whitespace", ".txt");
        tempFile.deleteOnExit();
        
        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write("131148009\n");
            writer.write(" << 239161005\n");
            writer.write("16541001\n");
        }
        
        // Test parsing without RF2 archive
        Set<OWLClass> classes = InputSignatureHandler.readRefset(tempFile);
        
        // Should have 3 concepts (readRefset doesn't handle << flag, so all are treated as direct concepts)
        assertEquals(3, classes.size());
        
        // Verify the concepts are present
        boolean has131148009 = false;
        boolean has239161005 = false;
        boolean has16541001 = false;
        
        for (OWLClass cls : classes) {
            String iri = cls.getIRI().toString();
            if (iri.contains("131148009")) has131148009 = true;
            if (iri.contains("239161005")) has239161005 = true;
            if (iri.contains("16541001")) has16541001 = true;
        }
        
        assertTrue(has131148009, "Should contain concept 131148009");
        assertTrue(has239161005, "Should contain concept 239161005");
        assertTrue(has16541001, "Should contain concept 16541001");
    }
    
    @Test
    public void testReadRefsetWithTerms() throws IOException {
        // Create a temporary test file with concept terms
        File tempFile = File.createTempFile("test_subset_terms", ".txt");
        tempFile.deleteOnExit();
        
        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write("131148009 |Bleeding (finding)|\n");
            writer.write("<<239161005 |Wound hemorrhage (finding)|\n");
            writer.write("16541001 |Yellow fever (disorder)|\n");
        }
        
        // Test parsing without RF2 archive
        Set<OWLClass> classes = InputSignatureHandler.readRefset(tempFile);
        
        // Should have 3 concepts (readRefset doesn't handle << flag, so all are treated as direct concepts)
        assertEquals(3, classes.size());
        
        // Verify the concepts are present
        boolean has131148009 = false;
        boolean has239161005 = false;
        boolean has16541001 = false;
        
        for (OWLClass cls : classes) {
            String iri = cls.getIRI().toString();
            if (iri.contains("131148009")) has131148009 = true;
            if (iri.contains("239161005")) has239161005 = true;
            if (iri.contains("16541001")) has16541001 = true;
        }
        
        assertTrue(has131148009, "Should contain concept 131148009");
        assertTrue(has239161005, "Should contain concept 239161005");
        assertTrue(has16541001, "Should contain concept 16541001");
    }
}
