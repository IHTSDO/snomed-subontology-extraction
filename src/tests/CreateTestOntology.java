package tests;

import ExceptionHandlers.ReasonerException;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.OWLXMLDocumentFormat;
import org.semanticweb.owlapi.model.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

public class CreateTestOntology {

    public static void main(String[] args) throws OWLOntologyCreationException, OWLOntologyStorageException, ReasonerException, ClassNotFoundException, IllegalAccessException, InstantiationException {
        OWLOntologyManager man = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = man.getOWLDataFactory();

        //String IRIName = "http://snomed.info/id/";
        String IRIName = "http://example.com/id/#";
        String outputFile = "E:/Users/warren/Documents/aPostdoc/code/~test-code/abstract-definitions-test/propertyChainTest/test1.owl";

        OWLOntology ont = man.createOntology();
        //OWLOntology ont = man.loadOntologyFromOntologyDocument(IRI.create(new File("E:/Users/warren/Documents/aPostdoc/code/~test-code/abstract-definitions-test/sct/sct-july-2020.owl")));

        OWLClass P3 = df.getOWLClass(IRI.create(IRIName + "P3"));
        OWLClass P4 = df.getOWLClass(IRI.create(IRIName + "P4"));
        OWLClass P5 = df.getOWLClass(IRI.create(IRIName + "P5"));
        OWLClass T1 = df.getOWLClass(IRI.create(IRIName + "T1"));
        OWLClass T2 = df.getOWLClass(IRI.create(IRIName + "T2"));
        OWLObjectProperty r = df.getOWLObjectProperty(IRI.create(IRIName + "r"));
        OWLObjectProperty s = df.getOWLObjectProperty(IRI.create(IRIName + "s"));
        OWLObjectProperty roleGroup = df.getOWLObjectProperty(IRI.create(IRIName + "RG"));

        OWLEquivalentClassesAxiom ax1 = df.getOWLEquivalentClassesAxiom(P4,
                df.getOWLObjectIntersectionOf(P3, df.getOWLObjectSomeValuesFrom(roleGroup, df.getOWLObjectSomeValuesFrom(r, T2))));
        OWLEquivalentClassesAxiom ax2 = df.getOWLEquivalentClassesAxiom(P3,
                df.getOWLObjectIntersectionOf(P5, df.getOWLObjectSomeValuesFrom(roleGroup, df.getOWLObjectSomeValuesFrom(r, T1))));
        OWLSubClassOfAxiom ax3 = df.getOWLSubClassOfAxiom(T2, df.getOWLObjectSomeValuesFrom(s, T1));
        OWLSubPropertyChainOfAxiom ax4 = df.getOWLSubPropertyChainOfAxiom(Arrays.asList(r, s), r);

        man.addAxioms(ont, new HashSet<OWLAxiom>(Arrays.asList(ax1, ax2, ax3, ax4)));
        man.saveOntology(ont, new OWLXMLDocumentFormat(), IRI.create(new File(outputFile)));
    }
}
