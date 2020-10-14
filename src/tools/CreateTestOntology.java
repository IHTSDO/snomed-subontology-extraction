package tools;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.OWLXMLDocumentFormat;
import org.semanticweb.owlapi.model.*;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;

public class CreateTestOntology {

    public static void main(String[] args) throws OWLOntologyCreationException, OWLOntologyStorageException {
        OWLOntologyManager man = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = man.getOWLDataFactory();

        String IRIName = "example.com/#";
        //String outputFile = "C:\\Users\\warre\\Documents\\aPostdoc\\code\\~test-code\\abstract-definitions-test\\pv_ancestors_redundancy_test.owl";
        String outputFile = "E:/Users/warren/Documents/aPostdoc/code/~test-code/abstract-definitions-test/reflexive-transitive-tests/test_reflexive_transitive.owl";

        OWLClass A = df.getOWLClass(IRI.create(IRIName + "A"));
        OWLClass B = df.getOWLClass(IRI.create(IRIName + "B"));
        OWLClass C = df.getOWLClass(IRI.create(IRIName + "C"));
        OWLClass D = df.getOWLClass(IRI.create(IRIName + "D"));
        OWLClass E = df.getOWLClass(IRI.create(IRIName + "E"));
        OWLClass F = df.getOWLClass(IRI.create(IRIName + "F"));
        OWLClass G = df.getOWLClass(IRI.create(IRIName + "G"));

        OWLObjectProperty r = df.getOWLObjectProperty(IRI.create(IRIName + "r"));
        OWLObjectProperty s = df.getOWLObjectProperty(IRI.create(IRIName + "s"));
        OWLObjectProperty t = df.getOWLObjectProperty(IRI.create(IRIName + "t"));
        OWLObjectProperty roleGroup = df.getOWLObjectProperty(IRI.create(IRIName + "RG"));

        OWLEquivalentClassesAxiom ax1 = df.getOWLEquivalentClassesAxiom(A, df.getOWLObjectIntersectionOf(B,
                                                                                                         df.getOWLObjectSomeValuesFrom(r, C)));
        OWLSubClassOfAxiom ax2 = df.getOWLSubClassOfAxiom(B, df.getOWLObjectIntersectionOf(F, df.getOWLObjectSomeValuesFrom(r, df.getOWLObjectIntersectionOf(D, E))));
        OWLReflexiveObjectPropertyAxiom ax3 = df.getOWLReflexiveObjectPropertyAxiom(r);
        OWLTransitiveObjectPropertyAxiom ax4 = df.getOWLTransitiveObjectPropertyAxiom(r);

        OWLOntology ont = man.createOntology();
        //OWLOntology ont = man.loadOntologyFromOntologyDocument(IRI.create(new File("E:/Users/warren/Documents/aPostdoc/code/~test-code/abstract-definitions-test/sct/sct-reflexivity-removed/sct-july-2020-noReflexivity.owl")));

        for(OWLObjectProperty prop:ont.getObjectPropertiesInSignature()) {
            for (OWLReflexiveObjectPropertyAxiom propAx : ont.getReflexiveObjectPropertyAxioms(prop)) {
                System.out.println("Reflexive prop: " + propAx.toString());
                man.removeAxiom(ont, propAx);
            }
        }
        man.addAxioms(ont, new HashSet<OWLAxiom>(Arrays.asList(ax1, ax2, ax3, ax4)));

        //OWLEquivalentClassesAxiom equiv = df.getOWLEquivalentClassesAxiom(A, df.getOWLObjectIntersectionOf(B, df.getOWLObjectSomeValuesFrom(r, C)));
        //System.out.println(equiv.getNestedClassExpressions());


        man.saveOntology(ont, new OWLXMLDocumentFormat(), IRI.create(new File(outputFile)));
    }
}
