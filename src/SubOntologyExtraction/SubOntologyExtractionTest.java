package SubOntologyExtraction;

import ExceptionHandlers.ReasonerException;
import ResultsWriters.OntologySaver;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLException;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.snomed.otf.owltoolkit.conversion.ConversionException;

import java.io.File;
import java.io.IOException;
import java.util.Set;

public class SubOntologyExtractionTest {


    public static void main(String[] args) throws OWLException, ReasonerException, IOException, ReleaseImportException, ConversionException {
        //test run
        String inputPath = "E:/Users/warren/Documents/aPostdoc/code/~test-code/SCT-files/";
        File inputOntologyFile = new File(inputPath + "sct-july-2020.owl");

        String outputPath = "E:/Users/warren/Documents/aPostdoc/code/~test-code/computedSubOntology/";

        OWLOntologyManager man = OWLManager.createOWLOntologyManager();
        OWLOntology inputOntology = man.loadOntologyFromOntologyDocument(inputOntologyFile);
        //Set<OWLClass> conceptsToDefine = inputOntology.getClassesInSignature();

        SubOntologyExtractor generator = new SubOntologyExtractor(inputOntology, outputPath);
        Set<OWLClass> conceptsToDefine = generator.readRefset("E:/Users/warren/Documents/aPostdoc/code/~test-code/abstract-definitions-test/era-refset/era_edta_refset.txt");
        /*
        Set<OWLClass> conceptsToDefine = new HashSet<OWLClass>();
        OWLDataFactory df = man.getOWLDataFactory();
        conceptsToDefine.add(df.getOWLClass(IRI.create(snomedIRIString + "14669001")));
        conceptsToDefine.add(df.getOWLClass(IRI.create(snomedIRIString + "90688005")));
        conceptsToDefine.add(df.getOWLClass(IRI.create(snomedIRIString + "42399005")));
        conceptsToDefine.add(df.getOWLClass(IRI.create(snomedIRIString + "302233006")));
        conceptsToDefine.add(df.getOWLClass(IRI.create(snomedIRIString + "51292008")));
         */
        generator.computeSubontology(conceptsToDefine);

        OWLOntology subOntology = generator.getSubOntology();
        OWLOntology nnfOntology = generator.getNnfOntology();

        //Extract RF2 for subontology
        OntologySaver.saveOntology(subOntology, outputPath+"subOntology.owl");

        //Create temporary ontology for nnfs + subOntology, use this to extract everything except the Refset and Relationship files
        //TODO: this is to extract the Concept, Description, Language and Text Definition RF2 files. Make more efficient.
        OWLOntology nnfsWithSubOntology = man.createOntology();
        man.addAxioms(nnfsWithSubOntology, subOntology.getAxioms());
        man.addAxioms(nnfsWithSubOntology, nnfOntology.getAxioms());
        OntologySaver.saveOntology(nnfsWithSubOntology, outputPath+"subOntologyWithNNFs.owl");


        SubOntologyRF2Converter converter = new SubOntologyRF2Converter(outputPath);
        converter.convertSubOntologytoRF2(subOntology, nnfOntology);
    }
}
