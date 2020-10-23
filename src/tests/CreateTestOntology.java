package tests;

import Classification.OntologyReasoningService;
import ExceptionHandlers.ReasonerException;
import org.semanticweb.elk.reasoner.Reasoner;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.OWLXMLDocumentFormat;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.*;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;

public class CreateTestOntology {

    public static void main(String[] args) throws OWLOntologyCreationException, OWLOntologyStorageException, ReasonerException, ClassNotFoundException, IllegalAccessException, InstantiationException {
        OWLOntologyManager man = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = man.getOWLDataFactory();

        String IRIName = "example.com/#";
        String outputFile = "E:/Users/warren/Documents/aPostdoc/code/~test-code/abstract-definitions-test/sct/sct-july-2020-noTransitivity.owl";

        OWLClass A = df.getOWLClass(IRI.create(IRIName + "A"));
        OWLClass B = df.getOWLClass(IRI.create(IRIName + "B"));
        OWLClass C = df.getOWLClass(IRI.create(IRIName + "C"));


        OWLObjectProperty r = df.getOWLObjectProperty(IRI.create(IRIName + "r"));
        OWLObjectProperty s = df.getOWLObjectProperty(IRI.create(IRIName + "s"));
        OWLObjectProperty t = df.getOWLObjectProperty(IRI.create(IRIName + "t"));
        OWLObjectProperty roleGroup = df.getOWLObjectProperty(IRI.create(IRIName + "RG"));

        OWLSubClassOfAxiom ax1 = df.getOWLSubClassOfAxiom(A, df.getOWLObjectSomeValuesFrom(r, B));

        //OWLDisjointClassesAxiom ax2 = df.getOWLDisjointClassesAxiom(B, df.getOWLObjectSomeValuesFrom(r, A));
        OWLReflexiveObjectPropertyAxiom ax2 = df.getOWLReflexiveObjectPropertyAxiom(r);
        OWLTransitiveObjectPropertyAxiom ax3 = df.getOWLTransitiveObjectPropertyAxiom(r);

        OWLNamedIndividual a = df.getOWLNamedIndividual(IRI.create(IRIName + "a"));

        //OWLClassAssertionAxiom as1 = df.getOWLClassAssertionAxiom(df.getOWLObjectIntersectionOf(A, df.getOWLObjectComplementOf(df.getOWLObjectSomeValuesFrom(r, A))), a);
        OWLOntology ont = man.loadOntologyFromOntologyDocument(IRI.create(new File("E:/Users/warren/Documents/aPostdoc/code/~test-code/abstract-definitions-test/sct/sct-july-2020-noTransitivity.owl")));

        for(OWLAxiom ax:ont.getAxioms()) {
            if(ax instanceof OWLTransitiveObjectPropertyAxiom) {
                System.out.println("Removing transitive axiom: " + ax);
                man.removeAxiom(ont, ax);
            }
        }

       // OntologyReasoningService service = new OntologyReasoningService(ont);
        //System.out.println("Consistent? " + service.isConsistent());

       // OWLClassAssertionAxiom ax = df.getOWLClassAssertionAxiom(df.getOWLObjectIntersectionOf(df.getOWLObjectSomeValuesFrom(r, A),
          //                                                                                     df.getOWLObjectAllValuesFrom(r, df.getOWLObjectComplementOf(B))), a);


        man.saveOntology(ont, new OWLXMLDocumentFormat(), IRI.create(new File(outputFile)));
    }
}
