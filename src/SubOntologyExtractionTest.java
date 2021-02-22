import Classification.OntologyReasoningService;
import ExceptionHandlers.ReasonerException;
import ResultsWriters.OntologySaver;
import SubOntologyExtraction.SubOntologyExtractor;
import SubOntologyExtraction.SubOntologyRF2Converter;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.snomed.otf.owltoolkit.conversion.ConversionException;
import tools.RefsetHandler;

import java.io.*;
import java.util.HashSet;
import java.util.Set;

public class SubOntologyExtractionTest {


    public static void main(String[] args) throws OWLException, ReasonerException, IOException, ReleaseImportException, ConversionException {
        //test run
        String inputPath = "E:/Users/warren/Documents/aPostdoc/subontologies/TM-subontology/";
        //String inputPath = "E:/Users/warren/Documents/aPostdoc/code/~test-code/examples/";
        File inputOntologyFile = new File(inputPath + "TM_demo.owl");

        String outputPath = "E:/Users/warren/Documents/aPostdoc/subontologies/TM-subontology/";

        OWLOntologyManager man = OWLManager.createOWLOntologyManager();
        OWLOntology inputOntology = man.loadOntologyFromOntologyDocument(inputOntologyFile);

        SubOntologyExtractor generator = new SubOntologyExtractor(inputOntology);
        //Set<OWLClass> conceptsToDefine = RefsetHandler.readRefset("E:/Users/warren/Documents/aPostdoc/code/~test-code/refsets/medicinal_products_demo_refset.txt");
        //Set<OWLClass> conceptsToDefine = inputOntology.getClassesInSignature();

        Set<OWLClass> conceptsToDefine = new HashSet<OWLClass>();
        OWLDataFactory df = man.getOWLDataFactory();

        conceptsToDefine = RefsetHandler.readRefset("E:/Users/warren/Documents/aPostdoc/IAA-content-extraction/refsets/TM_refset_edit.txt");

        //OntologyReasoningService reasoner = new OntologyReasoningService(inputOntology);
        //reasoner.classifyOntology();
        /*
        Set<OWLClass> conceptsToDefine = new HashSet<OWLClass>(conceptsToDefineInitial);
        for(OWLClass cls:conceptsToDefineInitial) {
            if(cls.toString().contains("431591009") || cls.toString().contains("78564009")) {
                conceptsToDefine.removeAll(reasoner.getDescendantClasses(cls));
            }
        }
         */

        //RefsetHandler.printRefset(new HashSet<OWLEntity>(conceptsToDefine), inputPath+"TM_refset_edit.txt");

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

        //TODO: make background file path more understandable (should always be latest SCT release)
        String backgroundFilePath = "E:/Users/warren/Documents/aPostdoc/SCT-files/sct-snapshot-jan-2021.zip";
        SubOntologyRF2Converter converter = new SubOntologyRF2Converter(outputPath, backgroundFilePath);
        converter.convertSubOntologytoRF2(subOntology, nnfOntology);
        System.out.println("Input ontology num axioms: " + inputOntology.getLogicalAxiomCount());
        System.out.println("Input ontology num classes: " + inputOntology.getClassesInSignature().size() + " and properties: " + inputOntology.getObjectPropertiesInSignature().size());
        System.out.println("Subontology Stats");
        System.out.println("Num axioms: " + subOntology.getLogicalAxiomCount());
        System.out.println("Num classes: " + subOntology.getClassesInSignature().size() + " and properties: " + subOntology.getObjectPropertiesInSignature().size());
        System.out.println("Focus classes: " + conceptsToDefine.size());
        System.out.println("Supporting classes: " + (subOntology.getClassesInSignature().size()-conceptsToDefine.size()));
        System.out.println("---------------------");
        System.out.println("Other stats: ");
        System.out.println("Number of definitions added for supporting classes: " + generator.getNumberOfAdditionalSupportingClassDefinitionsAdded());
        System.out.println("Num supporting classes added by incremental signature expansion: " + generator.getNumberOfClassesAddedDuringSignatureExpansion());
        System.out.println("Supporting classes with incrementally added definitions: " + generator.getSupportingClassesWithAddedDefinitions().toString());
    }

}
