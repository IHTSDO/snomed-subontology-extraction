import Classification.OntologyReasoningService;
import DefinitionGeneration.DefinitionGenerator;
import DefinitionGeneration.DefinitionGeneratorAbstract;
import DefinitionGeneration.DefinitionGeneratorNNF;
import DefinitionGeneration.RedundancyOptions;
import ExceptionHandlers.ReasonerException;
import NamingApproach.PropertyValueNamer;
import ResultsWriters.RF2Printer;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.OWLXMLDocumentFormat;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.snomed.otf.owltoolkit.conversion.ConversionException;
import org.snomed.otf.owltoolkit.conversion.OWLtoRF2Service;

import java.io.*;
import java.util.*;

/*
Produces an extracted subontology for a given background ontology and set of concepts.
Includes
        : abstract (authoring) form definitions for each concept in the input set
        : all role inclusion axioms
        : all GCIs (currently, TODO: downward definitions option)
        : NNF definitions (nearest parent, non-redundant named classes and PV relationships -- see necessary normal forms)
Converted to RF2 as follows
        : All RF2 files except Relationships file - extracted from OWLtoRF2 of subontology
        : Relationships RF2 file - extracted from NNF file (TODO: implement, use Axiom to Relationship conversion service as in RF2 printer)
 */
//TODO: currently, this process computes the abstract definitions and NNF definitions entirely separately. Can we combine these somehow?
//      thought: maybe not, since if A <= B and C and PV1, B <= P1, C <= P2 then if B <= C, NNF will be A <= B and PV1.
//      however, in abstract form: B <= C does not imply P1 <= P2, so cannot necessarily remove them!
public class SubontologyGenerator {

    private OWLOntology backgroundOntology;
    private OntologyReasoningService reasoningService;
    private OWLOntologyManager man;
    private OWLDataFactory df;
    private PropertyValueNamer namer;

    public SubontologyGenerator(OWLOntology backgroundOntology) throws OWLOntologyCreationException, ReasonerException {
        this.backgroundOntology = backgroundOntology;
        man = backgroundOntology.getOWLOntologyManager();
        df = man.getOWLDataFactory();

        System.out.println("Initialising ELK taxonomy graph.");
        this.renamePVsAndClassify();

    }

    public void computeSubontologyAsRF2(Set<OWLClass> conceptsToDefine) throws OWLException, IOException, ReleaseImportException, ConversionException {
        Set<RedundancyOptions> defaultOptions = new HashSet<RedundancyOptions>();
        defaultOptions.add(RedundancyOptions.eliminateLessSpecificRedundancy);
        defaultOptions.add(RedundancyOptions.eliminateReflexivePVRedundancy);
        defaultOptions.add(RedundancyOptions.eliminateRoleGroupRedundancy);
        this.computeSubontologyAsRF2(conceptsToDefine, defaultOptions);
    }

    //TODO: refactor, split
    public void computeSubontologyAsRF2(Set<OWLClass> conceptsToDefine, Set<RedundancyOptions> redundancyOptions) throws OWLException, IOException, ReleaseImportException, ConversionException {
        //Compute abstract (authoring) form definitions for concepts
        DefinitionGenerator abstractDefinitionsGenerator = new DefinitionGeneratorAbstract(backgroundOntology, reasoningService, namer);

        for(OWLClass cls:conceptsToDefine) {
            abstractDefinitionsGenerator.generateDefinition(cls, redundancyOptions);
        }

        Set<OWLSubClassOfAxiom> gciAxioms = this.computeGCIAxioms(conceptsToDefine);

        //include authoring definitions, role inclusions (rbox?), GCIs for defined concepts
        OWLOntology subOntology = man.createOntology(abstractDefinitionsGenerator.getGeneratedDefinitions());
        man.addAxioms(subOntology, backgroundOntology.getRBoxAxioms(Imports.fromBoolean(false)));
        man.addAxioms(subOntology, gciAxioms);

        //Compute NNFs
        DefinitionGenerator nnfDefinitionsGenerator = new DefinitionGeneratorNNF(backgroundOntology, reasoningService, namer);

        for(OWLClass cls:conceptsToDefine) {
            nnfDefinitionsGenerator.generateDefinition(cls, redundancyOptions);
        }

        OWLOntology nnfOntology = man.createOntology(nnfDefinitionsGenerator.getGeneratedDefinitions());

        //Extract RF2 for subontology
        String outputPath = "E:/Users/warren/Documents/aPostdoc/code/computedSubOntology/";
        man.saveOntology(subOntology, new OWLXMLDocumentFormat(), IRI.create(new File(outputPath + "subOntology.owl")));
        OWLtoRF2Service owlToRF2Converter = new OWLtoRF2Service();
        InputStream is = new FileInputStream(outputPath + "subOntology.owl");
        InputStream owlFileStream = new BufferedInputStream(is);

        File rf2Zip = new File(outputPath + "subOntology_rf2_" + new Date().getTime() + ".zip");
        owlToRF2Converter.writeToRF2(owlFileStream, new FileOutputStream(rf2Zip), new GregorianCalendar(2020, Calendar.SEPTEMBER, 3).getTime());

        //Extract RF2 for NNFs TODO: bit redundant, fine for testing purposes.
        man.saveOntology(subOntology, new OWLXMLDocumentFormat(), IRI.create(new File(outputPath + "NNF.owl")));
        InputStream isNNF = new FileInputStream(outputPath + "nnf.owl");
        InputStream owlFileStreamNNF = new BufferedInputStream(isNNF);

        File rf2ZipNNF = new File(outputPath + "NNF_rf2_" + new Date().getTime() + ".zip");
        owlToRF2Converter.writeToRF2(owlFileStreamNNF, new FileOutputStream(rf2ZipNNF), new GregorianCalendar(2020, Calendar.SEPTEMBER, 3).getTime());

        //Filter RF2 (to extract relationship file) TODO: again, bit redundant. Improve.
        printRelationshipRF2(nnfOntology, outputPath);
    }

    public static Set<Long> extractAllEntityIDsForOntology(OWLOntology ont) {
        Set<OWLEntity> entitiesInOnt = new HashSet<OWLEntity>();
        entitiesInOnt.addAll(ont.getClassesInSignature());
        entitiesInOnt.addAll(ont.getObjectPropertiesInSignature()); //TODO: only need classes and properties?

        Set<Long> entityIDs = new HashSet<Long>();
        for(OWLEntity ent:entitiesInOnt) {
            String entIDString = ent.toStringID().replace("http://snomed.info/id/","");
            Long entID = Long.valueOf(entIDString);

            //System.out.println(entID);
            entityIDs.add(entID);
        }
        return entityIDs;
    }

    private void renamePVsAndClassify() throws OWLOntologyCreationException, ReasonerException {
        namer = new PropertyValueNamer();
        OWLOntology backgroundOntologyWithRenamings = namer.namePropertyValues(backgroundOntology);

        reasoningService = new OntologyReasoningService(backgroundOntologyWithRenamings);
        reasoningService.classifyOntology();

    }

    private Set<OWLSubClassOfAxiom> computeGCIAxioms(Set<OWLClass> conceptsToDefine) {
        Set<OWLSubClassOfAxiom> gciAxioms = new HashSet<OWLSubClassOfAxiom>();

        for(OWLClass cls:conceptsToDefine) {
            gciAxioms.addAll(backgroundOntology.getSubClassAxiomsForSuperClass(cls)); //TODO: check this works.
        }

        return gciAxioms;
    }


    private static void printRelationshipRF2(OWLOntology nnfOntology, String outputPath) throws IOException, ReleaseImportException, ConversionException {
        RF2Printer printer = new RF2Printer(outputPath);
        printer.printRF2RelationshipFile(nnfOntology);
    }

    public static void main(String[] args) throws OWLException, ReasonerException, IOException, ReleaseImportException, ConversionException {
        //test run
        String inputPath = "E:/Users/warren/Documents/aPostdoc/code/~test-code/abstract-definitions-test/anatomy-module/";
        File inputOntologyFile = new File(inputPath + "anatomy.owl");

        OWLOntologyManager man = OWLManager.createOWLOntologyManager();
        OWLOntology inputOntology = man.loadOntologyFromOntologyDocument(inputOntologyFile);
        Set<OWLClass> conceptsToDefine = inputOntology.getClassesInSignature();

        SubontologyGenerator generator = new SubontologyGenerator(inputOntology);
        generator.computeSubontologyAsRF2(conceptsToDefine);
    }


}
