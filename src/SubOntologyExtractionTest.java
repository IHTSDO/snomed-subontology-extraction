import ExceptionHandlers.ReasonerException;
import ResultsWriters.MapPrinter;
import ResultsWriters.OntologySaver;
import SubOntologyExtraction.SubOntologyExtractionHandler;
import SubOntologyExtraction.SubOntologyRF2Converter;
import Verification.VerificationChecker;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.snomed.otf.owltoolkit.conversion.ConversionException;
import tools.InputSignatureHandler;

import java.io.*;
import java.util.Set;

public class SubOntologyExtractionTest {
    public static void main(String[] args) throws OWLException, ReasonerException, IOException, ReleaseImportException, ConversionException {
        //test run
        String inputPath = "E:/Users/warren/Documents/aPostdoc/SCT-files/";
        File inputOntologyFile = new File(inputPath + "sct-july-2018.owl");
        File inputRefsetFile = new File("E:/Users/warren/Documents/aPostdoc/IAA-content-extraction/refsets/nursing/nursing_full_refset.txt");

        String outputPath = "E:/Users/warren/Documents/aPostdoc/subontologies/nursing/";
        boolean verifySubontology = false;

        OWLOntologyManager man = OWLManager.createOWLOntologyManager();
        OWLOntology inputOntology = man.loadOntologyFromOntologyDocument(inputOntologyFile);

        Set<OWLClass> conceptsToDefine = InputSignatureHandler.readRefset(inputRefsetFile);

        SubOntologyExtractionHandler generator = new SubOntologyExtractionHandler(inputOntology, conceptsToDefine);
        generator.computeSubontology();

        OWLOntology subOntology = generator.getCurrentSubOntology();
        OWLOntology nnfOntology = generator.getNnfOntology();

        OntologySaver.saveOntology(subOntology, outputPath+"subOntology.owl");

        //Create temporary ontology for nnfs + subOntology, use this to extract everything except the Refset and Relationship files
        //TODO: this is to extract the Concept, Description, Language and Text Definition RF2 files. Make more efficient.
        OWLOntology nnfsWithSubOntology = man.createOntology();
        man.addAxioms(nnfsWithSubOntology, subOntology.getAxioms());
        man.addAxioms(nnfsWithSubOntology, nnfOntology.getAxioms());
        OntologySaver.saveOntology(nnfsWithSubOntology, outputPath+"subOntologyWithNNFs.owl");

        //TODO: make background file path more understandable (should always be latest SCT release)
        String backgroundFilePath = "E:/Users/warren/Documents/aPostdoc/SCT-files/sct-snapshot-jan-2021.zip";
        //Extract RF2 for subontology
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

        if(verifySubontology) {
            VerificationChecker checker = new VerificationChecker();
            System.out.println("==========================");
            System.out.println("VERIFICATION: ");
            System.out.println("==========================");
            boolean satisfiesTransitiveClosureReq = checker.satisfiesTransitiveClosureRequirement(subOntology, inputOntology);
            System.out.println("Satisfies transitive closure requirement?" + satisfiesTransitiveClosureReq);
            MapPrinter printer = new MapPrinter(outputPath);
            printer.printGeneralMap(checker.getLatestSubOntologyClosureDiffs(), "subOntDiffMap.txt");
            printer.printGeneralMap(checker.getLatestSourceOntologyClosureDiffs(), "sourceOntDiffMap.txt");

        }
    }
}
