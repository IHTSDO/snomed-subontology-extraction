import Classification.OntologyReasoningService;
import ExceptionHandlers.ReasonerException;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;

public class MiscTest {

    public static void main(String[] args) throws OWLOntologyCreationException, OWLOntologyStorageException, ReasonerException {
        String inputPath = "E:/Users/warren/Documents/aPostdoc/SCT-files/";
        File inputOntologyFile = new File(inputPath + "sct-jan-2021.owl");

        OWLOntologyManager man = OWLManager.createOWLOntologyManager();
        OWLOntology inputOntology = man.loadOntologyFromOntologyDocument(inputOntologyFile);

        System.out.println("Axioms: " + inputOntology.getLogicalAxioms().size());
        System.out.println("Concepts: " + inputOntology.getClassesInSignature().size());
    }
}
