package org.snomed.ontology.extraction.manualtests;

import org.snomed.ontology.extraction.tools.InputSignatureHandler;
import org.semanticweb.owlapi.model.OWLClass;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Manual test to demonstrate the new subset parsing functionality with descendant flags.
 * 
 * This test shows how to use the "<<" flag in subset files to include all descendants
 * of a concept when extracting a subontology.
 * 
 * Usage:
 * 1. Create a subset file with concept IDs and optional "<<" flags
 * 2. Run the subontology extraction with the -rf2-snapshot-archive parameter
 * 3. The tool will automatically include all descendants of flagged concepts
 * 
 * Example subset file format:
 * 131148009
 * <<239161005
 * 16541001
 */
public class SubsetWithDescendantsTest {

    public static void main(String[] args) {
        try {
            // Create a test subset file
            File subsetFile = createTestSubsetFile();
            
            System.out.println("Created test subset file: " + subsetFile.getAbsolutePath());
            System.out.println("File contents:");
            System.out.println("131148009");
            System.out.println("<<239161005");
            System.out.println("16541001");
            System.out.println();
            
            // Test parsing without RF2 archive (will just add root concepts)
            System.out.println("Testing parsing without RF2 archive:");
            Set<OWLClass> classesWithoutRF2 = InputSignatureHandler.readRefset(subsetFile);
            System.out.println("Found " + classesWithoutRF2.size() + " concepts");
            for (OWLClass cls : classesWithoutRF2) {
                System.out.println("  " + cls.getIRI().toString());
            }
            System.out.println();
            
            // Test parsing with RF2 archive (would include descendants)
            System.out.println("Testing parsing with RF2 archive (if available):");
            System.out.println("Note: This would require a valid RF2 snapshot archive file");
            System.out.println("The tool would automatically find all descendants of concepts marked with '<<'");
            System.out.println();
            
            System.out.println("To use this functionality:");
            System.out.println("1. Create a subset file with the format shown above");
            System.out.println("2. Run the subontology extraction tool with:");
            System.out.println("   -input-subset <subset-file>");
            System.out.println("   -rf2-snapshot-archive <rf2-archive>");
            System.out.println("   -output-rf2");
            System.out.println("3. The tool will automatically include all descendants");
            
            // Clean up
            subsetFile.delete();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static File createTestSubsetFile() throws IOException {
        File tempFile = File.createTempFile("test_subset_with_descendants", ".txt");
        tempFile.deleteOnExit();
        
        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write("131148009\n");
            writer.write("<<239161005\n");
            writer.write("16541001\n");
        }
        
        return tempFile;
    }
}
