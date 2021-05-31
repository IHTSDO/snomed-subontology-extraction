import Classification.OntologyReasoningService;
import ExceptionHandlers.ReasonerException;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import tools.InputSignatureHandler;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class MiscTest {

    public static void main(String[] args) throws OWLOntologyCreationException, OWLOntologyStorageException, ReasonerException {
        String inputPath = "E:/Users/warren/Documents/aPostdoc/SCT-files/";
        File inputOntologyFile = new File(inputPath + "sct-jan-2021.owl");

        OWLOntologyManager man = OWLManager.createOWLOntologyManager();
        OWLOntology inputOntology = man.loadOntologyFromOntologyDocument(inputOntologyFile);
        OWLDataFactory df =man.getOWLDataFactory();

        File inputRefsetFile = new File("E:/Users/warren/Documents/aPostdoc/IAA-content-extraction/refsets/test/auth_form_bug_list.txt");

        Set<OWLClass> conceptsToDefine = InputSignatureHandler.readRefset(inputRefsetFile);

        OntologyReasoningService reasoningService = new OntologyReasoningService(inputOntology);
        reasoningService.classifyOntology();

        for(OWLClass cls:conceptsToDefine) {
            if(!reasoningService.getAncestors(cls).contains(df.getOWLClass(IRI.create("http://snomed.info/id/763158003")))) {
                System.out.println("Not subclass of medicinal product: " + cls.toString());
            }
        }



    }
}
