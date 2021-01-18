package SubOntologyExtraction;

import Classification.OntologyReasoningService;
import DefinitionGeneration.DefinitionGenerator;
import DefinitionGeneration.DefinitionGeneratorAbstract;
import DefinitionGeneration.DefinitionGeneratorNNF;
import DefinitionGeneration.RedundancyOptions;
import ExceptionHandlers.ReasonerException;
import NamingApproach.PropertyValueNamer;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.snomed.otf.owltoolkit.conversion.ConversionException;

import java.io.*;
import java.util.*;

/*
Produces an extracted subontology for a given background ontology and set of concepts.
Includes
        : abstract (authoring) form definitions for each concept in the input set
        : all role inclusion axioms
        : all GCIs (currently, TODO: downward definitions option)
        : NNF definitions (nearest parent, non-redundant named classes and PV relationships -- see necessary normal forms)
 */
//TODO: currently, this process computes the abstract definitions and NNF definitions entirely separately. Can we combine these somehow?
//      thought: maybe not, since if A <= B and C and PV1, B <= P1, C <= P2 then if B <= C, NNF will be A <= B and PV1.
//      however, in abstract form: B <= C does not imply P1 <= P2, so cannot necessarily remove them!
public class SubOntologyExtractor {

    private OWLOntology sourceOntology;
    private OntologyReasoningService sourceOntologyReasoningService;
    private OWLOntologyManager man;
    private OWLDataFactory df;
    private PropertyValueNamer namer;
    private Set<OWLAxiom> authoringFormDefinitions;
    private Set<OWLAxiom> nnfDefinitions;
    private Set<OWLClass> focusClasses;
    private OWLOntology subOntology;
    private OWLOntology nnfOntology;
    private static String snomedIRIString = "http://snomed.info/id/";
    private OWLClass sctTop;
    //TODO: temp variable, implement stats handler
    //classes and definitions added during signature expansion
    private Set<OWLClass> definedSupportingClasses = new HashSet<OWLClass>();
    private List<OWLClass> additionalClassesInExpandedSignature = new ArrayList<OWLClass>();
    private Set<OWLAxiom> additionalSupportingClassDefinitionsAdded = new HashSet<OWLAxiom>();

    public SubOntologyExtractor(OWLOntology backgroundOntology, String outputPath) throws OWLOntologyCreationException, ReasonerException {
        this.sourceOntology = backgroundOntology;
        man = backgroundOntology.getOWLOntologyManager();
        df = man.getOWLDataFactory();
        sctTop = df.getOWLClass(IRI.create("http://snomed.info/id/138875005"));

        System.out.println("Initialising ELK taxonomy graph.");
        this.renamePVsAndClassify();
        authoringFormDefinitions = new HashSet<OWLAxiom>();
        nnfDefinitions = new HashSet<OWLAxiom>(); //TODO: add "memory" of generated defs somewhere?
    }

    public void computeSubontology(Set<OWLClass> classesToDefine) throws OWLException, IOException, ReleaseImportException, ConversionException, ReasonerException {
        Set<RedundancyOptions> defaultOptions = new HashSet<RedundancyOptions>();
        defaultOptions.add(RedundancyOptions.eliminateLessSpecificRedundancy);
        defaultOptions.add(RedundancyOptions.eliminateReflexivePVRedundancy);
        defaultOptions.add(RedundancyOptions.eliminateRoleGroupRedundancy);
        this.computeSubontology(classesToDefine, defaultOptions);
    }

    //TODO: refactor, split
    //TODO: a lot of redundancy, fine for testing purposes but needs streamlining.
    public void computeSubontology(Set<OWLClass> classesToDefine, Set<RedundancyOptions> redundancyOptions) throws OWLException, IOException, ReleaseImportException, ConversionException, ReasonerException {
        //store input signature (needed to differentiate between supporting and defined classes)
        focusClasses = classesToDefine; //TODO: check this is needed, in progress.

        //Compute abstract (authoring) form definitions for classes
        computeAuthoringFormDefinitions(classesToDefine, redundancyOptions);

        //Compute NNFs
        computeNNFDefinitions(classesToDefine, redundancyOptions);
        nnfOntology = man.createOntology(nnfDefinitions);

        //include authoring definitions, role inclusions (rbox?), GCIs for defined classes
        populateSubOntology();
    }

    private void computeAuthoringFormDefinitions(Set<OWLClass> classesToDefine, Set<RedundancyOptions> redundancyOptions) {
        System.out.println("Computing authoring form.");
        DefinitionGenerator abstractDefinitionsGenerator = new DefinitionGeneratorAbstract(sourceOntology, sourceOntologyReasoningService, namer);
        classesToDefine.remove(df.getOWLNothing());
        for(OWLClass cls:classesToDefine) {
            System.out.println("Authoring def for cls: " + cls);
            abstractDefinitionsGenerator.generateDefinition(cls, redundancyOptions);
        }
        authoringFormDefinitions = abstractDefinitionsGenerator.getGeneratedDefinitions();
    }

    private void computeNNFDefinitions(Set<OWLClass> classesToDefine, Set<RedundancyOptions> redundancyOptions) {
        System.out.println("Computing necessary normal form (inferred relationships).");
        DefinitionGenerator nnfDefinitionsGenerator = new DefinitionGeneratorNNF(sourceOntology, sourceOntologyReasoningService, namer);
        classesToDefine.remove(df.getOWLNothing());
        for(OWLClass cls:classesToDefine) {
            System.out.println("NNF def for cls: " + cls);
            nnfDefinitionsGenerator.generateDefinition(cls, redundancyOptions);
        }
        nnfDefinitions = nnfDefinitionsGenerator.getGeneratedDefinitions();
    }

    private void populateSubOntology() throws OWLOntologyCreationException, ReasonerException {
        subOntology = man.createOntology(authoringFormDefinitions);
        //TODO: check ordering of these steps.
        //add gci axioms for relevant classes
        addGCIAxioms();

        //add property inclusion axioms for properties
        addPropertyInclusions();

        //TODO: which?
        addSupportingClassesInformation();

        //add grouper classes
        addGrouperClasses();

        //add atomic class hierarchy where needed. Classes in the focus set, or classes whose definitions have been added, do not need to be considered here.
        addAtomicClassHierarchy();

        //add top level classes
        addTopLevelClasses();

        //add top level roles -- //TODO: 12-01-2021, add role hierarchy needed. Initial: drag in all subobjectproperty, chains wrt to signature.
        //addTopLevelRoles();

        //add necessary metadata

        //add relevant annotation axioms
        addAnnotationAssertions();

        //TODO: temp printing, get stats handler and remove.
        System.out.println("Added classes: " + additionalClassesInExpandedSignature);
    }

    private void addGCIAxioms() {
        Set<OWLClass> classesInSignature = subOntology.getClassesInSignature();
        System.out.println("Adding GCI axioms to subontology.");
        Set<OWLSubClassOfAxiom> gciAxioms = new HashSet<OWLSubClassOfAxiom>();
        for (OWLClass cls : classesInSignature) {
            //gciAxioms.addAll(backgroundOntology.getSubClassAxiomsForSuperClass(cls)); //TODO: check this works.
            for (OWLSubClassOfAxiom ax : sourceOntology.getSubClassAxiomsForSuperClass(cls)) {
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
            man.addAxioms(subOntology, sourceOntology.getSubClassAxiomsForSubClass((OWLClass)cls));
        }
    }

    private void addPropertyInclusions() {
        System.out.println("Adding role inclusion axioms to subontology.");
        Set<OWLAxiom> roleInclusions = new HashSet<OWLAxiom>();
        Set<OWLObjectProperty> propertiesInSignature = subOntology.getObjectPropertiesInSignature();
        for (OWLAxiom ax : sourceOntology.getRBoxAxioms(Imports.fromBoolean(false))) {
            if (!Collections.disjoint(ax.getSignature(), propertiesInSignature)) {
                roleInclusions.add(ax);
            }
        }
        System.out.println("total role inclusions added: " + roleInclusions.size());
        man.addAxioms(subOntology, roleInclusions);
    }

    private void addGrouperClasses() {
        //TODO: is there a cheaper way to extract these from SCT?
        Set<OWLClass> topLevelSCTGroupers = new HashSet<OWLClass>();
        for(OWLSubClassOfAxiom ax: sourceOntology.getSubClassAxiomsForSuperClass(sctTop)) {
            OWLClassExpression subClass = ax.getSubClass();
            //System.out.println("subClass: " + subClass);
            if(subClass instanceof OWLClass) { // TODO: fix
                topLevelSCTGroupers.add((OWLClass) ax.getSubClass());
            }
        }
        topLevelSCTGroupers.remove(sctTop);
        for(OWLClass cls:topLevelSCTGroupers) {
            if(!Collections.disjoint(subOntology.getClassesInSignature(), sourceOntologyReasoningService.getDescendantClasses(cls))) {
                System.out.println("Added additional grouper class: " + cls);
                man.addAxiom(subOntology, df.getOWLSubClassOfAxiom(cls, sctTop));
            }
        }
    }

    private void addTopLevelClasses() {
        //add cls <= topClass for all "top-level" classes //TODO: currently hardcoded to SCT.
        System.out.println("Adding top level classes.");
        Set<OWLClass> classesUsedInSubontology = subOntology.getClassesInSignature();
        classesUsedInSubontology.remove(sctTop);

        for(OWLClass cls:classesUsedInSubontology) { //TODO: inefficient top-down, but no link to df.getOWLThing() yet?
            List<OWLClass> parentClasses = new ArrayList<OWLClass>(sourceOntologyReasoningService.getParentClasses(cls));
            if(Collections.disjoint(parentClasses, classesUsedInSubontology)
                    && subOntology.getSubClassAxiomsForSubClass(cls).isEmpty()
                    && subOntology.getEquivalentClassesAxioms(cls).isEmpty()) {
                man.addAxiom(subOntology, df.getOWLSubClassOfAxiom(cls, sctTop));
            }
        }
    }

    //11-01-21 -- temp
    private void addTopLevelRoles() {
        System.out.println("Adding top level roles.");
        for(OWLObjectProperty prop:subOntology.getObjectPropertiesInSignature()) {
            if(subOntology.getObjectSubPropertyAxiomsForSubProperty(prop).isEmpty()) {
                man.addAxiom(subOntology, df.getOWLSubObjectPropertyOfAxiom(prop, df.getOWLObjectProperty(IRI.create(snomedIRIString + "762705008"))));
            }
        }
    }

    //TODO: should classesUsed be subontology + nnf classes, or just subontology (authoring form) classes?
    //TODO: determine, do we really want to add atomic class hierarchy for all of the defined concepts in the subontology?
    //currently, do not add hierarchy for PVs, classes outside subontology or classes that have already been defined
    //the latter can be inferred within the subontology, so no explicit statement needed.
    private void addSupportingClassesInformation() throws ReasonerException, OWLOntologyCreationException {
        //TODO: which approach?
        //addPartialSupportingClassDefinitions();
        ArrayList<OWLClass> supportingClasses = new ArrayList<OWLClass>(subOntology.getClassesInSignature());
        supportingClasses.removeAll(focusClasses);

        ListIterator<OWLClass> supportingClassIterator = supportingClasses.listIterator();
        List<OWLClass> additionalSupportingClasses = new ArrayList<OWLClass>();
        Set<OWLAxiom> additionalClassDefinitions = new HashSet<OWLAxiom>();

        //Set<OWLClass> currentlyDefinedClasses = new HashSet<OWLClass>(focusClasses);
        while(supportingClassIterator.hasNext()) {
            OWLClass suppCls = supportingClassIterator.next();

            //TODO: add option to add supporting defs for defined only, or primitive & defined?
            Set<OWLClass> descendents = sourceOntologyReasoningService.getDescendantClasses(suppCls); //TODO: descendents, or children? CHECK
            //Set<OWLClass> descendents = sourceOntologyReasoningService.getChildClasses(suppCls);
            //if the supporting class has a focus class as a descendant, add the full definition
            //if(!sourceOntologyReasoningService.isPrimitive(suppCls)) {
                if (!Collections.disjoint(focusClasses, descendents)) {
                    //Set<OWLEquivalentClassesAxiom> suppClsDefinitions = sourceOntology.getEquivalentClassesAxioms(suppCls);
                    Set<OWLAxiom> suppClsDefinitions = new HashSet<OWLAxiom>();
                    suppClsDefinitions.addAll(sourceOntology.getSubClassAxiomsForSubClass(suppCls));
                    suppClsDefinitions.addAll(sourceOntology.getEquivalentClassesAxioms(suppCls)); //both?

                    definedSupportingClasses.add(suppCls);

                    System.out.println("Adding definitions for supporting class: " + suppCls.toString() + " (" + suppClsDefinitions.size() + " axioms)");
                    for (OWLAxiom ax : suppClsDefinitions) {
                        Set<OWLClass> defClasses = ax.getClassesInSignature();
                        for (OWLClass defClass : defClasses) {
                            if (!subOntology.getClassesInSignature().contains(defClass) && !definedSupportingClasses.contains(defClass)) {
                                supportingClassIterator.add(defClass);
                                additionalSupportingClasses.add(defClass);
                                supportingClassIterator.previous();
                            }
                        }
                    }
                    additionalClassDefinitions.addAll(suppClsDefinitions);
                }
           // }
        }
        man.addAxioms(subOntology, additionalClassDefinitions);

        additionalClassesInExpandedSignature = additionalSupportingClasses;
        additionalSupportingClassDefinitionsAdded = additionalClassDefinitions;
        System.out.println("Supporting class definitions added " + additionalSupportingClasses.size() + " new classes.");
        System.out.println("Added classes: " + additionalSupportingClasses);
    }

    private void addAtomicClassHierarchy() throws ReasonerException, OWLOntologyCreationException {
        Set<OWLClass> partiallyDefinedSupportingClasses = subOntology.getClassesInSignature();
        partiallyDefinedSupportingClasses.removeAll(focusClasses);
        partiallyDefinedSupportingClasses.removeAll(definedSupportingClasses);
        partiallyDefinedSupportingClasses.remove(sctTop);

        //TODO: used in checking what is already entailed by subontology (ancestor search), do we need this?
        //OWLOntology subOntologyWithRenamings = namer.returnOntologyWithNamedPropertyValues(subOntology);
        System.out.println("classifying ontology.");
        //OntologyReasoningService subOntologyReasoningService = new OntologyReasoningService(subOntologyWithRenamings);
        OntologyReasoningService subOntologyReasoningService = new OntologyReasoningService(subOntology);

        for (OWLClass cls : partiallyDefinedSupportingClasses) {
            subOntologyReasoningService.classifyOntology(); //TODO: expensive and not best solution. May need to be done as a postprocessing step?
            //TODO:
            //if(subOntology.getEquivalentClassesAxioms(cls).isEmpty() && subOntology.getSubClassAxiomsForSubClass(cls).isEmpty()) {
            Set<OWLClass> sourceOntologyAncestors = sourceOntologyReasoningService.getAncestorClasses(cls);
            Set<OWLClass> subOntologyAncestors = subOntologyReasoningService.getAncestorClasses(cls);
            //reduce ancestor set based on whether or not it is an atomic class in the subontology signature
            Set<OWLClass> namedClassAncestorsInSignature = new HashSet<OWLClass>();
            namedClassAncestorsInSignature.addAll(subOntologyAncestors);

            for (OWLClass ancestor : sourceOntologyAncestors) {
                if (!namer.isNamedPV(ancestor) && subOntology.getClassesInSignature().contains(ancestor) && !subOntologyAncestors.contains(ancestor)) {
                    //remove ancestors that are already covered by axioms in subontology
                    namedClassAncestorsInSignature.add(ancestor);
                }
            }
            //reduce ancestor set based on subsumption
            Set<OWLClass> reducedAncestors = sourceOntologyReasoningService.reduceClassSet(namedClassAncestorsInSignature);

            //reduce ancestor set based on what is already entailed by subontology
            for (OWLClass ancestor : reducedAncestors) {
                if (!subOntologyReasoningService.getAncestorClasses(cls).contains(ancestor)) {
                    man.addAxiom(subOntology, df.getOWLSubClassOfAxiom(cls, ancestor));
                }
            }
        }
    }

    /*
    //TODO: semantically minimal subontologies, not necessarily user-preferred, some overlap with NNFs here, refactor/reuse
    private void addMinimalSupportingClassDefinitions() throws OWLOntologyCreationException {
        Set<OWLClass> supportingClasses = subOntology.getClassesInSignature();
        supportingClasses.removeAll(inputClasses);

        Set<OWLObjectSomeValuesFrom> pvsInSubontology = new HashSet<OWLObjectSomeValuesFrom>();
        PropertyValueNamer subOntologyNamer = new PropertyValueNamer();
        subOntologyNamer.returnOntologyWithNamedPropertyValues(subOntology); //TODO: fix this, add method in namer to extract names for given ontology
        pvsInSubontology.addAll(subOntologyNamer.getNamingPvMap().values());

        Set<OWLObjectSomeValuesFrom> pvsInSubOntology = new HashSet<OWLObjectSomeValuesFrom>();
        //TODO: reuse renamer code?
        for(OWLClass cls:supportingClasses) {
            if(cls.toString().contains("128139000")) {
                System.out.println("Supporting class: " + cls.toString());
            }
            Set<OWLClass> ancestorClassesInSignature = new HashSet<OWLClass>();
            Set<OWLClass> ancestorPVsInSignature = new HashSet<OWLClass>();
            for (OWLClass ancestor : sourceOntologyReasoningService.getAncestorClasses(cls)) {
                if (subOntology.getClassesInSignature().contains(ancestor)) {
                    if(cls.toString().contains("128139000")) {
                        System.out.println("Parent: " + ancestor.toString());
                    }
                    ancestorClassesInSignature.add(ancestor);
                }
                else if(namer.isNamedPV(ancestor)) {
                    OWLObjectSomeValuesFrom pv = namer.getNamingPvMap().get(ancestor); //
                    if(cls.toString().contains("128139000")) {
                        System.out.println("parent pv: " + pv.toString());
                    }
                    if(pvsInSubOntology.contains(pv)) {
                        if(cls.toString().contains("128139000")) {
                            System.out.println("parent pv added");
                        }
                        ancestorPVsInSignature.add(ancestor);
                    }
                }
            }
            //TODO: reduce named & pvs? Or as separate sets, as with NNF?
            Set<OWLClass> reducedParentClasses= sourceOntologyReasoningService.reduceClassSet(ancestorClassesInSignature);
            Set<OWLObjectSomeValuesFrom> reducedParentPVs = namer.retrievePVsFromNames(sourceOntologyReasoningService.reduceClassSet(ancestorPVsInSignature));

            Set<OWLClassExpression> reducedParents = new HashSet<OWLClassExpression>(reducedParentClasses);
            reducedParents.addAll(reducedParentPVs);
            if(reducedParents.isEmpty()) {
                continue;
            }
            else if(reducedParents.size() == 1) {
                man.addAxiom(subOntology, df.getOWLSubClassOfAxiom(cls, new ArrayList<OWLClassExpression>(reducedParents).get(0)));
                continue;
            }
            else {
                man.addAxiom(subOntology, df.getOWLSubClassOfAxiom(cls, df.getOWLObjectIntersectionOf(reducedParents)));
            }
        }
    }
     */

    private void addAnnotationAssertions() {
        System.out.println("Adding annotation assertion axioms to subontology: ");
        Set<OWLEntity> subOntologyEntities = new HashSet<OWLEntity>();
        subOntologyEntities.addAll(subOntology.getClassesInSignature());
        subOntologyEntities.addAll(subOntology.getObjectPropertiesInSignature()); //TODO: check, subontology or background entities?
        //TODO: are nnf entities needed?
        subOntologyEntities.addAll(nnfOntology.getClassesInSignature());
        subOntologyEntities.addAll(nnfOntology.getObjectPropertiesInSignature());
        Set<OWLAnnotationAssertionAxiom> annotationAssertionAxioms = new HashSet<OWLAnnotationAssertionAxiom>();
        for (OWLEntity ent : subOntologyEntities) {
            for (OWLAnnotationAssertionAxiom as : sourceOntology.getAnnotationAssertionAxioms(ent.getIRI())) {
                if ("skos:prefLabel".equals(as.getProperty().toString()) || "rdfs:label".equals(as.getProperty().toString())) {
                    annotationAssertionAxioms.add(as);
                }
            }
        }

        System.out.println("total annotation assertions added: " + annotationAssertionAxioms.size());
        man.addAxioms(subOntology, annotationAssertionAxioms);
    }

    private static Set<Long> extractAllEntityIDsForOntology(OWLOntology ont) {
        Set<OWLEntity> entitiesInOnt = new HashSet<OWLEntity>();
        entitiesInOnt.addAll(ont.getClassesInSignature());
        entitiesInOnt.addAll(ont.getObjectPropertiesInSignature()); //TODO: only need classes and properties?

        Set<Long> entityIDs = new HashSet<Long>();
        for(OWLEntity ent:entitiesInOnt) {
            String entIDString = ent.toStringID().replace("http://snomed.info/id/","");
            Long entID = Long.valueOf(entIDString);
            entityIDs.add(entID);
        }
        return entityIDs;
    }

    private void renamePVsAndClassify() throws OWLOntologyCreationException, ReasonerException {
        namer = new PropertyValueNamer();
        OWLOntology backgroundOntologyWithRenamings = namer.returnOntologyWithNamedPropertyValues(sourceOntology);
        sourceOntologyReasoningService = new OntologyReasoningService(backgroundOntologyWithRenamings);
        sourceOntologyReasoningService.classifyOntology();
    }

    public Set<OWLClass> readRefset(String refsetPath) {
        if(refsetPath.substring(refsetPath.lastIndexOf(".")).equals("json")) {
            return readRefsetJson(refsetPath);
        }
        return readRefsetTxt(refsetPath);
    }

    private Set<OWLClass> readRefsetJson(String refsetPath) {
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

    private Set<OWLClass> readRefsetTxt(String refsetPath) {
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

    public OWLOntology getSubOntology() {
        return subOntology;
    }

    public OWLOntology getNnfOntology() {
        return nnfOntology;
    }

    public Set<OWLClass> getSupportingClassesWithAddedDefinitions() {return definedSupportingClasses;}
    public int getNumberOfClassesAddedDuringSignatureExpansion() { return additionalClassesInExpandedSignature.size();}
    public int getNumberOfAdditionalSupportingClassDefinitionsAdded() {return additionalSupportingClassDefinitionsAdded.size();}

}
