import ExceptionHandlers.ReasonerException;
import ResultsWriters.MapPrinter;
import ResultsWriters.OntologySaver;
import SubOntologyExtraction.SubOntologyExtractionHandler;
import SubOntologyExtraction.SubOntologyRF2ConversionService;
import Verification.VerificationChecker;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.snomed.otf.owltoolkit.conversion.ConversionException;
import tools.InputSignatureHandler;

import java.io.*;
import java.util.Set;

public class RunSubontologyExtraction {
    public static void main(String[] args) throws OWLException, ReasonerException, IOException, ReleaseImportException, ConversionException {
        /*
        * Input for subontology extraction: source ontology (path), focus concepts (list, refset as .txt), source RF2 file for OWL to RF2 conversion
         */
        File sourceOntologyFile = new File("E:/Users/warren/Documents/aPostdoc/SCT-files/sct-july-2020.owl");
        //File inputRefsetFile = new File("E:/Users/warren/Documents/aPostdoc/IAA-content-extraction/refsets/medicinal-products/medicinal_products_refset.txt");
        File inputRefsetFile = new File("E:/Users/warren/Documents/aPostdoc/IAA-content-extraction/refsets/ips/ips_refset.txt");

        //ensure same version as source ontology OWL file
        String sourceRF2FilePath = "E:/Users/warren/Documents/aPostdoc/SCT-files/sct-snapshot-july-2020.zip";

        String outputPath = "E:/Users/warren/Documents/aPostdoc/subontologies/ips/";

        boolean computeRF2 = false;
        boolean verifySubontology = false;

        OWLOntologyManager man = OWLManager.createOWLOntologyManager();
        OWLOntology sourceOntology = man.loadOntologyFromOntologyDocument(sourceOntologyFile);

        Set<OWLClass> conceptsToDefine = InputSignatureHandler.readRefset(inputRefsetFile);

        SubOntologyExtractionHandler generator = new SubOntologyExtractionHandler(sourceOntology, conceptsToDefine);
        generator.computeSubontology(computeRF2);

        OWLOntology subOntology = generator.getCurrentSubOntology();

        OntologySaver.saveOntology(subOntology, outputPath+"subOntology.owl");

        //Extract RF2 for subontology
        if(computeRF2) {
            generator.generateNNFs();

            OWLOntology nnfOntology = generator.getNnfOntology();
            OntologySaver.saveOntology(nnfOntology, outputPath+"subOntologyNNFs.owl");

            SubOntologyRF2ConversionService.convertSubOntologytoRF2(subOntology, nnfOntology, outputPath, sourceRF2FilePath);
        }

        if(verifySubontology) {
            VerificationChecker checker = new VerificationChecker();
            System.out.println("==========================");
            System.out.println("VERIFICATION: Step (1) focus concept equivalence");
            System.out.println("==========================");
            boolean satisfiesEquivalentFocusConceptsRequirement = checker.namedFocusConceptsSatisfyEquivalence(generator.getFocusConcepts(), subOntology, sourceOntology);
            System.out.println("Satisfies equivalence of focus classes requirement?" + satisfiesEquivalentFocusConceptsRequirement);

            System.out.println("==========================");
            System.out.println("VERIFICATION: Step (2) transitive closure equal within sig(subOntology)");
            System.out.println("==========================");
            boolean satisfiesTransitiveClosureReq = checker.satisfiesTransitiveClosureRequirement(subOntology, sourceOntology);
            System.out.println("Satisfies transitive closure requirement?" + satisfiesTransitiveClosureReq);

            if (!satisfiesEquivalentFocusConceptsRequirement || !satisfiesTransitiveClosureReq) {
                if(!satisfiesEquivalentFocusConceptsRequirement) {
                    System.out.println("Equivalence test failed.");
                    Set<OWLClass> failedCases = checker.getFailedFocusClassEquivalenceCases();
                    System.out.println("Failed cases for equivalence: ");
                    System.out.println(failedCases);
                    System.out.println("Num failed cases: " + failedCases.size() + " out of: " + conceptsToDefine.size());
                }
                if(!satisfiesTransitiveClosureReq) {
                    System.out.println("Transitive Closure test failed");
                    System.out.println("Failed classes: " + checker.getLatestSourceOntologyClosureDiffs().keySet());
                    MapPrinter printer = new MapPrinter(outputPath);
                    printer.printGeneralMap(checker.getLatestSubOntologyClosureDiffs(), "subOntDiffMap.txt");
                    printer.printGeneralMap(checker.getLatestSourceOntologyClosureDiffs(), "sourceOntDiffMap.txt");
                }
            }
            else {
                System.out.println("Verification passed.");
                System.out.println("Input ontology num axioms: " + sourceOntology.getLogicalAxiomCount());
                System.out.println("Input ontology num classes: " + sourceOntology.getClassesInSignature().size() + " and properties: " + sourceOntology.getObjectPropertiesInSignature().size());
                System.out.println("Subontology Stats");
                System.out.println("Num axioms: " + subOntology.getLogicalAxiomCount());
                System.out.println("Num classes: " + subOntology.getClassesInSignature().size() + " and properties: " + subOntology.getObjectPropertiesInSignature().size());
                System.out.println("Focus classes: " + conceptsToDefine.size());
                System.out.println("Supporting classes: " + (subOntology.getClassesInSignature().size() - conceptsToDefine.size()));
                System.out.println("---------------------");
                System.out.println("Other stats: ");
                System.out.println("Number of definitions added for supporting classes: " + generator.getNumberOfAdditionalSupportingClassDefinitionsAdded());
                System.out.println("Num supporting classes added by incremental signature expansion: " + generator.getNumberOfClassesAddedDuringSignatureExpansion());
                System.out.println("Supporting classes with incrementally added definitions: " + generator.getSupportingClassesWithAddedDefinitions().toString());
            }
        }
    }
}
