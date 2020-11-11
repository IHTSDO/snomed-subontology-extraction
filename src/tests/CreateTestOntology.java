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
        OWLOntologyManager man2 = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = man.getOWLDataFactory();

        //String IRIName = "http://snomed.info/id/";
        String IRIName = "http://example.com/id/#";
        String outputFile = "E:/Users/warren/Documents/aPostdoc/code/~test-code/abstract-definitions-test/anatomy-module/anatomy-without-disjointness.owl";

        //OWLOntology ont = man.createOntology();
        OWLOntology ont = man.loadOntologyFromOntologyDocument(IRI.create(new File("E:/Users/warren/Documents/aPostdoc/code/~test-code/abstract-definitions-test/anatomy-module/anatomy.owl")));
        OWLOntology ont2 = man2.loadOntologyFromOntologyDocument(IRI.create(new File("E:/Users/warren/Documents/aPostdoc/code/~test-code/abstract-definitions-test/anatomy-module/anatomy-without-disjointness.owl")));

        System.out.println("ont1 axioms count: " + ont.getAxiomCount());
        System.out.println("ont2 axioms count: " + ont2.getAxiomCount());

        //man.saveOntology(ont, new OWLXMLDocumentFormat(), IRI.create(new File(outputFile)));
    }
}
