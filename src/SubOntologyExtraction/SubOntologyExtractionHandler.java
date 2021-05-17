package SubOntologyExtraction;

import Classification.OntologyReasoningService;
import DefinitionGeneration.DefinitionGenerator;
import DefinitionGeneration.DefinitionGeneratorAbstract;
import DefinitionGeneration.DefinitionGeneratorNNF;
import DefinitionGeneration.RedundancyOptions;
import ExceptionHandlers.ReasonerException;
import NamingApproach.IntroducedNameHandler;
import ResultsWriters.OntologySaver;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.structural.StructuralReasoner;
import tools.InputSignatureHandler;
import tools.ModuleExtractionHandler;
import uk.ac.manchester.cs.owlapi.modularity.ModuleType;

import java.io.File;
import java.util.*;

/*
Produces an extracted subontology for a given background ontology and set of concepts.
Includes
        : abstract (authoring) form definitions for each concept in the input set
        : all role inclusion axioms
        : all GCIs (currently, TODO: downward definitions option)
        : NNF definitions (nearest parent, non-redundant named classes and PV relationships -- see necessary normal forms)
 */
//note: currently, this process computes the abstract definitions and NNF definitions entirely separately. Can we combine these somehow?
//      thought: maybe not, since if A <= B and C and PV1, B <= P1, C <= P2 then if B <= C, NNF will be A <= B and PV1.
//      however, in abstract form: B <= C does not imply P1 <= P2, so cannot necessarily remove them!
public class SubOntologyExtractionHandler {

    private final OWLOntology sourceOntology;
    private OntologyReasoningService sourceOntologyReasoningService;
    private OntologyReasoningService subOntologyReasoningService;
    private final OWLOntologyManager man;
    private final OWLDataFactory df;
    private IntroducedNameHandler sourceOntologyNamer;
    private final Set<OWLAxiom> focusConceptDefinitions;
    private final Set<OWLAxiom> nnfDefinitions;
    private Set<OWLClass> focusClasses;
    private OWLOntology subOntology;
    private OWLOntology nnfOntology;
    private final OWLClass sctTop;
    //TODO: temp variable, implement stats handler
    //classes and definitions added during signature expansion
    private final Set<OWLClass> definedSupportingClasses = new HashSet<>();
    private Set<OWLClass> additionalClassesInExpandedSignature = new HashSet<>();
    private final Set<OWLAxiom> additionalSupportingClassDefinitions = new HashSet<>();
    private final DefinitionGenerator abstractDefinitionsGenerator;
    private Set<RedundancyOptions> redundancyOptions;

    public SubOntologyExtractionHandler(OWLOntology backgroundOntology, Set<OWLClass> inputClasses) throws OWLOntologyCreationException, ReasonerException {
        this.sourceOntology = backgroundOntology;
        this.focusClasses = inputClasses;
        man = backgroundOntology.getOWLOntologyManager();
        df = man.getOWLDataFactory();
        sctTop = df.getOWLClass(IRI.create("http://snomed.info/id/138875005"));

        OWLObjectProperty prop1 = df.getOWLObjectProperty(IRI.create("http://snomed.info/id/363701004"));
        OWLObjectProperty prop2 = df.getOWLObjectProperty(IRI.create("http://snomed.info/id/762951001"));
        man.addAxiom(sourceOntology, df.getOWLSubPropertyChainOfAxiom(Arrays.asList(prop1, prop2), prop1));

        System.out.println("Initialising ELK taxonomy graph.");
        this.renamePVsAndClassify();
        focusConceptDefinitions = new HashSet<>();
        nnfDefinitions = new HashSet<>();

        abstractDefinitionsGenerator = new DefinitionGeneratorAbstract(sourceOntology, sourceOntologyReasoningService, sourceOntologyNamer);
    }

    private void renamePVsAndClassify() throws OWLOntologyCreationException, ReasonerException {
        sourceOntologyNamer = new IntroducedNameHandler(sourceOntology);
        OWLOntology backgroundOntologyWithRenamings = sourceOntologyNamer.returnOntologyWithNamings();
        sourceOntologyReasoningService = new OntologyReasoningService(backgroundOntologyWithRenamings);
        sourceOntologyReasoningService.classifyOntology();
    }

    public void computeSubontology() throws OWLException, ReasonerException {
        Set<RedundancyOptions> defaultOptions = new HashSet<RedundancyOptions>();
        defaultOptions.add(RedundancyOptions.eliminateLessSpecificRedundancy);
        defaultOptions.add(RedundancyOptions.eliminateReflexivePVRedundancy);
        defaultOptions.add(RedundancyOptions.eliminateRoleGroupRedundancy);
        defaultOptions.add(RedundancyOptions.eliminateSufficientProximalGCIs);
        this.computeSubontology(defaultOptions);
    }

    //TODO: refactor, split
    //TODO: a lot of redundancy, fine for testing purposes but needs streamlining.
    public void computeSubontology(Set<RedundancyOptions> inputRedundancyOptions) throws OWLException, ReasonerException {
        redundancyOptions = inputRedundancyOptions;
        //Compute initial abstract (authoring) form definitions for focus classes
        computeFocusClassDefinitions();

        //include authoring definitions, role inclusions (rbox?), GCIs for defined classes
        populateSubOntology();

        //Compute NNFs
        computeNNFDefinitions(subOntology.getClassesInSignature(), redundancyOptions);
        nnfOntology = man.createOntology(nnfDefinitions);

        //add necessary metadata
        //add relevant annotation axioms
        addAnnotationAssertions();
    }

    private void computeFocusClassDefinitions() {
        System.out.println("Computing authoring form.");
        focusClasses.remove(df.getOWLNothing());
        for(OWLClass cls:focusClasses) {
            abstractDefinitionsGenerator.generateDefinition(cls, redundancyOptions);
        }
        focusConceptDefinitions.addAll(abstractDefinitionsGenerator.getGeneratedDefinitions());
    }

    private void populateSubOntology() throws OWLOntologyCreationException, ReasonerException {
        subOntology = man.createOntology(focusConceptDefinitions);
        //TODO: check ordering of these steps.

        //addInitialGCIAxioms(); //TODO: needed with latest update?

        //definition expansion loop
        computeRequiredSupportingClassDefinitions();

        //TODO: used in checking what is already entailed by subontology (ancestor search), do we need this?
        //OWLOntology subOntologyWithRenamings = namer.returnOntologyWithNamedPropertyValues(subOntology);
        System.out.println("classifying ontology.");
        subOntologyReasoningService = new OntologyReasoningService(subOntology);
        subOntologyReasoningService.classifyOntology();

        //add property inclusion axioms for properties
        populateRBox();

        //add grouper classes
        addGrouperClasses();

        //add atomic class hierarchy where needed. Classes in the focus set, or classes whose definitions have been added, do not need to be considered here.
        addAtomicClassHierarchy();

        //add top level classes
        addTopLevelClasses();

        //add top level roles
        //addTopLevelRoles();

        //TODO: temp printing, get stats handler and remove.
        System.out.println("Added classes: " + additionalClassesInExpandedSignature);
    }

    private void addInitialGCIAxioms() {
        //general layout: check if class in subontology signature has GCI axioms associated
        //OR
        //if there are any classes in the subontology such that they are a subclass of a GCI (RHS) concept e.g. A <= ... <= B, P1 and R some C <= B ??
        Set<OWLClass> namedGCIs = sourceOntologyNamer.retrieveAllNamesForGCIs();
        for(OWLClass name:namedGCIs) {
            //if(!Collections.disjoint(sourceOntologyReasoningService.getDescendants(name, true), focusClasses)) {
            if(!Collections.disjoint(sourceOntologyReasoningService.getDescendants(name), focusClasses)) {
                abstractDefinitionsGenerator.generateDefinition(name, redundancyOptions);
                man.addAxiom(subOntology, df.getOWLSubClassOfAxiom(df.getOWLObjectIntersectionOf(abstractDefinitionsGenerator.getLatestNecessaryConditions()),
                                                      sourceOntologyNamer.retrieveSuperClassFromNamedGCI(name)));
            }
        }
    }

    private void computeRequiredSupportingClassDefinitions() {
        List<OWLClass> expressionsToCheck = new ArrayList<>();

        //concepts to be checked - all supporting concepts that are ancestors of focus concepts
        Set<OWLClass> supportingClasses = new HashSet<>(subOntology.getClassesInSignature());
        supportingClasses.removeAll(focusClasses);
        for(OWLClass cls:supportingClasses) {
            if(!Collections.disjoint(focusClasses, sourceOntologyReasoningService.getDescendants(cls))) {
                expressionsToCheck.add(cls);
            }
        }

        //pvs to be checked: initialise as the set of expressions that are ancestors of focus concepts
        //this includes A <= R some C as well as A <= RG some (R some C and S some D), which can be expanded out during loop.
        for(OWLClassExpression exp:subOntology.getNestedClassExpressions()) {
            if(exp instanceof OWLObjectSomeValuesFrom) {
                OWLClass pvName = sourceOntologyNamer.retrieveNameForPV((OWLObjectSomeValuesFrom)exp);
                if(!Collections.disjoint(sourceOntologyReasoningService.getDescendants(pvName), focusClasses)) {
                    //TODO: 09/04/21 this check not needed? All PVs at this point will be in authoring defs of focus concepts anyway.
                    expressionsToCheck.add(pvName);
                }
            }
        }

        ListIterator<OWLClass> checkingIterator = expressionsToCheck.listIterator();
        Set<OWLClass> additionalSupportingClasses = new HashSet<>();

        //check for each supporting class & pv if a definition is required
        while(checkingIterator.hasNext()) {
            OWLClass clsBeingChecked = checkingIterator.next();
            boolean newDefinitionAdded = false;
            System.out.println("Checking required definition status for class: " + clsBeingChecked);
            if(sourceOntologyNamer.isNamedPV(clsBeingChecked)) { //pv case
                //add filler as defined supporting class
                OWLObjectSomeValuesFrom pv = sourceOntologyNamer.retrievePVForName(clsBeingChecked);
                if(pv.getFiller() instanceof OWLClass) {
                    //System.out.println("Filler is class");
                    //generate authoring form for filler class + check role chain requirement
                    abstractDefinitionsGenerator.generateDefinition((OWLClass) pv.getFiller(), redundancyOptions);
                    if(supportingDefinitionRequired(pv, abstractDefinitionsGenerator.getLatestNecessaryConditions())) {
                        definedSupportingClasses.add((OWLClass) pv.getFiller());
                        //additionalSupportingClassDefinitions.add(abstractDefinitionsGenerator.getLastDefinitionGenerated());
                        newDefinitionAdded = true;
                    }
                }
                else if(pv.getFiller() instanceof OWLObjectSomeValuesFrom) {
                    //System.out.println("Filler is R some");
                    checkingIterator.add(sourceOntologyNamer.retrieveNameForPV((OWLObjectSomeValuesFrom) pv.getFiller()));
                    checkingIterator.previous();
                }
                else if(pv.getFiller() instanceof OWLObjectIntersectionOf) {
                    //System.out.println("Filler is RG some");
                    for(OWLClassExpression conj:pv.getFiller().asConjunctSet()) {
                        if(conj instanceof OWLClass) {
                            //break into separate cases to be checked
                            checkingIterator.add((OWLClass) conj);
                            checkingIterator.previous();
                        }
                        else if(conj instanceof OWLObjectSomeValuesFrom) {
                            checkingIterator.add(sourceOntologyNamer.retrieveNameForPV((OWLObjectSomeValuesFrom) conj));
                            checkingIterator.previous();
                        }
                    }
                }
            }
            else { //non-pv case
                //add supporting class to list of defined supporting classes
                if(supportingDefinitionRequired(clsBeingChecked)) {
                    definedSupportingClasses.add(clsBeingChecked);

                    //generate and add authoring form def for cls, + iterate
                    abstractDefinitionsGenerator.generateDefinition(clsBeingChecked, redundancyOptions);
                    //additionalSupportingClassDefinitions.add(abstractDefinitionsGenerator.getLastDefinitionGenerated());
                    newDefinitionAdded = true;
                }
            }

            //add any new classes to the set to be checked, do the same for PVs. Add RBox axioms where necessary.
            //new classes
            if(newDefinitionAdded) {
                //definition generated for clsBeingChecked in loop above.
                System.out.println("latest def being added: " + abstractDefinitionsGenerator.getLastDefinitionGenerated());
                additionalSupportingClassDefinitions.add(abstractDefinitionsGenerator.getLastDefinitionGenerated());

                //add authoring form for any GCIs associated with newly defined class
                Set<OWLSubClassOfAxiom> addedGCIs = addSupportingConceptGCIs(clsBeingChecked);
                additionalSupportingClassDefinitions.addAll(addedGCIs);

                //check for new classes / expressions in the new definition (and GCIs if applicable)
                Set<OWLClassExpression> expressionsInNewDefinition = abstractDefinitionsGenerator.getLatestNecessaryConditions();
                for(OWLSubClassOfAxiom gci:addedGCIs) {
                    expressionsInNewDefinition.addAll(gci.getSubClass().asConjunctSet());
                }

                for (OWLClassExpression defExp : expressionsInNewDefinition) {
                    //new classes
                    if (defExp instanceof OWLClass) {
                        if (!subOntology.getClassesInSignature().contains(defExp) && !definedSupportingClasses.contains(defExp)) { //TODO: should be ancestor of focus concept?
                            checkingIterator.add((OWLClass) defExp);
                            additionalSupportingClasses.add((OWLClass) defExp);
                            checkingIterator.previous();
                        }
                    }
                    //new pvs
                    else if (defExp instanceof OWLObjectSomeValuesFrom) { //TODO: should be ancestor of focus concept?
                        OWLClass pvName = sourceOntologyNamer.retrieveNameForPV((OWLObjectSomeValuesFrom)defExp);
                        if (!Collections.disjoint(sourceOntologyReasoningService.getDescendants(pvName), focusClasses)) {
                            checkingIterator.add(pvName);
                            checkingIterator.previous();
                        }
                    }
                }
            }
        }

        //add new definitions to subontology
        man.addAxioms(subOntology, additionalSupportingClassDefinitions);
        additionalClassesInExpandedSignature = additionalSupportingClasses;
        System.out.println("Supporting class definitions added " + additionalSupportingClassDefinitions.size());
        System.out.println("Number of classes added as result of expansion: " + additionalSupportingClasses.size());
        System.out.println("Added classes: " + additionalSupportingClasses);
    }

    //Expansion rule 1
    private boolean supportingDefinitionRequired(OWLClass cls) {
        return !Collections.disjoint(sourceOntologyReasoningService.getDescendants(cls), focusClasses);
    }

    //Expansion rule 2
    private boolean supportingDefinitionRequired(OWLObjectSomeValuesFrom pv, Set<OWLClassExpression> fillerNecessaryConditions) {
        boolean definitionRequired = false;
        Set<OWLObjectPropertyExpression> topLevelPropertiesInFillerDefinition = new HashSet<>();
        for(OWLClassExpression exp:fillerNecessaryConditions) {
            if(exp instanceof OWLObjectSomeValuesFrom) {
                topLevelPropertiesInFillerDefinition.add(((OWLObjectSomeValuesFrom) exp).getProperty());
            }
        }
        for(OWLSubPropertyChainOfAxiom chainAx:sourceOntology.getAxioms(AxiomType.SUB_PROPERTY_CHAIN_OF)) {
            if(chainAx.getSuperProperty().equals(pv.getProperty())) {
                Set<OWLObjectPropertyExpression> otherPropertiesInChain = new HashSet<>(chainAx.getPropertyChain());
                otherPropertiesInChain.remove(pv.getProperty());
                //r o s <= r case
                if(!Collections.disjoint(topLevelPropertiesInFillerDefinition, otherPropertiesInChain)) {
                    definitionRequired = true;
                }
                //r o r <= r case
                else if(!sourceOntology.getTransitiveObjectPropertyAxioms(pv.getProperty()).isEmpty() && topLevelPropertiesInFillerDefinition.contains(pv.getProperty())) {
                    definitionRequired = true;
                }
            }
        }
        return definitionRequired;
    }

    //TODO: improve generation procedure in DefinitionGeneratorAbstract - separate method for GCIs?
    private Set<OWLSubClassOfAxiom> addSupportingConceptGCIs(OWLClass suppCls) {
        Set<OWLSubClassOfAxiom> associatedGCIs = new HashSet<OWLSubClassOfAxiom>();
        if(sourceOntologyNamer.hasAssociatedGCIs(suppCls)) {
            System.out.println("GCI AXIOMS FOR DEFINED SUPP CLS: " + suppCls);
            Set<OWLClass> gciNames = sourceOntologyNamer.returnNamesOfGCIsForSuperConcept(suppCls);
            System.out.println("GCINAMES: " + gciNames);
            for(OWLClass gciName:gciNames) {
                abstractDefinitionsGenerator.generateDefinition(gciName, redundancyOptions);
                associatedGCIs.add(df.getOWLSubClassOfAxiom(df.getOWLObjectIntersectionOf(abstractDefinitionsGenerator.getLatestNecessaryConditions()),suppCls));
            }
        }
        return associatedGCIs;
    }

    /*
    private void populateRBox() {
        Set<OWLObjectProperty> subOntologyRoles = subOntology.getObjectPropertiesInSignature();

        //currently: add all RBox axioms for object properties in the subontology (post definition expansion)
        /*
        Set<OWLAxiom> roleAxioms = new HashSet<>();
        for (OWLAxiom ax : sourceOntology.getRBoxAxioms(Imports.fromBoolean(false))) {
            //if (!Collections.disjoint(ax.getObjectPropertiesInSignature(), subOntologyProperties)) {
            if(subOntologyRoles.containsAll(ax.getObjectPropertiesInSignature())) {
                roleAxioms.add(ax);
            }
        }
    }
     */
    //temp: compute STAR module for RBox population
    private void populateRBox() throws OWLOntologyCreationException {
        Set<OWLEntity> signature = new HashSet<OWLEntity>();
        signature.addAll(subOntology.getObjectPropertiesInSignature());

        OWLOntology module = ModuleExtractionHandler.extractSingleModule(sourceOntology, signature, ModuleType.STAR);

        System.out.println("Module axioms: " + module.getAxioms());
        System.out.println("Module classes: " + module.getClassesInSignature());
        System.out.println("Module roles: " + module.getObjectPropertiesInSignature());

        man.addAxioms(subOntology, module.getLogicalAxioms());

        //TODO: does RBox check automatically include transitive & reflexive axioms?
        /*
        for(OWLObjectProperty prop:subOntology.getObjectPropertiesInSignature()) {
            man.addAxioms(subOntology, sourceOntology.getTransitiveObjectPropertyAxioms(prop));
            man.addAxioms(subOntology, sourceOntology.getReflexiveObjectPropertyAxioms(prop));
        }
         */
    }

    private void addGrouperClasses() {
        Set<OWLClass> topLevelSCTGroupers = new HashSet<>();
        for(OWLSubClassOfAxiom ax: sourceOntology.getSubClassAxiomsForSuperClass(sctTop)) {
            OWLClassExpression subClass = ax.getSubClass();
            //System.out.println("subClass: " + subClass);
            if(subClass instanceof OWLClass) { // TODO: check
                topLevelSCTGroupers.add((OWLClass) ax.getSubClass());
            }
        }
        topLevelSCTGroupers.remove(sctTop);
        System.out.println("Adding additional grouper concepts (beneath SCT top)");
        for(OWLClass cls:topLevelSCTGroupers) {
            if(!Collections.disjoint(subOntology.getClassesInSignature(), sourceOntologyReasoningService.getDescendants(cls))) {
                man.addAxiom(subOntology, df.getOWLSubClassOfAxiom(cls, sctTop));
            }
        }
    }

    private void addTopLevelClasses() {
        //add cls <= topClass for all "top-level" classes //currently hardcoded to SCT.
        System.out.println("Adding top level classes.");
        Set<OWLClass> classesUsedInSubontology = subOntology.getClassesInSignature();
        classesUsedInSubontology.remove(sctTop);

        for(OWLClass cls:classesUsedInSubontology) { //TODO: inefficient top-down, but no link to OWLThing yet?
            List<OWLClass> parentClasses = new ArrayList<OWLClass>(sourceOntologyReasoningService.getDirectAncestors(cls));
            if(Collections.disjoint(parentClasses, classesUsedInSubontology)
                    && subOntology.getSubClassAxiomsForSubClass(cls).isEmpty()
                    && subOntology.getEquivalentClassesAxioms(cls).isEmpty()) {
                man.addAxiom(subOntology, df.getOWLSubClassOfAxiom(cls, sctTop));
            }
        }
    }

    private void addAtomicClassHierarchy() throws ReasonerException {
        Set<OWLClass> partiallyDefinedSupportingClasses = subOntology.getClassesInSignature();
        partiallyDefinedSupportingClasses.removeAll(focusClasses);
        partiallyDefinedSupportingClasses.removeAll(definedSupportingClasses);
        partiallyDefinedSupportingClasses.remove(sctTop);

        for (OWLClass cls : partiallyDefinedSupportingClasses) {
            //subOntologyReasoningService.classifyOntology(); //TODO: expensive. Do we need to do this at every class or just once?
            //if(subOntology.getEquivalentClassesAxioms(cls).isEmpty() && subOntology.getSubClassAxiomsForSubClass(cls).isEmpty()) {
            Set<OWLClass> sourceOntologyAncestors = sourceOntologyReasoningService.getAncestors(cls);
            Set<OWLClass> subOntologyAncestors = subOntologyReasoningService.getAncestors(cls);

            //reduce ancestor set based on whether or not it is an atomic class in the subontology signature
            Set<OWLClass> namedClassAncestorsInSignature = new HashSet<>(subOntologyAncestors);

            for (OWLClass ancestor : sourceOntologyAncestors) {
                //TODO: 23-04-2021, third condition not needed?
                if (!sourceOntologyNamer.isNamedPV(ancestor) && subOntology.getClassesInSignature().contains(ancestor) && !subOntologyAncestors.contains(ancestor)) {
                    //remove ancestors that are already covered by axioms in subontology
                    namedClassAncestorsInSignature.add(ancestor);
                }
            }
            //reduce ancestor set based on subsumption
            Set<OWLClass> reducedAncestors = sourceOntologyReasoningService.eliminateWeakerClasses(namedClassAncestorsInSignature);

            //reduce ancestor set based on what is already entailed by subontology
            Set<OWLClass> nonRedundantAncestors = new HashSet<OWLClass>();
            for (OWLClass ancestor : reducedAncestors) {
                //TODO: 23-04-2021, this check not needed? DOUBLE CHECK.
                if (!subOntologyReasoningService.getAncestors(cls).contains(ancestor)) {
                    //man.addAxiom(subOntology, df.getOWLSubClassOfAxiom(cls, ancestor));
                    nonRedundantAncestors.add(ancestor);
                }
            }
            if(!nonRedundantAncestors.isEmpty()) {
                if(nonRedundantAncestors.size() == 1) {
                    man.addAxiom(subOntology, df.getOWLSubClassOfAxiom(cls, new ArrayList<OWLClass>(nonRedundantAncestors).get(0)));
                }
                else {
                    man.addAxiom(subOntology, df.getOWLSubClassOfAxiom(cls, df.getOWLObjectIntersectionOf(nonRedundantAncestors)));
                }
            }
        }
    }

    /*
    //TODO: semantically minimal subontologies, not necessarily user-preferred, some overlap with NNFs here, refactor/reuse
    private void addMinimalSupportingClassDefinitions() throws OWLOntologyCreationException {
    }
     */

    private void computeNNFDefinitions(Set<OWLClass> classes, Set<RedundancyOptions> redundancyOptions) throws ReasonerException, OWLOntologyCreationException {
        //TODO: 08-04-21 check if using separate namer to sourceOntologyNamer works as intended.
        System.out.println("Computing necessary normal form (inferred relationships).");
        IntroducedNameHandler subOntologyNamer = new IntroducedNameHandler(subOntology);
        OWLOntology subOntologyWithNamings = subOntologyNamer.returnOntologyWithNamings();
        OntologyReasoningService subOntologyReasoningService = new OntologyReasoningService(subOntologyWithNamings);
        subOntologyReasoningService.classifyOntology();
        DefinitionGenerator nnfDefinitionsGenerator = new DefinitionGeneratorNNF(subOntology, subOntologyReasoningService, subOntologyNamer);

        classes.remove(df.getOWLNothing());
        for(OWLClass cls:classes) {
            nnfDefinitionsGenerator.generateDefinition(cls, redundancyOptions);
        }
        nnfDefinitions.addAll(nnfDefinitionsGenerator.getGeneratedDefinitions());
    }

    private void addAnnotationAssertions() {
        System.out.println("Adding annotation assertion axioms to subontology: ");
        Set<OWLEntity> subOntologyEntities = new HashSet<>();

        subOntologyEntities.addAll(subOntology.getClassesInSignature());
        subOntologyEntities.addAll(subOntology.getObjectPropertiesInSignature());

        subOntologyEntities.addAll(nnfOntology.getClassesInSignature());
        subOntologyEntities.addAll(nnfOntology.getObjectPropertiesInSignature());
        Set<OWLAnnotationAssertionAxiom> annotationAssertionAxioms = new HashSet<>();
        for (OWLEntity ent : subOntologyEntities) {
            annotationAssertionAxioms.addAll(sourceOntology.getAnnotationAssertionAxioms(ent.getIRI()));
        }

        System.out.println("total annotation assertions added: " + annotationAssertionAxioms.size());
        man.addAxioms(subOntology, annotationAssertionAxioms);

        System.out.println("Annotating concept types.");
        for(OWLClass cls:subOntology.getClassesInSignature()) {
            OWLAnnotation anno;
            if(focusClasses.contains(cls)) {
                anno = df.getOWLAnnotation(df.getRDFSComment(), df.getOWLLiteral("Focus concept"));
            }
            else if(definedSupportingClasses.contains(cls)) {
                anno = df.getOWLAnnotation(df.getRDFSComment(), df.getOWLLiteral("Supporting concept (with definition)"));
            }
            else {
                anno = df.getOWLAnnotation(df.getRDFSComment(), df.getOWLLiteral("Supporting concept"));
            }

            OWLAnnotationAssertionAxiom as = df.getOWLAnnotationAssertionAxiom(cls.getIRI(), anno);
            man.addAxiom(subOntology, as);
        }

    }

    private static Set<Long> extractAllEntityIDsForOntology(OWLOntology ont) {
        Set<OWLEntity> entitiesInOnt = new HashSet<>();
        entitiesInOnt.addAll(ont.getClassesInSignature());
        entitiesInOnt.addAll(ont.getObjectPropertiesInSignature());

        Set<Long> entityIDs = new HashSet<>();
        for(OWLEntity ent:entitiesInOnt) {
            String entIDString = ent.toStringID().replace("http://snomed.info/id/","");
            Long entID = Long.valueOf(entIDString);
            entityIDs.add(entID);
        }
        return entityIDs;
    }

    public OWLOntology getNnfOntology() {return nnfOntology;}
    public OWLOntology getCurrentSubOntology() {return subOntology;}
    public Set<OWLClass> getSupportingClassesWithAddedDefinitions() {return definedSupportingClasses;}
    public int getNumberOfClassesAddedDuringSignatureExpansion() { return additionalClassesInExpandedSignature.size();}
    public int getNumberOfAdditionalSupportingClassDefinitionsAdded() {return additionalSupportingClassDefinitions.size();}
    public Set<OWLClass> getFocusClasses() {return focusClasses;}

    //public void setFocusClasses(Set<OWLClass> newFocusClasses) {focusClasses = newFocusClasses;}
    //public void resetSubOntology(){man.removeOntology(subOntology);}

}
