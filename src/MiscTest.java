import Classification.OntologyReasoningService;
import ExceptionHandlers.ReasonerException;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
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
        OWLOntology inputOntology = man.createOntology();
        OWLDataFactory df = man.getOWLDataFactory();

        OWLClass A = df.getOWLClass(IRI.create("http://snomed.info/id/" + "A"));
        OWLClass B = df.getOWLClass(IRI.create("http://snomed.info/id/" + "D"));
        OWLClass C = df.getOWLClass(IRI.create("http://snomed.info/id/" + "C"));
        OWLClass D = df.getOWLClass(IRI.create("http://snomed.info/id/" + "P"));
        OWLClass E = df.getOWLClass(IRI.create("http://snomed.info/id/" + "P2"));
        OWLClass F = df.getOWLClass(IRI.create("http://snomed.info/id/" + "E"));

        OWLObjectProperty r = df.getOWLObjectProperty(IRI.create("http://snomed.info/id/" + "r"));

        OWLEquivalentClassesAxiom ax1 = df.getOWLEquivalentClassesAxiom(A, df.getOWLObjectIntersectionOf(B, df.getOWLObjectSomeValuesFrom(r, C)));
        OWLEquivalentClassesAxiom ax2 = df.getOWLEquivalentClassesAxiom(B, df.getOWLObjectIntersectionOf(D, df.getOWLObjectSomeValuesFrom(r, E)));
        OWLSubClassOfAxiom ax3 = df.getOWLSubClassOfAxiom(df.getOWLObjectSomeValuesFrom(r, C), df.getOWLObjectSomeValuesFrom(r, E));

        man.addAxioms(inputOntology, new HashSet<OWLAxiom>(Arrays.asList(ax1, ax2, ax3)));


        OntologyReasoningService reasoningService = new OntologyReasoningService(inputOntology);
        reasoningService.classifyOntology();

        OWLReasoner reasoner = reasoningService.getReasoner();
        reasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY);

        OWLClassExpression exp = df.getOWLObjectSomeValuesFrom(r, C);

        System.out.println(reasoner.getSuperClasses(exp, false));
        System.out.println(reasoner.getSubClasses(exp, false));
        System.out.println(reasoner.getSuperClasses(exp, true));
        System.out.println(reasoner.getSubClasses(exp, true));

    }
}