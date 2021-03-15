package tools;

import Classification.OntologyReasoningService;
import ExceptionHandlers.ReasonerException;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;

import java.io.File;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class RemoveEquivalentClasses {

    public static void main(String[] args) throws OWLOntologyCreationException, ReasonerException {
        OWLOntology inputOnt = OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(new File("E:/Users/warren/Documents/aPostdoc/SCT-files/anatomy.owl"));

        OntologyReasoningService reasoningService = new OntologyReasoningService(inputOnt);
        reasoningService.classifyOntology();

        Map<OWLClass, Set<OWLClass>> equivalentClassesMap = reasoningService.getEquivalentClassesMap();

        Set<OWLClass> removedClasses = new HashSet<OWLClass>();
        for(Map.Entry<OWLClass, Set<OWLClass>> entry:equivalentClassesMap.entrySet()) {
            if(removedClasses.contains(entry.getKey())) {
                continue;
            }


        }

    }
}
