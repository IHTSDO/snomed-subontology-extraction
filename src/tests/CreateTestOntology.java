package tests;

import ExceptionHandlers.ReasonerException;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.OWLXMLDocumentFormat;
import org.semanticweb.owlapi.model.*;

import java.io.File;

public class CreateTestOntology {

    public static void main(String[] args) throws OWLOntologyCreationException, OWLOntologyStorageException, ReasonerException, ClassNotFoundException, IllegalAccessException, InstantiationException {
        OWLOntologyManager man = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = man.getOWLDataFactory();

        String IRIName = "example.com/#";
        String outputFile = "E:/Users/warren/Documents/aPostdoc/code/~test-code/abstract-definitions-test/sct/sct-july-2020-bilirubinDiglucuronideEdit.owl";

        OWLOntology ont = man.loadOntologyFromOntologyDocument(IRI.create(new File("E:/Users/warren/Documents/aPostdoc/code/~test-code/abstract-definitions-test/sct/sct-july-2020.owl")));

        OWLObjectProperty isModificationof = df.getOWLObjectProperty(IRI.create("http://snomed.info/id/738774007"));
        OWLClass Flumazenil18F = df.getOWLClass(IRI.create("http://snomed.info/id/423577002"));
        OWLClass Flumazenil = df.getOWLClass(IRI.create("http://snomed.info/id/387575000"));
        OWLClass Fluorine_Radioisotope = df.getOWLClass(IRI.create("http://snomed.info/id/25205007"));
        OWLClass Radioisotope_labeled_flumazenil = df.getOWLClass(IRI.create("http://snomed.info/id/771270004"));
        OWLClass Benzodiazepine_receptor_antagonist = df.getOWLClass(IRI.create("http://snomed.info/id/734630008"));
        OWLClass Fluorine18 = df.getOWLClass(IRI.create("http://snomed.info/id/77004003"));
        OWLClass Radioactive_isotope = df.getOWLClass(IRI.create("http://snomed.info/id/89457008"));
        OWLObjectProperty hasDisposition = df.getOWLObjectProperty(IRI.create("http://snomed.info/id/726542003"));

        OWLClass bilirubin_diglucuronide = df.getOWLClass(IRI.create("http://snomed.info/id/42231008"));
        OWLClass direct_reacting_bilirubin = df.getOWLClass(IRI.create("http://snomed.info/id/54462003"));
        OWLClass porphyrin_and = df.getOWLClass(IRI.create("http://snomed.info/id/259495009"));
        OWLClass bilirubin = df.getOWLClass(IRI.create("http://snomed.info/id/79706000"));


        OWLClassExpression supClass = df.getOWLObjectIntersectionOf(direct_reacting_bilirubin, porphyrin_and, df.getOWLObjectSomeValuesFrom(isModificationof, bilirubin));

        for(OWLSubClassOfAxiom ax:ont.getSubClassAxiomsForSubClass(bilirubin_diglucuronide)) {
            OWLClassExpression sup = ax.getSuperClass();
            System.out.println("Superclass: " + sup.toString());
            if(sup.equals(supClass)) {
                System.out.println("Removing: " + ax);
                man.removeAxiom(ont, ax);
                man.addAxiom(ont, df.getOWLSubClassOfAxiom(bilirubin_diglucuronide,
                                  df.getOWLObjectIntersectionOf(direct_reacting_bilirubin, porphyrin_and)));
            }
        }

        man.saveOntology(ont, new OWLXMLDocumentFormat(), IRI.create(new File(outputFile)));
    }
}
