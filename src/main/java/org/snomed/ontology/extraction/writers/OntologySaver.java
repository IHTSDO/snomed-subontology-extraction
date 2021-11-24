package org.snomed.ontology.extraction.writers;

import org.semanticweb.owlapi.formats.FunctionalSyntaxDocumentFormat;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;

import java.io.File;

public class OntologySaver {
    public static void saveOntology(OWLOntology ont, File outputDirectory, String outputFilename) throws OWLOntologyStorageException {
        if(!outputFilename.endsWith(".owl")) {
            outputFilename += ".owl";
        }
        OWLOntologyManager man = ont.getOWLOntologyManager();
        FunctionalSyntaxDocumentFormat owlDocumentFormat = new FunctionalSyntaxDocumentFormat();
        ont.getOWLOntologyManager().setOntologyFormat(ont, owlDocumentFormat);
        man.saveOntology(ont, new FunctionalSyntaxDocumentFormat(), IRI.create(new File(outputDirectory, outputFilename)));
    }
}
