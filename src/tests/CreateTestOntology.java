package tests;

import ExceptionHandlers.ReasonerException;
import org.semanticweb.elk.reasoner.Reasoner;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.OWLXMLDocumentFormat;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

public class CreateTestOntology {

    public static void main(String[] args) throws OWLOntologyCreationException, OWLOntologyStorageException, ReasonerException, ClassNotFoundException, IllegalAccessException, InstantiationException {
        OWLOntologyManager man = OWLManager.createOWLOntologyManager();
        OWLOntologyManager man2 = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = man.getOWLDataFactory();

        //String IRIName = "http://snomed.info/id/";
        String IRIName = "http://example.com/example#";
        //String outputFile = "E:/Users/warren/Documents/aPostdoc/code/~test-code/abstract-definitions-test/anatomy-module/anatomy-without-disjointness.owl";

        //OWLOntology ont = man.createOntology();
        //OWLOntology ont = man.loadOntologyFromOntologyDocument(IRI.create(new File("E:/Users/warren/Documents/aPostdoc/code/~test-code/abstract-definitions-test/anatomy-module/anatomy.owl")));
       // OWLOntology ont2 = man2.loadOntologyFromOntologyDocument(IRI.create(new File("E:/Users/warren/Documents/aPostdoc/code/~test-code/abstract-definitions-test/anatomy-module/anatomy-without-disjointness.owl")));

        //System.out.println("ont1 axioms count: " + ont.getAxiomCount());
        //System.out.println("ont2 axioms count: " + ont2.getAxiomCount());
        OWLClass A = df.getOWLClass(IRI.create(IRIName + "A"));
        OWLClass A0 = df.getOWLClass(IRI.create(IRIName + "A0"));
        OWLClass A1 = df.getOWLClass(IRI.create(IRIName + "A1"));
        OWLClass A11 = df.getOWLClass(IRI.create(IRIName + "A11"));
        OWLClass A12 = df.getOWLClass(IRI.create(IRIName + "A12"));
        OWLClass C = df.getOWLClass(IRI.create(IRIName + "C"));
        OWLClass C1 = df.getOWLClass(IRI.create(IRIName + "C1"));
        OWLClass D = df.getOWLClass(IRI.create(IRIName + "D"));
        OWLClass D1 = df.getOWLClass(IRI.create(IRIName + "D1"));
        OWLObjectProperty r = df.getOWLObjectProperty(IRI.create(IRIName + "r"));
        OWLObjectProperty s = df.getOWLObjectProperty(IRI.create(IRIName + "s"));

        OWLEquivalentClassesAxiom ax1 = df.getOWLEquivalentClassesAxiom(A, df.getOWLObjectIntersectionOf(A0, df.getOWLObjectSomeValuesFrom(r, C)));
        OWLSubClassOfAxiom ax2 = df.getOWLSubClassOfAxiom(A1, df.getOWLObjectIntersectionOf(A, df.getOWLObjectSomeValuesFrom(r, C)));
        OWLSubClassOfAxiom ax3 = df.getOWLSubClassOfAxiom(df.getOWLObjectIntersectionOf(A0, df.getOWLObjectSomeValuesFrom(r, D)), A1);
        OWLSubClassOfAxiom ax4 = df.getOWLSubClassOfAxiom(A11, df.getOWLObjectIntersectionOf(A0, df.getOWLObjectSomeValuesFrom(r, D1)));
        OWLSubClassOfAxiom ax5 = df.getOWLSubClassOfAxiom(D1, D);
        OWLSubClassOfAxiom ax6 = df.getOWLSubClassOfAxiom(A12, df.getOWLObjectIntersectionOf(A1, df.getOWLObjectSomeValuesFrom(r, C1)));

        OWLOntology ont = man.createOntology();
        man.addAxioms(ont, new HashSet<OWLAxiom>(Arrays.asList(ax1,ax2,ax3,ax4,ax5,ax6)));

        String outputPath = "E:/Users/warren/Documents/aPostdoc/code/~test-code/examples/authoring_GCI";
        man.saveOntology(ont, new OWLXMLDocumentFormat(), IRI.create(new File(outputPath + ".owl")));

    }
}
