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

        String IRIName = "http://snomed.info/id/";
        String outputFile = "E:/Users/warren/Documents/aPostdoc/code/~test-code/abstract-definitions-test/complex-nesting-test/complex_test1.owl";

        OWLOntology ont = man.createOntology();
        //OWLOntology ont = man.loadOntologyFromOntologyDocument(IRI.create(new File("E:/Users/warren/Documents/aPostdoc/code/~test-code/abstract-definitions-test/sct/sct-july-2020.owl")));

        OWLClass A = df.getOWLClass(IRI.create(IRIName + "A"));
        OWLClass B = df.getOWLClass(IRI.create(IRIName + "B"));
        OWLClass C = df.getOWLClass(IRI.create(IRIName + "B"));
        OWLObjectProperty r = df.getOWLObjectProperty(IRI.create(IRIName + "r"));
        OWLObjectProperty s = df.getOWLObjectProperty(IRI.create(IRIName + "s"));
        OWLObjectProperty roleGroup = df.getOWLObjectProperty(IRI.create(IRIName + "609096000"));

        OWLSubClassOfAxiom ax1 = df.getOWLSubClassOfAxiom(A, df.getOWLObjectIntersectionOf(B, df.getOWLObjectSomeValuesFrom(r, C)));
        OWLSubClassOfAxiom ax2 = df.getOWLSubClassOfAxiom(A, df.getOWLObjectIntersectionOf(B, df.getOWLObjectSomeValuesFrom(roleGroup, df.getOWLObjectSomeValuesFrom(r, C))));
        OWLSubClassOfAxiom ax3 = df.getOWLSubClassOfAxiom(A, df.getOWLObjectIntersectionOf(B, df.getOWLObjectSomeValuesFrom(r, df.getOWLObjectSomeValuesFrom(s, C))));

        man.addAxioms(ont, new HashSet<OWLAxiom>(Arrays.asList(ax1, ax2, ax3)));
        man.saveOntology(ont, new OWLXMLDocumentFormat(), IRI.create(new File(outputFile)));
    }
}
