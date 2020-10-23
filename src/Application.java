import DefinitionGeneration.DefinitionGenerator;
import DefinitionGeneration.DefinitionGeneratorNNF;
import DefinitionGeneration.RedundancyOptions;
import ExceptionHandlers.ReasonerException;
import Classification.OntologyReasoningService;
import RenamingApproach.PropertyValueNamer;
import ResultsWriters.MapPrinter;
import ResultsWriters.RF2Printer;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.OWLXMLDocumentFormat;
import org.semanticweb.owlapi.model.*;
import org.snomed.otf.owltoolkit.conversion.ConversionException;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class Application {

    /*
    Test alternative to current toolkit approach to NNF generation. Steps include:
        1) For each PV (existential restriction) in SCT,

     */
    public static void main(String[] args) throws OWLOntologyCreationException, ReasonerException, IOException, OWLOntologyStorageException, ConversionException {
        //File inputOntologyFile = new File(args[0]);
        String inputPath = "E:/Users/warren/Documents/aPostdoc/code/~test-code/abstract-definitions-test/";
        File inputOntologyFile = new File(inputPath + "sct/sct-july-2020-noTransitivity.owl");

        OWLOntologyManager man = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = man.getOWLDataFactory();
        OWLOntology inputOntology = man.loadOntologyFromOntologyDocument(inputOntologyFile);
        man = inputOntology.getOWLOntologyManager();
        df = man.getOWLDataFactory();

        ///////////
        //RENAMING TEST
        ///////////
        //for each PV in ontology, add a definition of the form PVCi == PVi
        PropertyValueNamer renamer = new PropertyValueNamer();
        OWLOntology inputOntologyWithRenamings = renamer.namePropertyValues(inputOntology);

        //MapPrinter printer = new MapPrinter(inputPath + "namingTest/");

        //Map<OWLObjectSomeValuesFrom, OWLClass> pvRenamingsMap = renamer.getPvNamingMap();

        //printer.printNamingsForPVs(pvRenamingsMap);

        //perform classification using ELK
        OntologyReasoningService reasoningService = new OntologyReasoningService(inputOntologyWithRenamings);
        reasoningService.classifyOntology();

        String inputOntologyPath = inputOntologyFile.getAbsolutePath().substring(0, inputOntologyFile.getAbsolutePath().lastIndexOf(File.separator)+1);

        man.removeAxioms(inputOntologyWithRenamings, inputOntology.getAxioms());
        man.saveOntology(inputOntologyWithRenamings, new OWLXMLDocumentFormat(), IRI.create(new File("E:/Users/warren/Documents/aPostdoc/code/~test-code/abstract-definitions-test/" +
                "pv_test_renamings.owl")));
        man.saveOntology(inputOntology, new OWLXMLDocumentFormat(), IRI.create(new File("E:/Users/warren/Documents/aPostdoc/code/~test-code/abstract-definitions-test/" +
                "input_test.owl")));

        //////////
        //NNF TEST
        //////////
        //compute definitions using renames. TODO: NNF, abstract defs. So will need way to test if primitive or non-primitive
        Set<OWLClass> ontClasses = new HashSet<OWLClass>();

        ontClasses.addAll(inputOntology.getClassesInSignature());
        List<OWLClass> classesToDefine = new ArrayList<OWLClass>(ontClasses);
        for(OWLClass cls:ontClasses) {
            if(renamer.namingPvMap.containsKey(cls)) {
                classesToDefine.remove(cls);
            }
        }
        classesToDefine.remove(df.getOWLThing());
        classesToDefine.remove(df.getOWLNothing());

        Set<OWLAxiom> definitionsNNF = new HashSet<OWLAxiom>();

        DefinitionGeneratorNNF definitionGenerator = new DefinitionGeneratorNNF(inputOntology, reasoningService, renamer);

        Set<RedundancyOptions> redundancyOptions = new HashSet<RedundancyOptions>();
        redundancyOptions.add(RedundancyOptions.eliminatereflexivePVRedundancy);
        redundancyOptions.add(RedundancyOptions.eliminateRoleGroupRedundancy);

        for(OWLClass cls:classesToDefine) {
            System.out.println("Generating NNF for class: " + cls.toString());
            definitionGenerator.generateDefinition(cls, redundancyOptions);
        }

       // for(OWLClass cls:classesToDefine) {
          //  if(cls.toString().contains("387139005")) {
          //      definitionGenerator.generateDefinition(cls, redundancyOptions);
          // }
       // }

        definitionsNNF.addAll(definitionGenerator.getGeneratedDefinitions());

        OWLOntology definitionsOnt = man.createOntology();
        man.addAxioms(definitionsOnt, definitionsNNF);

        Set<OWLAnnotationAssertionAxiom> annotations = new HashSet<OWLAnnotationAssertionAxiom>();
        for(OWLEntity ent : definitionsOnt.getSignature()) {
            annotations.addAll(inputOntology.getAnnotationAssertionAxioms(ent.getIRI()));
        }

        man.addAxioms(definitionsOnt, annotations);

        man.saveOntology(definitionsOnt, new OWLXMLDocumentFormat(),
                IRI.create(new File("E:/Users/warren/Documents/aPostdoc/code/~test-code/abstract-definitions-test/NNF_definitions_" + inputOntologyFile.getName())));

        //print in RF2 tuple format
        RF2Printer rf2Printer = new RF2Printer(inputOntologyPath);
        System.out.println(inputOntologyPath);
        System.out.println(definitionsOnt.getAxioms());
        //RF2Printer.printNNFsAsRF2Tuples(definitionsOnt);
        rf2Printer.printNNFsAsFSNTuples(definitionsOnt);
    }

}
