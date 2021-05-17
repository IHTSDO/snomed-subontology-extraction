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

        OWLOntology ont = man.createOntology();
        OWLDataFactory df = man.getOWLDataFactory();
        OWLClass A = df.getOWLClass(IRI.create("http://snomed.info/id/" + "A"));
        OWLClass A0 = df.getOWLClass(IRI.create("http://snomed.info/id/" + "A0"));
        OWLClass A1 = df.getOWLClass(IRI.create("http://snomed.info/id/" + "A1"));
        OWLClass A11 = df.getOWLClass(IRI.create("http://snomed.info/id/" + "A11"));
        OWLClass A12 = df.getOWLClass(IRI.create("http://snomed.info/id/" + "A12"));
        OWLClass C = df.getOWLClass(IRI.create("http://snomed.info/id/" + "C"));
        OWLClass D = df.getOWLClass(IRI.create("http://snomed.info/id/" + "D"));
        OWLClass C1 = df.getOWLClass(IRI.create("http://snomed.info/id/" + "C1"));
        OWLClass D1 = df.getOWLClass(IRI.create("http://snomed.info/id/" + "D1"));
        OWLObjectProperty r = df.getOWLObjectProperty(IRI.create("http://snomed.info/id/" + "r"));

        OWLSubClassOfAxiom ax1 = df.getOWLSubClassOfAxiom(C1, C);
        OWLSubClassOfAxiom ax2 = df.getOWLSubClassOfAxiom(D1, D);
        OWLEquivalentClassesAxiom ax3 = df.getOWLEquivalentClassesAxiom(A, df.getOWLObjectIntersectionOf(A0, df.getOWLObjectSomeValuesFrom(r, C)));
        OWLSubClassOfAxiom ax4 = df.getOWLSubClassOfAxiom(A1, df.getOWLObjectIntersectionOf(A, df.getOWLObjectSomeValuesFrom(r, C)));
        OWLSubClassOfAxiom ax5 = df.getOWLSubClassOfAxiom(df.getOWLObjectIntersectionOf(A0, df.getOWLObjectSomeValuesFrom(r, D)), A1);
        OWLEquivalentClassesAxiom ax6 = df.getOWLEquivalentClassesAxiom(A11, df.getOWLObjectIntersectionOf(A0, df.getOWLObjectSomeValuesFrom(r, D1)));
        OWLEquivalentClassesAxiom ax7 = df.getOWLEquivalentClassesAxiom(A12, df.getOWLObjectIntersectionOf(A1, df.getOWLObjectSomeValuesFrom(r, C1)));

        man.addAxioms(ont, new HashSet<OWLAxiom>(Arrays.asList(ax1, ax2, ax3, ax4, ax5, ax6, ax7)));
        man.saveOntology(ont, IRI.create(new File("E:/Users/warren/Documents/aPostdoc/SCT-files/examples/yong-gci-example.owl")));
    }
}
