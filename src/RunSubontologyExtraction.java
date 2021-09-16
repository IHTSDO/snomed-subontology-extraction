import DefinitionGeneration.RedundancyOptions;
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
import java.util.HashSet;
import java.util.Set;

public class RunSubontologyExtraction {
    public static void main(String[] args) throws OWLException, ReasonerException, IOException, ReleaseImportException, ConversionException {
        OWLOntologyManager man = OWLManager.createOWLOntologyManager();
        /*
        * Input for subontology extraction: source ontology (path), inputRefsetFile for focus concepts (list, refset as .txt)
         */
        File sourceOntologyFile = new File("E:/Users/warren/Documents/aPostdoc/SCT-files/sct-july-2020.owl");
        File inputRefsetFile = new File("E:/Users/warren/Documents/aPostdoc/IAA-content-extraction/refsets/dentistry/dentistry_refset.txt");
        //if focus concepts specified as refset
        Set<OWLClass> conceptsToDefine = InputSignatureHandler.readRefset(inputRefsetFile);

        //alternatively, can specify concepts directly as a set e.g.
        /*
        Set<OWLClass> conceptsToDefine = new HashSet<OWLClass>();
        OWLDataFactory df = man.getOWLDataFactory();
        conceptsToDefine.add(df.getOWLClass(IRI.create("http://snomed.info/id/" + 22688005)));
         */

        boolean computeRF2 = false;
        //if computing RF2, provide RF2 files corresponding to the sourceOntologyFile OWL file for OWL to RF2 conversion -- ensure same ontology version as sourceOntologyFile
        String sourceRF2FilePath = "";
        if(computeRF2) {
            sourceRF2FilePath = "E:/Users/warren/Documents/aPostdoc/SCT-files/sct-snapshot-jan-2021.zip";
        }

        boolean verifySubontology = false;

        //output path
        String outputPath = "E:/Users/warren/Documents/aPostdoc/subontologies/dentistry/";

        OWLOntology sourceOntology = man.loadOntologyFromOntologyDocument(sourceOntologyFile);
        //generating subontology
        SubOntologyExtractionHandler generator = new SubOntologyExtractionHandler(sourceOntology, conceptsToDefine);

        //redundancy elimination options -- optional, if not specified, default will be used
        Set<RedundancyOptions> customRedundancyOptions = new HashSet<RedundancyOptions>();

        //Example of specifying non-default redundancy elimination options.
        /*
        Set<RedundancyOptions> customRedundancyOptions = new HashSet<RedundancyOptions>();
        customRedundancyOptions.add(RedundancyOptions.eliminateLessSpecificRedundancy);
        customRedundancyOptions.add(RedundancyOptions.eliminateRoleGroupRedundancy);
        customRedundancyOptions.add(RedundancyOptions.eliminateSufficientProximalGCIs);
         */

        //with default redundancy elimination -- if using non-default (below), comment out this line and uncomment the next block
        long startTime = System.currentTimeMillis();
        if(customRedundancyOptions.isEmpty()) {
            generator.computeSubontology(computeRF2);
        }
        else {
            //with non-default redundancy elimination options specified by user
            generator.computeSubontology(computeRF2, customRedundancyOptions);
        }
        long endTime = System.currentTimeMillis();
        OWLOntology subOntology = generator.getCurrentSubOntology();
        OntologySaver.saveOntology(subOntology, outputPath+"subOntology.owl");
        System.out.println("Time taken: " + (endTime - startTime)/1000 + " seconds");
        //Extract RF2 for subontology
        if(computeRF2) {
            //generator.generateNNFs();
            OWLOntology nnfOntology = generator.getNnfOntology();
            OntologySaver.saveOntology(nnfOntology, outputPath + "subOntologyNNFs.owl");

            SubOntologyRF2ConversionService.convertSubOntologytoRF2(subOntology, nnfOntology, outputPath, sourceRF2FilePath);
        }
        if(verifySubontology) {
            VerificationChecker checker = new VerificationChecker();
            System.out.println("==========================");
            System.out.println("VERIFICATION: Step (1) (defined) focus concept equivalence");
            System.out.println("==========================");
            boolean satisfiesEquivalentFocusConceptsRequirement = checker.namedFocusConceptsSatisfyEquivalence(generator.getFocusConcepts(), subOntology, sourceOntology);
            System.out.println("Satisfies equivalence of (defined) focus classes requirement?" + satisfiesEquivalentFocusConceptsRequirement);

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
