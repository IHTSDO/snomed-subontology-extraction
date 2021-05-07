import ExceptionHandlers.ReasonerException;
import ResultsWriters.OntologySaver;
import SubOntologyExtraction.SubOntologyExtractionHandler;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.snomed.otf.owltoolkit.conversion.ConversionException;
import tools.InputSignatureHandler;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class RunExperiment {

    public static void main(String[] args) throws OWLException, ReasonerException, IOException, ReleaseImportException, ConversionException {
        //test run
        String inputPath = "E:/Users/warren/Documents/aPostdoc/SCT-files/";
        File inputOntologyFile = new File(inputPath + "sct-july-2018.owl");

        OWLOntologyManager man = OWLManager.createOWLOntologyManager();
        OWLOntology inputOntology = man.loadOntologyFromOntologyDocument(inputOntologyFile);

        int numExperiments = 10;
        int numClasses = 50;

        List<Integer> sizeResults = new ArrayList<Integer>();
        for(int i=0; i<numExperiments; i++) {
            System.out.println("Experiment " + i);

            List<OWLClass> conceptsInSignature = new ArrayList<OWLClass>(inputOntology.getClassesInSignature());
            Random rand = new Random();
            Collections.shuffle(conceptsInSignature, rand);
            Set<OWLClass> conceptsToDefine = new HashSet<OWLClass>();
            while(conceptsToDefine.size() < numClasses) {
                conceptsToDefine.add(conceptsInSignature.get(conceptsToDefine.size()));
            }

            sizeResults.add(executeSingleExperiment(inputOntology, conceptsToDefine));
            System.out.println("Done. Size: " + sizeResults.get(i));
        }

        int tot = 0;
        for(int size:sizeResults) {
            tot = tot+size;
        }
        Collections.sort(sizeResults);
        int mid = (int) Math.round(sizeResults.size() * 0.5);

        System.out.println("Sizes: " + sizeResults);
        System.out.println("Mean size: " + tot/sizeResults.size());
        System.out.println("Median size: " + sizeResults.get(mid));
        System.out.println("Min: " + sizeResults.get(0));
        System.out.println("Max: " + sizeResults.get(sizeResults.size()-1));
    }

    private static int executeSingleExperiment(OWLOntology inputOntology, Set<OWLClass> conceptsToDefine) throws OWLException, ReasonerException {

        SubOntologyExtractionHandler generator = new SubOntologyExtractionHandler(inputOntology, conceptsToDefine);
        generator.computeSubontology();

        OWLOntology subOntology = generator.getCurrentSubOntology();
        return subOntology.getLogicalAxiomCount();
    }
}
