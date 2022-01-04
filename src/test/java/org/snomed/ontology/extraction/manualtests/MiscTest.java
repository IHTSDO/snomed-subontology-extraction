package org.snomed.ontology.extraction.manualtests;

import org.snomed.ontology.extraction.exception.ReasonerException;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

public class MiscTest {

	public static void main(String[] args) throws OWLOntologyCreationException, OWLOntologyStorageException, ReasonerException {
		String inputPath = "E:/Users/warren/Documents/aPostdoc/subontologies/era/";
		File inputOntologyFile1 = new File(inputPath + "era_edta_preMultiAxiom.owl");
		File inputOntologyFile2 = new File(inputPath + "subOntology.owl");

		OWLOntologyManager man = OWLManager.createOWLOntologyManager();
		OWLOntologyManager man2 = OWLManager.createOWLOntologyManager();

		OWLOntology inputOntology1 = man.loadOntologyFromOntologyDocument(inputOntologyFile1);
		OWLOntology inputOntology2 = man2.loadOntologyFromOntologyDocument(inputOntologyFile2);

		Set<OWLClass> classes1 = inputOntology1.getClassesInSignature();
		Set<OWLClass> classes2 = inputOntology2.getClassesInSignature();

		Set<OWLClass> diffIn1 = new HashSet<OWLClass>(classes1);
		diffIn1.removeAll(classes2);
		Set<OWLClass> diffIn2 = new HashSet<OWLClass>(classes2);
		diffIn2.removeAll(classes1);
		System.out.println("In 1, not 2: " + diffIn1);
		System.out.println("In 2, not 1: " + diffIn2);

		OWLDataFactory df = man.getOWLDataFactory();


	}
}