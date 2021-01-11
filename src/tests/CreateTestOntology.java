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
        OWLClass B = df.getOWLClass(IRI.create(IRIName + "B"));
        OWLClass C = df.getOWLClass(IRI.create(IRIName + "C"));
        OWLClass E = df.getOWLClass(IRI.create(IRIName + "E"));
        OWLClass F = df.getOWLClass(IRI.create(IRIName + "F"));
        OWLClass G = df.getOWLClass(IRI.create(IRIName + "G"));
        OWLClass H = df.getOWLClass(IRI.create(IRIName + "H"));
        OWLClass I = df.getOWLClass(IRI.create(IRIName + "I"));
        OWLObjectProperty r = df.getOWLObjectProperty(IRI.create(IRIName + "r"));
        OWLObjectProperty s = df.getOWLObjectProperty(IRI.create(IRIName + "s"));

        OWLNamedIndividual a = df.getOWLNamedIndividual(IRI.create(IRIName + "a"));

        OWLSubClassOfAxiom ax1 = df.getOWLSubClassOfAxiom(A, C);
        OWLSubClassOfAxiom ax2 = df.getOWLSubClassOfAxiom(df.getOWLObjectSomeValuesFrom(r, B), C);
        OWLSubClassOfAxiom ax3 = df.getOWLSubClassOfAxiom(F, G);
        OWLDisjointClassesAxiom ax4 = df.getOWLDisjointClassesAxiom(A, H);
        OWLSubClassOfAxiom ax5 = df.getOWLSubClassOfAxiom(C, I);
        OWLClassAssertionAxiom as1 = df.getOWLClassAssertionAxiom(df.getOWLObjectAllValuesFrom(r, df.getOWLObjectComplementOf(B)), a);
        OWLClassAssertionAxiom as2 = df.getOWLClassAssertionAxiom(H, a);

        OWLSubClassOfAxiom obs1 = df.getOWLSubClassOfAxiom(df.getOWLObjectSomeValuesFrom(s, F), df.getOWLObjectSomeValuesFrom(s, E));
        OWLClassAssertionAxiom obs2 = df.getOWLClassAssertionAxiom(C, a);

        OWLOntology backOnt = man.createOntology();
        OWLOntology obsOnt = man.createOntology();
        man.addAxioms(backOnt, new HashSet<OWLAxiom>(Arrays.asList(ax1,ax2,ax3,ax4,ax5,as2)));
        man.addAxioms(obsOnt, new HashSet<OWLAxiom>(Arrays.asList(obs1,obs2)));

        String outputPath = "E:/Users/warren/Documents/aPhD/THESIS-EXAMPLES/kb-illustrative";
        man.saveOntology(backOnt, new OWLXMLDocumentFormat(), IRI.create(new File(outputPath + "_background.owl")));
        man.saveOntology(obsOnt, new OWLXMLDocumentFormat(), IRI.create(new File(outputPath + "_obs.owl")));

    }
}
