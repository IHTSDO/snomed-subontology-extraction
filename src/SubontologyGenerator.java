import Classification.OntologyReasoningService;
import DefinitionGeneration.DefinitionGenerator;
import DefinitionGeneration.DefinitionGeneratorAbstract;
import DefinitionGeneration.DefinitionGeneratorNNF;
import DefinitionGeneration.RedundancyOptions;
import ExceptionHandlers.ReasonerException;
import NamingApproach.PropertyValueNamer;
import ResultsWriters.OntologySaver;
import ResultsWriters.RF2Printer;
import com.google.common.collect.Sets;
import org.apache.commons.io.FileUtils;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.OWLXMLDocumentFormat;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.snomed.otf.owltoolkit.conversion.ConversionException;
import org.snomed.otf.owltoolkit.conversion.OWLtoRF2Service;
import org.snomed.otf.owltoolkit.service.RF2ExtractionService;
import org.snomed.otf.owltoolkit.util.InputStreamSet;

import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

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
    private Set<OWLAxiom> authoringFormDefinitions;
    private Set<OWLAxiom> nnfDefinitions;
    private OWLOntology subOntology;
    private OWLOntology nnfOntology;
    private static String snomedIRIString = "http://snomed.info/id/";
    private String outputPath;

    public SubontologyGenerator(OWLOntology backgroundOntology, String outputPath) throws OWLOntologyCreationException, ReasonerException {
        this.backgroundOntology = backgroundOntology;
        man = backgroundOntology.getOWLOntologyManager();
        df = man.getOWLDataFactory();

        System.out.println("Initialising ELK taxonomy graph.");
        this.renamePVsAndClassify();
        authoringFormDefinitions = new HashSet<OWLAxiom>();
        nnfDefinitions = new HashSet<OWLAxiom>(); //TODO: add "memory" of generated defs somewhere?

        this.outputPath = outputPath;
    }

    public void computeSubontologyAsRF2(Set<OWLClass> conceptsToDefine) throws OWLException, IOException, ReleaseImportException, ConversionException {
        Set<RedundancyOptions> defaultOptions = new HashSet<RedundancyOptions>();
        defaultOptions.add(RedundancyOptions.eliminateLessSpecificRedundancy);
        defaultOptions.add(RedundancyOptions.eliminateReflexivePVRedundancy);
        defaultOptions.add(RedundancyOptions.eliminateRoleGroupRedundancy);
        this.computeSubontologyAsRF2(conceptsToDefine, defaultOptions);
    }

    //TODO: refactor, split
    //TODO: a lot of redundancy, fine for testing purposes but needs streamlining.
    public void computeSubontologyAsRF2(Set<OWLClass> classesToDefine, Set<RedundancyOptions> redundancyOptions) throws OWLException, IOException, ReleaseImportException, ConversionException {
        //Compute abstract (authoring) form definitions for classes
        computeAuthoringFormDefinitions(classesToDefine, redundancyOptions);

        //Compute NNFs
        computeNNFDefinitions(classesToDefine, redundancyOptions);
        nnfOntology = man.createOntology(nnfDefinitions);

        //include authoring definitions, role inclusions (rbox?), GCIs for defined classes
        populateSubOntology(classesToDefine);

        //Extract RF2 for subontology
        OntologySaver.saveOntology(subOntology, outputPath+"subOntology.owl");

        //Create temporary ontology for nnfs + subOntology, use this to extract everything except the Refset and Relationship files
        //TODO: this is to extract the Concept, Description, Language and Text Definition RF2 files. Make more efficient.
        OWLOntology nnfsWithSubOntology = man.createOntology();
        man.addAxioms(nnfsWithSubOntology, subOntology.getAxioms());
        man.addAxioms(nnfsWithSubOntology, nnfOntology.getAxioms());
        OntologySaver.saveOntology(nnfsWithSubOntology, outputPath+"subOntologyWithNNFs.owl");

        Set<OWLEntity> entitiesInSubontologyAndNNFs = new HashSet<OWLEntity>();
        entitiesInSubontologyAndNNFs.addAll(nnfsWithSubOntology.getClassesInSignature());
        entitiesInSubontologyAndNNFs.addAll(nnfsWithSubOntology.getObjectPropertiesInSignature());
        extractConceptAndTermInformationFromBackgroundRF2(entitiesInSubontologyAndNNFs);

        //Extract relationship rf2 file from nnfs
        printRelationshipRF2(nnfOntology, outputPath);

        //Extract refset rf2 file from authoring definitions TODO: this is only for the OWLAxiom Refset file. Reduce redundancy.
        OWLtoRF2Service owlToRF2Converter = new OWLtoRF2Service();
        InputStream isAuthoring = new FileInputStream(outputPath + "subOntology.owl");
        InputStream owlFileStreamAuthoring = new BufferedInputStream(isAuthoring);
        File rf2ZipAuthoring = new File(outputPath + "authoring_OWLRefset_RF2_" + new Date().getTime() + ".zip");
        owlToRF2Converter.writeToRF2(owlFileStreamAuthoring, new FileOutputStream(rf2ZipAuthoring), new GregorianCalendar(2020, Calendar.SEPTEMBER, 3).getTime());
        System.out.println("Total defined classes: " + authoringFormDefinitions.size());
    }

    private void computeAuthoringFormDefinitions(Set<OWLClass> classesToDefine, Set<RedundancyOptions> redundancyOptions) {
        System.out.println("Computing authoring form.");
        DefinitionGenerator abstractDefinitionsGenerator = new DefinitionGeneratorAbstract(backgroundOntology, reasoningService, namer);
        for(OWLClass cls:classesToDefine) {
            abstractDefinitionsGenerator.generateDefinition(cls, redundancyOptions);
        }
        authoringFormDefinitions = abstractDefinitionsGenerator.getGeneratedDefinitions();

    }

    private void computeNNFDefinitions(Set<OWLClass> classesToDefine, Set<RedundancyOptions> redundancyOptions) {
        System.out.println("Computing necessary normal form (inferred relationships).");
        DefinitionGenerator nnfDefinitionsGenerator = new DefinitionGeneratorNNF(backgroundOntology, reasoningService, namer);
        for(OWLClass cls:classesToDefine) {
            nnfDefinitionsGenerator.generateDefinition(cls, redundancyOptions);
        }
        nnfDefinitions = nnfDefinitionsGenerator.getGeneratedDefinitions();
    }

    private void populateSubOntology(Set<OWLClass> definedClasses) throws OWLOntologyCreationException {
        subOntology = man.createOntology(authoringFormDefinitions);
        Set<OWLClass> classesInSignature = subOntology.getClassesInSignature();
        Set<OWLObjectProperty> propertiesInSignature = subOntology.getObjectPropertiesInSignature();

        //include hierarchy for non-defined classes
        Set<OWLClass> nonDefinedClasses = new HashSet<OWLClass>();
        nonDefinedClasses.addAll(subOntology.getClassesInSignature());
        nonDefinedClasses.removeAll(definedClasses);
        Set<OWLAxiom> nonDefinedClassHierarchy = returnInclusionsForNonDefinedConcepts(nonDefinedClasses);
        man.addAxioms(subOntology, nonDefinedClassHierarchy);

        //add gci axioms for relevant classes
        System.out.println("Adding GCI axioms to subontology.");
        Set<OWLSubClassOfAxiom> gciAxioms = new HashSet<OWLSubClassOfAxiom>();
        for (OWLClass cls : classesInSignature) {
            //gciAxioms.addAll(backgroundOntology.getSubClassAxiomsForSuperClass(cls)); //TODO: check this works.
            for (OWLSubClassOfAxiom ax : backgroundOntology.getSubClassAxiomsForSuperClass(cls)) {
                if (ax.getSubClass() instanceof OWLObjectSomeValuesFrom || ax.getSubClass() instanceof OWLObjectIntersectionOf) {
                    gciAxioms.add(ax);
                }
            }
        }
        System.out.println("total gci axioms added: " + gciAxioms.size());
        man.addAxioms(subOntology, gciAxioms);
        //add subclass inclusions for GCI related axioms //TODO: temporary, improve with above.
        for(OWLSubClassOfAxiom ax:gciAxioms) {
            OWLClassExpression cls = ax.getSuperClass();
            man.addAxioms(subOntology, backgroundOntology.getSubClassAxiomsForSubClass((OWLClass)cls));
        }


        //add relevant property inclusion axioms for properties
        System.out.println("Adding role inclusion axioms to subontology.");
        Set<OWLAxiom> roleInclusions = new HashSet<OWLAxiom>();
        for (OWLAxiom ax : backgroundOntology.getRBoxAxioms(Imports.fromBoolean(false))) {
            if (!Collections.disjoint(ax.getSignature(), propertiesInSignature)) {
                roleInclusions.add(ax);
            }
        }
        System.out.println("total role inclusions added: " + roleInclusions.size());
        man.addAxioms(subOntology, roleInclusions);

        //add relevant annotation axioms
        System.out.println("Adding annotation assertion axioms to subontology: ");
        Set<OWLEntity> subOntologyEntities = new HashSet<OWLEntity>();
        subOntologyEntities.addAll(subOntology.getClassesInSignature());
        subOntologyEntities.addAll(subOntology.getObjectPropertiesInSignature()); //TODO: check, subontology or background entities?
        Set<OWLAnnotationAssertionAxiom> annotationAssertionAxioms = new HashSet<OWLAnnotationAssertionAxiom>();
        for (OWLEntity ent : subOntologyEntities) {
            for (OWLAnnotationAssertionAxiom as : backgroundOntology.getAnnotationAssertionAxioms(ent.getIRI())) {
                if ("skos:prefLabel".equals(as.getProperty().toString()) || "rdfs:label".equals(as.getProperty().toString())) {
                    annotationAssertionAxioms.add(as);
                }
            }
        }

        System.out.println("total annotation assertions added: " + annotationAssertionAxioms.size());
        man.addAxioms(subOntology, annotationAssertionAxioms);
    }

    //returns all class inclusions for non-defined concepts, to retain the hierarchical information
    //TODO: this computes them based on ELK taxonomy. Easier way to extract directly from background?
    private Set<OWLAxiom> returnInclusionsForNonDefinedConcepts(Set<OWLClass> nonDefinedClasses) {
        Set<OWLAxiom> inclusionsForNonDefinedConcepts = new HashSet<OWLAxiom>();

        //extract parent classes from ELK taxonomy graph, remove PVs.
        for(OWLClass cls:nonDefinedClasses) {
            Set<OWLClass> parents = reasoningService.getParentClasses(cls);
            //TODO: reduce redundancy here, bring over "extractRenamedPVs" functionality
            for(OWLClass parentCls:parents) {
                //System.out.println("Parentcls: " + parentCls + " for class: " + cls);
                if (!namer.isNamedPV(parentCls) == true && subOntology.getClassesInSignature().contains(parentCls) == true) {
                    OWLSubClassOfAxiom ax = df.getOWLSubClassOfAxiom(cls, parentCls);
                    inclusionsForNonDefinedConcepts.add(ax);
                }
            }

        }
        return inclusionsForNonDefinedConcepts;
    }

    private static Set<Long> extractAllEntityIDsForOntology(OWLOntology ont) {
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

    //TODO: temporary solution, needs refactoring to avoid redundancy.
    //TODO: sufficient to just concat unique lines?
    /*
    private static void combineConceptAndTermRF2Files(ZipFile subOntologyRF2Zip, ZipFile nnfRF2Zip) {
        File conceptFileRF2;
        File descriptionFileRF2;
        File languageFileRF2;
        File textDefinitionRF2;

        Enumeration<? extends ZipEntry> subOntologyRF2Entries = subOntologyRF2Zip.entries();
        while(subOntologyRF2Entries.hasMoreElements()) {
            ZipEntry subOntologyRF2Entry = subOntologyRF2Entries.nextElement();

            Enumeration<? extends ZipEntry> nnfRF2Entries = nnfRF2Zip.entries();
            while(nnfRF2Entries.hasMoreElements()) {
                ZipEntry nnfRF2Entry = nnfRF2Entries.nextElement();
                if(nnfRF2Entry.getName().equals(subOntologyRF2Entry.getName()) && nnfRF2Entry.getName().contains("")) {
                    System.out.println("Combining RF2 files: " + subOntologyRF2Entry.getName());
                    combineRF2FilePair
                }
            }

        }
    }
     */

    //private static void combineRF2FilePair(ZipEntry subOntologyEntry, ZipEntry nnfEntry) {
    //}

    private static void printRelationshipRF2(OWLOntology nnfOntology, String outputPath) throws IOException, ReleaseImportException, ConversionException {
        RF2Printer printer = new RF2Printer(outputPath);
        printer.printRelationshipRF2File(nnfOntology);
    }

    private void extractConceptAndTermInformationFromBackgroundRF2(Set<OWLEntity> entitiesToExtract) throws IOException, ReleaseImportException {
        //assumes SCT concepts, with standard IRI
        String IRIPrefix = "http://snomed.info/id/";
        Set<Long> entityIDs = new HashSet<Long>();
        System.out.println("Extracting background RF2 information for entities in subontology and inferred relationships.");
        System.out.println("Storing in " + outputPath + "conceptAndTermRF2");
        for(OWLEntity ent:entitiesToExtract) {
            Long id = Long.parseLong(ent.toString().replaceFirst(IRIPrefix, "").replaceAll("[<>]", ""));
            entityIDs.add(id);
        }

        new RF2ExtractionService().extractConcepts(
                new InputStreamSet(new File(outputPath + "SnomedCT_InternationalRF2_PRODUCTION_20200731T120000Z.zip")),
                entityIDs, new File(outputPath + "conceptAndTermRF2"));
    }

    /*
    private static void printOWLRefsetRF2(OWLOntology subOntology, String outputPath) throws IOException, ReleaseImportException, ConversionException {
        RF2Printer printer = new RF2Printer(outputPath);
        printer.printOWLRefsetRF2File(subOntology);
    }
     */

    public Set<OWLClass> readRefset(String refsetPath) {
        Set<OWLClass> classes = new HashSet<OWLClass>();
        try (BufferedReader br = new BufferedReader(new FileReader(new File(refsetPath)))) {
            String inLine = "";
            br.readLine();
            while ((inLine = br.readLine()) != null) {
                // process the line.
                System.out.println("Adding class: " + inLine + " to input");
                classes.add(df.getOWLClass(IRI.create(snomedIRIString + inLine)));
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return classes;
    }

    public static void main(String[] args) throws OWLException, ReasonerException, IOException, ReleaseImportException, ConversionException {
        //test run
        String inputPath = "E:/Users/warren/Documents/aPostdoc/code/~test-code/abstract-definitions-test/sct/";
        File inputOntologyFile = new File(inputPath + "sct-july-2020.owl");

        OWLOntologyManager man = OWLManager.createOWLOntologyManager();
        OWLOntology inputOntology = man.loadOntologyFromOntologyDocument(inputOntologyFile);
        //Set<OWLClass> conceptsToDefine = inputOntology.getClassesInSignature();

        SubontologyGenerator generator = new SubontologyGenerator(inputOntology, "E:/Users/warren/Documents/aPostdoc/code/~test-code/computedSubOntology/");
        //Set<OWLClass> conceptsToDefine = generator.readRefset("E:/Users/warren/Documents/aPostdoc/code/~test-code/abstract-definitions-test/era-refset/era_edta_refset.txt");

        //System.out.println("CONCEPTS BEING DEFINED: " + conceptsToDefine);

        Set<OWLClass> conceptsToDefine = new HashSet<OWLClass>();
        OWLDataFactory df = man.getOWLDataFactory();
        conceptsToDefine.add(df.getOWLClass(IRI.create(snomedIRIString + "14669001")));
        conceptsToDefine.add(df.getOWLClass(IRI.create(snomedIRIString + "90688005")));
        conceptsToDefine.add(df.getOWLClass(IRI.create(snomedIRIString + "42399005")));
        conceptsToDefine.add(df.getOWLClass(IRI.create(snomedIRIString + "302233006")));
        conceptsToDefine.add(df.getOWLClass(IRI.create(snomedIRIString + "51292008")));


        generator.computeSubontologyAsRF2(conceptsToDefine);
    }
}
