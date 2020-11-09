package ResultsWriters;

import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLOntology;
import org.snomed.otf.owltoolkit.service.RF2ExtractionService;
import org.snomed.otf.owltoolkit.util.InputStreamSet;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class SnowstormConversion {

    public static Set<Long> extractAllEntityIDsForOntology(OWLOntology ont) {
        Set<OWLEntity> entitiesInOnt = new HashSet<OWLEntity>();
        entitiesInOnt.addAll(ont.getClassesInSignature());
        entitiesInOnt.addAll(ont.getObjectPropertiesInSignature()); //TODO: only need classes and properties?

        Set<Long> entityIDs = new HashSet<Long>();
        for(OWLEntity ent:entitiesInOnt) {
            String entIDString = ent.toStringID().replace("http://snomed.info/id/","");
            Long entID = Long.valueOf(entIDString);

            //System.out.println(entID);
            entityIDs.add(entID);
        }
        return entityIDs;
    }

    public static void runRF2Extraction(Set<Long> entityIDs, String outputPath) throws IOException, ReleaseImportException {
        File outputDirectory = new File(outputPath + "snowstorm-rf2-extracts/");
        //FileUtils.deleteDirectory(outputDirectory);
        new RF2ExtractionService().extractConcepts(
                new InputStreamSet(new File("E:/Users/warren/Documents/aPostdoc/code/~test-code/SCT-files/SnomedCT_InternationalRF2_PRODUCTION_20200731T120000Z.zip")),
                entityIDs, outputDirectory);
    }
}
