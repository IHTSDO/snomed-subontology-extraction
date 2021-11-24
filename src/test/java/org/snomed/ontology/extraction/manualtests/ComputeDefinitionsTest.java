package org.snomed.ontology.extraction.manualtests;

import org.snomed.ontology.extraction.definitiongeneration.DefinitionGenerator;
import org.snomed.ontology.extraction.definitiongeneration.DefinitionGeneratorAbstract;
import org.snomed.ontology.extraction.definitiongeneration.DefinitionGeneratorNNF;
import org.snomed.ontology.extraction.definitiongeneration.RedundancyOptions;
import org.snomed.ontology.extraction.exception.ReasonerException;
import org.snomed.ontology.extraction.classification.OntologyReasoningService;
import org.snomed.ontology.extraction.naming.IntroducedNameHandler;
import org.snomed.ontology.extraction.writers.RF2Printer;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.OWLXMLDocumentFormat;
import org.semanticweb.owlapi.model.*;
import org.snomed.otf.owltoolkit.conversion.ConversionException;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ComputeDefinitionsTest {

    public static void main(String[] args) throws OWLOntologyCreationException, ReasonerException, IOException, OWLOntologyStorageException, ConversionException {
        //File inputOntologyFile = new File(args[0]);
        String inputPath = "E:/Users/warren/Documents/aPostdoc/SCT-files/tests/role-chain-issue/";
        File inputOntologyFile = new File(inputPath + "snomed-role-chain-issue.owl");
        //File inputOntologyFile = new File(inputPath + "module_left_facet_joint.owl");
        String defType = "NNF";

        OWLOntologyManager man = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = man.getOWLDataFactory();
        OWLOntology inputOntology = man.loadOntologyFromOntologyDocument(inputOntologyFile);
        man = inputOntology.getOWLOntologyManager();
        df = man.getOWLDataFactory();

        ///////////
        //RENAMING TEST
        ///////////
        //for each PV in ontology, add a definition of the form PVCi == PVi
        IntroducedNameHandler namer = new IntroducedNameHandler(inputOntology);
        OWLOntology inputOntologyWithRenamings = namer.returnOntologyWithNamings();

        //perform classification using ELK
        OntologyReasoningService reasoningService = new OntologyReasoningService(inputOntologyWithRenamings);
        reasoningService.classifyOntology();

        String inputOntologyPath = inputOntologyFile.getAbsolutePath().substring(0, inputOntologyFile.getAbsolutePath().lastIndexOf(File.separator)+1);

        man.removeAxioms(inputOntologyWithRenamings, inputOntology.getAxioms());
        //man.saveOntology(inputOntologyWithRenamings, IRI.create(new File(inputPath + "pv_test_renamings.owl")));
        //man.saveOntology(inputOntology, IRI.create(new File(inputPath + "input_test.owl")));

        //////////
        //NNF TEST
        //////////
        //compute definitions using renames.
        Set<OWLClass> ontClasses = new HashSet<OWLClass>();
        //ontClasses.addAll(inputOntology.getClassesInSignature());

        //List<OWLClass> classesToDefine = new ArrayList<OWLClass>(ontClasses);
        List<OWLClass> classesToDefine = new ArrayList<OWLClass>();
        classesToDefine.add(df.getOWLClass(IRI.create("http://snomed.info/id/427633002")));

        Set<OWLAxiom> definitions = new HashSet<OWLAxiom>();

        DefinitionGenerator definitionGenerator;
        if(defType.equals("abstract")) {
            definitionGenerator = new DefinitionGeneratorAbstract(inputOntology, reasoningService, namer);
        }
        else {
            definitionGenerator = new DefinitionGeneratorNNF(inputOntology, reasoningService, namer);
        }

        Set<RedundancyOptions> redundancyOptions = new HashSet<RedundancyOptions>();
        redundancyOptions.add(RedundancyOptions.eliminateLessSpecificRedundancy);
        redundancyOptions.add(RedundancyOptions.eliminateReflexivePVRedundancy);
        redundancyOptions.add(RedundancyOptions.eliminateRoleGroupRedundancy);

        int numClasses = inputOntology.getClassesInSignature().size();

        int i=0;
        for(OWLClass cls:classesToDefine) {
            i++;
            System.out.println("Generating definition for class: " + cls.toString());
            System.out.println("Classes defined: " + i + " of: " + numClasses);
            definitionGenerator.generateDefinition(cls, redundancyOptions);
        }

        definitions.addAll(definitionGenerator.getAllGeneratedDefinitions());

        OWLOntology definitionsOnt = man.createOntology();
        man.addAxioms(definitionsOnt, definitions);

        Set<OWLAnnotationAssertionAxiom> annotations = new HashSet<OWLAnnotationAssertionAxiom>();
        for(OWLEntity ent : definitionsOnt.getSignature()) {
            annotations.addAll(inputOntology.getAnnotationAssertionAxioms(ent.getIRI()));
        }

        man.addAxioms(definitionsOnt, annotations);

        man.saveOntology(definitionsOnt, new OWLXMLDocumentFormat(),
                IRI.create(new File(inputPath + defType + "_definitions_" + inputOntologyFile.getName())));

        ////////////////////////////
        //print in RF2 tuple format
        ////////////////////////////
        RF2Printer rf2Printer = new RF2Printer(new File(inputOntologyPath + defType));
        System.out.println(inputOntologyPath);

        rf2Printer.printNNFsAsFSNTuples(definitionsOnt);
        System.out.println("Num undefined classes: " + definitionGenerator.getUndefinedClassAxioms().size());
        System.out.println("Undefined classes: " + definitionGenerator.getUndefinedClassAxioms());
        System.out.println("Num defined classes:"  + definitionGenerator.getAllGeneratedDefinitions().size());

        System.out.println("Printing pv map.");
        namer.printNameAndPvPairs(inputPath);

    }

}
