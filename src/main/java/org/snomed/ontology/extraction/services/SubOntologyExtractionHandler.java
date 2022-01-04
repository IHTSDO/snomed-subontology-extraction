package org.snomed.ontology.extraction.services;

import org.snomed.ontology.extraction.classification.OntologyReasoningService;
import org.snomed.ontology.extraction.definitiongeneration.DefinitionGenerator;
import org.snomed.ontology.extraction.definitiongeneration.DefinitionGeneratorAbstract;
import org.snomed.ontology.extraction.definitiongeneration.DefinitionGeneratorNNF;
import org.snomed.ontology.extraction.definitiongeneration.RedundancyOptions;
import org.snomed.ontology.extraction.exception.ReasonerException;
import org.snomed.ontology.extraction.naming.IntroducedNameHandler;
import org.semanticweb.owlapi.model.*;
import org.snomed.ontology.extraction.tools.ModuleExtractionHandler;
import uk.ac.manchester.cs.owlapi.modularity.ModuleType;

import java.util.*;

/*
PROTOTYPE subontology extraction tool, IAA Project University of Manchester / SNOMED International
Author: Warren Del-Pinto (warren.del-pinto@manchester.ac.uk)
Produces an extracted subontology for a given background ontology and set of concepts, with the aim of satisfying the following criteria:
    1) All focus (input) concept definitions are equivalent in the source and sub ontologies.
    2) The transitive closure between all concepts in the final subontology is equal (up to the signature of the subontology) in the source and sub ontologies.
Steps outline (see documentation for full details):
        : (i) Compute abstract (authoring) form definitions for each focus concept in the input set (including GCI axioms for these concepts)
        : (ii) Definition expansion: automatically identify required supporting concept definitions to satisfy the above criteria
        : (iii) Populate RBox (currently star module of roles appearing in the definitions added during steps (i) and (ii)
        : (iv) Addition of top-level SCT groupers (later may expand to utilise grouper selection algorithms)
        : (v) Completion of the transitive closure between all concepts in the subontology, using atomic inclusions A1 <= A2 only where necessary
        : (vi) Shrinking of subontology hierarchy (i.e., removal of unnecessary supporting concepts)
        : NNF definitions (nearest parent, non-redundant named classes and PV relationships -- see necessary normal forms)
 */
public class SubOntologyExtractionHandler {

    private final OWLOntologyManager man;
    private final OWLDataFactory df;
    private final OWLOntology sourceOntology;
    private OWLOntology subOntology;
    private OWLOntology nnfOntology;
    private OntologyReasoningService sourceOntologyReasoningService;
    private OntologyReasoningService subOntologyReasoningService;
    private IntroducedNameHandler sourceOntologyNamer;
    private final Set<OWLAxiom> focusConceptDefinitions;
    private final Set<OWLAxiom> nnfDefinitions;
    private final OWLClass sctTop;
    //classes and definitions added during signature expansion
    private Set<OWLClass> focusConcepts;
    private Set<OWLClass> grouperConcepts = new HashSet<>();
    private final Set<OWLClass> definedSupportingConcepts = new HashSet<>();
    private final DefinitionGenerator abstractDefinitionsGenerator;
    private Set<RedundancyOptions> authoringFormRedundancyOptions;
    private Set<RedundancyOptions> nnfRedundancyOptions;

    //TODO: temp variables, implement data handler
    private Set<OWLClass> additionalConceptsInExpandedSignature = new HashSet<>();
    private final Set<OWLAxiom> additionalSupportingConceptDefinitions = new HashSet<>();

    public SubOntologyExtractionHandler(OWLOntology backgroundOntology, Set<OWLClass> inputClasses) throws OWLOntologyCreationException, ReasonerException {
        this.sourceOntology = backgroundOntology;
        this.focusConcepts = inputClasses;
        man = backgroundOntology.getOWLOntologyManager();
        df = man.getOWLDataFactory();
        sctTop = df.getOWLClass(IRI.create("http://snomed.info/id/138875005"));

        System.out.println("Initialising ELK taxonomy graph.");
        this.renamePVsAndClassify();
        focusConceptDefinitions = new HashSet<>();
        nnfDefinitions = new HashSet<>();
        nnfOntology = man.createOntology();

        abstractDefinitionsGenerator = new DefinitionGeneratorAbstract(sourceOntology, sourceOntologyReasoningService, sourceOntologyNamer);
    }

    private void renamePVsAndClassify() throws OWLOntologyCreationException, ReasonerException {
        sourceOntologyNamer = new IntroducedNameHandler(sourceOntology);
        OWLOntology backgroundOntologyWithRenamings = sourceOntologyNamer.returnOntologyWithNamings();
        sourceOntologyReasoningService = new OntologyReasoningService(backgroundOntologyWithRenamings);
        sourceOntologyReasoningService.classifyOntology();
    }

    public void computeSubontology() throws OWLException, ReasonerException {
        Set<RedundancyOptions> defaultOptions = new HashSet<>();
        defaultOptions.add(RedundancyOptions.eliminateLessSpecificRedundancy);
        defaultOptions.add(RedundancyOptions.eliminateReflexivePVRedundancy);
        defaultOptions.add(RedundancyOptions.eliminateRoleGroupRedundancy);
        defaultOptions.add(RedundancyOptions.eliminateSufficientProximalGCIs);
        this.computeSubontology(true, defaultOptions);
    }

    public void computeSubontology(boolean computeRF2) throws OWLException, ReasonerException {
        Set<RedundancyOptions> defaultOptions = new HashSet<>();
        defaultOptions.add(RedundancyOptions.eliminateLessSpecificRedundancy);
        defaultOptions.add(RedundancyOptions.eliminateReflexivePVRedundancy);
        defaultOptions.add(RedundancyOptions.eliminateRoleGroupRedundancy);
        defaultOptions.add(RedundancyOptions.eliminateSufficientProximalGCIs);
        this.computeSubontology(computeRF2, defaultOptions, false);
    }

    public void computeSubontology(boolean computeRF2, Set<RedundancyOptions> inputRedundancyOptions) throws OWLException, ReasonerException {
        this.computeSubontology(computeRF2, inputRedundancyOptions, false);
    }

    public void computeSubontology(boolean computeRF2, Set<RedundancyOptions> inputRedundancyOptions, boolean customAuthoringForm) throws OWLException, ReasonerException {
        authoringFormRedundancyOptions = inputRedundancyOptions;

        //add necessary metadata concepts to concept list.
        if(computeRF2) {
            addMetaConceptsForBrowserRF2();
        }

        if(customAuthoringForm) {
            authoringFormRedundancyOptions = inputRedundancyOptions;
        }
        else {
            Set<RedundancyOptions> defaultOptions = new HashSet<>();
            defaultOptions.add(RedundancyOptions.eliminateLessSpecificRedundancy);
            defaultOptions.add(RedundancyOptions.eliminateReflexivePVRedundancy);
            defaultOptions.add(RedundancyOptions.eliminateRoleGroupRedundancy);
            defaultOptions.add(RedundancyOptions.eliminateSufficientProximalGCIs);
            authoringFormRedundancyOptions = defaultOptions;
        }

        nnfRedundancyOptions = inputRedundancyOptions;

        //Compute initial abstract (authoring) form definitions for focus classes
        computeFocusConceptDefinitions();

        subOntology = man.createOntology(focusConceptDefinitions);

        //add focus concept GCIs
        addFocusConceptGCIAxioms();

        //include authoring definitions, role inclusions (rbox?), GCIs for defined classes
        populateSubOntology();

        if(computeRF2) {
            //generateNNFs();
            generateNNFs(authoringFormRedundancyOptions);
        }

        //add necessary metadata
        //add relevant annotation axioms
        addAnnotationAssertions();
    }

    private void addMetaConceptsForBrowserRF2() {
        //language concepts for description loading
        focusConcepts.add(df.getOWLClass(IRI.create("http://snomed.info/id/900000000000509007")));
        focusConcepts.add(df.getOWLClass(IRI.create("http://snomed.info/id/900000000000508004")));

        //refset concept
        focusConcepts.add(df.getOWLClass(IRI.create("http://snomed.info/id/733073007")));
        focusConcepts.add(df.getOWLClass(IRI.create("http://snomed.info/id/900000000000455006")));
        focusConcepts.add(df.getOWLClass(IRI.create("http://snomed.info/id/900000000000506000")));

        // Attribute hierarchy concepts:
        // 410662002 | Concept model attribute (attribute) |
        focusConcepts.add(df.getOWLClass(IRI.create("http://snomed.info/id/410662002")));
        // 762705008 | Concept model object attribute (attribute) |
        focusConcepts.add(df.getOWLClass(IRI.create("http://snomed.info/id/762705008")));
        // 762706009 | Concept model data attribute (attribute) |
        focusConcepts.add(df.getOWLClass(IRI.create("http://snomed.info/id/762706009")));
    }

    private void computeFocusConceptDefinitions() {
        System.out.println("Computing authoring form.");
        focusConcepts.remove(df.getOWLNothing());
        for(OWLClass cls: focusConcepts) {
            abstractDefinitionsGenerator.generateDefinition(cls, authoringFormRedundancyOptions);
        }
        focusConceptDefinitions.addAll(abstractDefinitionsGenerator.getAllGeneratedDefinitions());
    }

    private void populateSubOntology() throws OWLOntologyCreationException, ReasonerException {
        //definition expansion loop
        computeRequiredSupportingClassDefinitions();

        //add property inclusion axioms for properties
        populateRBox();

        //add grouper classes -- currently only top level groupers (if using grouper selection algorithms, place functionality here
        addGrouperConcepts();

        System.out.println("Classifying subontology for transitive closure completion.");
        subOntologyReasoningService = new OntologyReasoningService(subOntology);
        subOntologyReasoningService.classifyOntology();

        //add atomic class hierarchy where needed. Classes in the focus set, or classes whose definitions have been added, do not need to be considered here.
        completeSubsumptionTransitiveClosure();

        System.out.println("Classifying subontology for hierarchy shrinking.");
        subOntologyReasoningService.classifyOntology(); //currently needing to classify twice. May be possible to make more efficient (all at once, or only use source reasoner).

        //shrinking hierarchy. P1 <= P2 <= P3   where P2 has no conjunctive definition (i.e. just P2 <= P3)
        shrinkAtomicHierarchy();

        System.out.println("Added classes: " + additionalConceptsInExpandedSignature);
    }

    private void addFocusConceptGCIAxioms() {
        //if there are any classes in the subontology such that they are a subclass of a GCI (RHS) concept e.g. A <= ... <= B, P1 and R some C <= B ??
        //OR if the GCI is part of a focus concept definition
        Set<OWLClass> namedGCIs = sourceOntologyNamer.retrieveAllNamesForGCIs();

        Set<OWLClass> gciNamesInFocusConceptDefinitions = new HashSet<>();
        for(OWLClass focusConcept:focusConcepts) {
            System.out.println("Concept: " + focusConcept.toString());
            if(!sourceOntologyNamer.returnNamesOfGCIsForSuperConcept(focusConcept).isEmpty()) {
                System.out.println("Has GCIs.");
                gciNamesInFocusConceptDefinitions.addAll(sourceOntologyNamer.returnNamesOfGCIsForSuperConcept(focusConcept));
            }
        }

        //if GCI is in focus definition, or a focus concept is a descendent of this definition, then compute authoring form of GCI LHS and add.
        for(OWLClass name:namedGCIs) {
            if(!Collections.disjoint(sourceOntologyReasoningService.getDescendants(name), focusConcepts) || gciNamesInFocusConceptDefinitions.contains(name)) {
                abstractDefinitionsGenerator.generateDefinition(name, authoringFormRedundancyOptions);
                man.addAxiom(subOntology, df.getOWLSubClassOfAxiom(df.getOWLObjectIntersectionOf(abstractDefinitionsGenerator.getLatestNecessaryConditions()),
                                                      sourceOntologyNamer.retrieveSuperClassFromNamedGCI(name)));
            }
        }
    }
    
    private void computeRequiredSupportingClassDefinitions() {
        List<OWLClass> expressionsToCheck = new ArrayList<>();

        //concepts to be checked - all supporting concepts that are ancestors of focus concepts
        Set<OWLClass> supportingClasses = new HashSet<>(subOntology.getClassesInSignature());
        supportingClasses.removeAll(focusConcepts);
        for(OWLClass cls:supportingClasses) {
            if(!Collections.disjoint(focusConcepts, sourceOntologyReasoningService.getDescendants(cls))) {
                expressionsToCheck.add(cls);
            }
        }

        //pvs to be checked: initialise as the set of expressions that are ancestors of focus concepts
        //this includes A <= R some C as well as A <= RG some (R some C and S some D), which can be expanded out during loop.
        for(OWLClassExpression exp:subOntology.getNestedClassExpressions()) {
            if(exp instanceof OWLObjectSomeValuesFrom) {
                OWLClass pvName = sourceOntologyNamer.retrieveNameForPV((OWLObjectSomeValuesFrom)exp);
                if(!Collections.disjoint(sourceOntologyReasoningService.getDescendants(pvName), focusConcepts)) {
                    //TODO: this check not needed? All PVs at this point will be in authoring defs of focus concepts anyway.
                    expressionsToCheck.add(pvName);
                }
            }
        }

        ListIterator<OWLClass> checkingIterator = expressionsToCheck.listIterator();
        Set<OWLClass> additionalSupportingClasses = new HashSet<>();

        Set<OWLClass> previouslyChecked = new HashSet<>(expressionsToCheck);
        //check for each supporting class & pv if a definition is required
        while(checkingIterator.hasNext()) {
            OWLClass clsBeingChecked = checkingIterator.next();
            previouslyChecked.add(clsBeingChecked);
            boolean newDefinitionAdded = false;

            if(clsBeingChecked.equals(df.getOWLThing())) {
                continue;
            }

            System.out.println("Checking required definition status for class: " + clsBeingChecked);
            if(sourceOntologyNamer.isNamedPV(clsBeingChecked)) { //pv case
                //add filler as defined supporting class
                OWLObjectSomeValuesFrom pv = sourceOntologyNamer.retrievePVForName(clsBeingChecked);
                if(pv.getFiller() instanceof OWLClass) {
                    //generate authoring form for filler class + check role chain requirement
                    abstractDefinitionsGenerator.generateDefinition((OWLClass) pv.getFiller(), authoringFormRedundancyOptions);
                    if(supportingDefinitionRequired(pv, abstractDefinitionsGenerator.getLatestNecessaryConditions())) {
                        definedSupportingConcepts.add((OWLClass) pv.getFiller());
                        System.out.println("ADDED DEF: " + pv.getFiller());
                        //additionalSupportingClassDefinitions.add(abstractDefinitionsGenerator.getLastDefinitionGenerated());
                        newDefinitionAdded = true;
                    }
                }
                else if(pv.getFiller() instanceof OWLObjectSomeValuesFrom) {
                    checkingIterator.add(sourceOntologyNamer.retrieveNameForPV((OWLObjectSomeValuesFrom) pv.getFiller()));
                    checkingIterator.previous();
                }
                else if(pv.getFiller() instanceof OWLObjectIntersectionOf) {
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
                    definedSupportingConcepts.add(clsBeingChecked);

                    //generate and add authoring form def for cls, + iterate
                    abstractDefinitionsGenerator.generateDefinition(clsBeingChecked, authoringFormRedundancyOptions);
                    //additionalSupportingClassDefinitions.add(abstractDefinitionsGenerator.getLastDefinitionGenerated());
                    newDefinitionAdded = true;
                }
            }

            //add any new classes to the set to be checked, do the same for PVs. Add RBox axioms where necessary.
            //new classes
            if(newDefinitionAdded) {
                //definition generated for clsBeingChecked in loop above.
                System.out.println("latest def being added: " + abstractDefinitionsGenerator.getLastDefinitionGenerated());
                additionalSupportingConceptDefinitions.addAll(abstractDefinitionsGenerator.getLastDefinitionGenerated());

                //add authoring form for any GCIs associated with newly defined class
                Set<OWLSubClassOfAxiom> addedGCIs = addSupportingConceptGCIs(clsBeingChecked);
                additionalSupportingConceptDefinitions.addAll(addedGCIs);

                //check for new classes / expressions in the new definition (and GCIs if applicable)
                Set<OWLClassExpression> expressionsInNewDefinition = abstractDefinitionsGenerator.getLatestNecessaryConditions();
                for(OWLSubClassOfAxiom gci:addedGCIs) {
                    expressionsInNewDefinition.addAll(gci.getSubClass().asConjunctSet());
                }

                for(OWLClass parentCls:sourceOntologyReasoningService.getDirectAncestors(clsBeingChecked)) {
                    if(!previouslyChecked.contains(parentCls)) {
                        checkingIterator.add(parentCls);
                        checkingIterator.previous();
                    }
                }

                for (OWLClassExpression defExp : expressionsInNewDefinition) {
                    //new classes
                    if (defExp instanceof OWLClass) {
                        if (!subOntology.getClassesInSignature().contains(defExp) && !definedSupportingConcepts.contains(defExp)) {
                            checkingIterator.add((OWLClass) defExp);
                            additionalSupportingClasses.add((OWLClass) defExp);
                            checkingIterator.previous();
                        }
                    }
                    //new pvs
                    else if (defExp instanceof OWLObjectSomeValuesFrom) {
                        OWLClass pvName = sourceOntologyNamer.retrieveNameForPV((OWLObjectSomeValuesFrom)defExp);
                        if (!Collections.disjoint(sourceOntologyReasoningService.getDescendants(pvName), focusConcepts)) {
                            checkingIterator.add(pvName);
                            checkingIterator.previous();
                        }
                    }
                }
            }
        }

        //add new definitions to subontology
        man.addAxioms(subOntology, additionalSupportingConceptDefinitions);
        additionalConceptsInExpandedSignature = additionalSupportingClasses;
        System.out.println("Supporting class definitions added " + additionalSupportingConceptDefinitions.size());
        System.out.println("Number of classes added as result of expansion: " + additionalSupportingClasses.size());
        System.out.println("Added classes: " + additionalSupportingClasses);
    }

    //Expansion rule 1 -- has become very general, could be reduced...?
    private boolean supportingDefinitionRequired(OWLClass cls) {
        if(!Collections.disjoint(sourceOntologyReasoningService.getDescendants(cls), focusConcepts) && !sourceOntologyReasoningService.isPrimitive(cls)) {
            return true;
        }
        else {
            return false;
        }
    }

    //Expansion rule 2
    private boolean supportingDefinitionRequired(OWLObjectSomeValuesFrom pv, Set<OWLClassExpression> fillerNecessaryConditions) {
        boolean definitionRequired = false;
        System.out.println("CHECKING  EXPANSION RULE 2.");
        Set<OWLObjectPropertyExpression> topLevelPropertiesInFillerDefinition = new HashSet<>();
        for(OWLClassExpression exp:fillerNecessaryConditions) {
            if(exp instanceof OWLObjectSomeValuesFrom) {
                topLevelPropertiesInFillerDefinition.add(((OWLObjectSomeValuesFrom) exp).getProperty());
            }
        }
        //checking role chain axioms
        Set<OWLAxiom> axiomsToCheck = new HashSet<>(sourceOntology.getAxioms(AxiomType.SUB_PROPERTY_CHAIN_OF));
        axiomsToCheck.addAll(sourceOntology.getAxioms(AxiomType.TRANSITIVE_OBJECT_PROPERTY));
        for(OWLAxiom ax:axiomsToCheck) {
            //chain case
            if(ax.getAxiomType().equals(AxiomType.SUB_PROPERTY_CHAIN_OF)) {
                OWLSubPropertyChainOfAxiom chainAx = (OWLSubPropertyChainOfAxiom) ax;
                if(chainAx.getSuperProperty().equals(pv.getProperty())) {//|| subPropertiesOfTransitiveProperty.contains(chainAx.getSuperProperty())) {
                    Set<OWLObjectPropertyExpression> otherPropertiesInChain = new HashSet<>(chainAx.getPropertyChain());
                    otherPropertiesInChain.remove(pv.getProperty());

                    //r o s <= r case //TODO -- subproperties of s, r?
                    if(!Collections.disjoint(topLevelPropertiesInFillerDefinition, otherPropertiesInChain)) {
                        definitionRequired = true;
                    }
                    //r o r <= r case
                    //if r is transitive...
                    else if(!sourceOntology.getTransitiveObjectPropertyAxioms(pv.getProperty()).isEmpty()) {
                        Set<OWLObjectPropertyExpression> subPropertiesOfTransitiveProperty = sourceOntologyReasoningService.getDescendantProperties(pv.getProperty());
                        //gather sub properties of r
                        //if filler definition contains r...
                        if(topLevelPropertiesInFillerDefinition.contains(pv.getProperty())) {
                            definitionRequired = true;
                        }
                        //or if filler definition contains subproperty of r, i.e. r o s <= r o r <= r
                        else if(!Collections.disjoint(topLevelPropertiesInFillerDefinition, subPropertiesOfTransitiveProperty)) {
                            definitionRequired = true;
                        }
                    }
                }
            }
            //transitive case //TODO: 14-09-2021, needed with above?
            else if(ax.getAxiomType().equals(AxiomType.TRANSITIVE_OBJECT_PROPERTY)) {
                if(ax.getObjectPropertiesInSignature().contains(pv.getProperty())) {
                    Set<OWLObjectPropertyExpression> subPropertiesOfTransitiveProperty = sourceOntologyReasoningService.getDescendantProperties(pv.getProperty());
                    //gather sub properties of r
                    //if filler definition contains r...
                    if(topLevelPropertiesInFillerDefinition.contains(pv.getProperty())) {
                        definitionRequired = true;
                    }
                    //or if filler definition contains subproperty of r, i.e. r o s <= r o r <= r
                    else if(!Collections.disjoint(topLevelPropertiesInFillerDefinition, subPropertiesOfTransitiveProperty)) {
                        definitionRequired = true;
                    }
                }
            }
        }
        return definitionRequired;
    }

    private Set<OWLSubClassOfAxiom> addSupportingConceptGCIs(OWLClass suppCls) {
        Set<OWLSubClassOfAxiom> associatedGCIs = new HashSet<>();
        if(sourceOntologyNamer.hasAssociatedGCIs(suppCls)) {
            Set<OWLClass> gciNames = sourceOntologyNamer.returnNamesOfGCIsForSuperConcept(suppCls);
            for(OWLClass gciName:gciNames) {
                abstractDefinitionsGenerator.generateDefinition(gciName, authoringFormRedundancyOptions);
                associatedGCIs.add(df.getOWLSubClassOfAxiom(df.getOWLObjectIntersectionOf(abstractDefinitionsGenerator.getLatestNecessaryConditions()),suppCls));
                System.out.println("ADDED GCI AXIOM: " + df.getOWLSubClassOfAxiom(df.getOWLObjectIntersectionOf(abstractDefinitionsGenerator.getLatestNecessaryConditions()),suppCls));
            }
        }
        return associatedGCIs;
    }

    //temp: compute STAR module for RBox population
    private void populateRBox() throws OWLOntologyCreationException {
        Set<OWLObjectProperty> objectPropertiesInSignature = subOntology.getObjectPropertiesInSignature();
        Set<OWLEntity> signature = new HashSet<>(objectPropertiesInSignature);

        OWLOntology module = ModuleExtractionHandler.extractSingleModule(sourceOntology, signature, ModuleType.STAR);

        System.out.println("Module axioms: " + module.getAxioms());
        System.out.println("Module classes: " + module.getClassesInSignature());
        System.out.println("Module roles: " + module.getObjectPropertiesInSignature());

        man.addAxioms(subOntology, module.getLogicalAxioms());

        // Include link to "762705008 |Concept model object attribute (attribute)|" in property hierarchy
        for (OWLObjectProperty property : objectPropertiesInSignature) {
            while (property != null && !property.getIRI().toString().equals("http://snomed.info/id/762705008")) {
                Set<OWLSubObjectPropertyOfAxiom> objectSubPropertyAxiomsForSubProperty = sourceOntology.getObjectSubPropertyAxiomsForSubProperty(property);
                if (objectSubPropertyAxiomsForSubProperty.isEmpty()) {
                    property = null;
                    continue;
                }
                man.addAxioms(subOntology, objectSubPropertyAxiomsForSubProperty);
                // We expect properties to have just one parent, taking the first one from the iterator is fine.
                OWLObjectProperty parentProperty = objectSubPropertyAxiomsForSubProperty.iterator().next().getSuperProperty().asOWLObjectProperty();
                if (parentProperty.equals(property)) {
                    System.out.println("EQUAL");
                }
                property = parentProperty;
            }
        }
    }

    private void addGrouperConcepts() {
        Set<OWLClass> topLevelSCTGroupers = new HashSet<>();
        for(OWLSubClassOfAxiom ax: sourceOntology.getSubClassAxiomsForSuperClass(sctTop)) {
            OWLClassExpression subClass = ax.getSubClass();
            if(subClass instanceof OWLClass) {
                topLevelSCTGroupers.add((OWLClass) ax.getSubClass());
            }
        }
        topLevelSCTGroupers.remove(sctTop);
        System.out.println("Adding additional grouper concepts (beneath SCT top)");
        for(OWLClass cls:topLevelSCTGroupers) {
            if(!Collections.disjoint(subOntology.getClassesInSignature(), sourceOntologyReasoningService.getDescendants(cls))) {
                man.addAxiom(subOntology, df.getOWLSubClassOfAxiom(cls, sctTop));
                grouperConcepts.add(cls);
            }
        }
        grouperConcepts.add(sctTop);
    }

    private void completeSubsumptionTransitiveClosure() {
        Set<OWLClass> partiallyDefinedSupportingClasses = new HashSet<>(subOntology.getClassesInSignature());
        partiallyDefinedSupportingClasses.removeAll(focusConcepts);
        partiallyDefinedSupportingClasses.removeAll(definedSupportingConcepts);

        //also include groupers
        partiallyDefinedSupportingClasses.addAll(grouperConcepts);

        for (OWLClass cls : partiallyDefinedSupportingClasses) {
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
            Set<OWLClass> nonRedundantAncestors = new HashSet<>();
            for (OWLClass ancestor : reducedAncestors) {
                //TODO: this check not needed?
                if (!subOntologyReasoningService.getAncestors(cls).contains(ancestor)) {
                    nonRedundantAncestors.add(ancestor);
                }
            }
            if(!nonRedundantAncestors.isEmpty()) {
                if(nonRedundantAncestors.size() == 1) {
                    man.addAxiom(subOntology, df.getOWLSubClassOfAxiom(cls, new ArrayList<>(nonRedundantAncestors).get(0)));
                }
                else {
                    man.addAxiom(subOntology, df.getOWLSubClassOfAxiom(cls, df.getOWLObjectIntersectionOf(nonRedundantAncestors)));
                }
            }
        }
    }

    //Due to definition expansion, some cases will have flat hierarchy e.g. A <= B <= C, where B does not have any non-isA structure in its definition. Can collapse this into
    //A <= C. TODO: improve, this is just initial shrinking. Note: implementation here avoids need to classify ontology, otherwise would need to reclassify post-removal.
    private void shrinkAtomicHierarchy() {
        //for each primitive, atomically defined supporting concept, check following conditions:
        // *If supporting concept "P" has an "atomic definition" i.e., P <= P1, where P1 is a concept (i.e., no conjunction / complex)
        // *If the supporting concept "P" is also only used in atomic inclusions of the form A <= P (A is not a focus concept)
        // *Is not an introduced grouper concept.
        //If satisfied, can be removed.
        Set<OWLClass> atomicPrimitives = new HashSet<>();
        Set<OWLClass> conceptsToCheck = subOntology.getClassesInSignature();
        conceptsToCheck.removeAll(grouperConcepts);
        conceptsToCheck.removeAll(focusConcepts);

        for(OWLClass conceptBeingChecked:conceptsToCheck) {
            if(!subOntologyReasoningService.isPrimitive(conceptBeingChecked)) { //exclude defined supporting concepts
                continue;
            }
            else if(subOntology.getSubClassAxiomsForSubClass(conceptBeingChecked).size() == 1) {//Exclude cases with a conjunctive definition (i.e. P <= P1 and P <= P2) (& empty case).
                OWLSubClassOfAxiom ax = new ArrayList<OWLSubClassOfAxiom>(subOntology.getSubClassAxiomsForSubClass(conceptBeingChecked)).get(0);

                //check if only one definitional axiom for this primitive (P <= ...)
                if (ax.getSuperClass() instanceof OWLClass && subOntologyReasoningService.isPrimitive((OWLClass) ax.getSuperClass())) {
                    OWLClass primitiveParent = (OWLClass) ax.getSuperClass();
                    Set<OWLSubClassOfAxiom> definitionsOfPrimitive = subOntology.getSubClassAxiomsForSubClass(primitiveParent);

                    boolean isAtomicallyDefined = true;
                    if (definitionsOfPrimitive.size() > 1) {
                        isAtomicallyDefined = false;
                    } else {
                        for (OWLSubClassOfAxiom subAx : definitionsOfPrimitive) {
                            if (!(subAx.getSuperClass() instanceof OWLClass)) {
                                isAtomicallyDefined = false;
                                break;
                            }
                        }
                    }
                    if (isAtomicallyDefined) {
                        atomicPrimitives.add(conceptBeingChecked);
                    }
                }
            }
        }

        System.out.println("Atomic primitives: " + atomicPrimitives);

        //check each atomically defined primitive "P": if it is only used in axioms of the form P1 <= P, where "P1" is not a focus concept, remove it.
        Set<OWLClass> primitivesToRemove = new HashSet<>();
        for(OWLClass primitive:atomicPrimitives) {
            Set<OWLLogicalAxiom> axiomsContainingPrimitive = new HashSet<>();
            for(OWLLogicalAxiom ax:subOntology.getLogicalAxioms()) {
                if(ax.containsEntityInSignature(primitive)) {
                    axiomsContainingPrimitive.add(ax);
                }
            }

            boolean usedElsewhere = false;
            //sole parent of primitive -- check if already removed by this process. If so, check for multiple parents. If any exist, do not remove this primitive.
            OWLClass parentOfPrimitive = new ArrayList<>(subOntologyReasoningService.getDirectAncestors(primitive)).get(0);

            for(OWLLogicalAxiom ax:axiomsContainingPrimitive) {
                if(ax instanceof OWLSubClassOfAxiom && ((OWLSubClassOfAxiom)ax).getSubClass().equals(primitive)) {//exclude definition for primitive itself.
                    continue;
                }
                else if(focusConceptDefinitions.contains(ax)) { //do not edit focus concept definitions.
                    usedElsewhere = true;
                    break;
                }
                else if(!(ax instanceof OWLSubClassOfAxiom)) {
                    usedElsewhere = true;
                    break;
                }
                else if(((OWLSubClassOfAxiom)ax).getSuperClass() instanceof OWLObjectIntersectionOf) {
                    //if axiom has form A <= P1 and P2 and ... and Pi where all conjuncts are primitive, including Pi being checked, do not consider this as Pi "used elsewhere".
                    for(OWLClassExpression exp:((OWLSubClassOfAxiom)ax).getSuperClass().asConjunctSet()) {
                        if(!(exp instanceof OWLClass) || !subOntologyReasoningService.isPrimitive((OWLClass)exp)) {
                            usedElsewhere = true;
                            break;
                        }
                    }
                }
                else if(((OWLSubClassOfAxiom)ax).getSubClass() instanceof OWLObjectIntersectionOf) { //do not remove primitives used on LHS of GCIs //TODO: necessary?
                    usedElsewhere = true;
                    break;
                }
            }
            if(!usedElsewhere) {
                System.out.println("Removing primitive: " + primitive.toString());
                primitivesToRemove.add(primitive);
            }
        }

        System.out.println("Primitives to remove: " + primitivesToRemove);

        //add content to remove to a set
        for(OWLClass primitive:primitivesToRemove) {
            Set<OWLAxiom> axiomsToRemove = new HashSet<>();
            System.out.println("Parents of primitive: " + subOntologyReasoningService.getDirectAncestors(primitive));
            //remove class axioms & references, shrink hierarchy to next parent up
            for (OWLAxiom ax : subOntology.getLogicalAxioms()) {
                if (ax.containsEntityInSignature(primitive)) {
                    axiomsToRemove.add(ax);
                    axiomsToRemove.addAll(subOntology.getAnnotationAssertionAxioms(primitive.getIRI()));
                }
            }

            //ensure that parent of the primitive concept is not already removed. Avoids re-adding removed primitives when bridging gap to children.
            List<OWLClass> parentsOfPrimitive = new ArrayList<>();
            ListIterator<OWLClass> parentIterator = new ArrayList<>(subOntologyReasoningService.getDirectAncestors(primitive)).listIterator();
            while(parentIterator.hasNext()) {
                OWLClass parent = parentIterator.next();
                //if already removed, check next parents.
                if(primitivesToRemove.contains(parent)) {
                    Set<OWLClass> nextParents = subOntologyReasoningService.getDirectAncestors(parent);
                    for(OWLClass nextParent:nextParents) {
                        parentIterator.add(nextParent);
                        parentIterator.previous();
                    }
                    continue;
                }
                //else, add as new direct parent.
                parentsOfPrimitive.add(parent);
            }

            //ensure axioms of form A <= P1 and ... and Pi and ... and Pn reduced correctly (remove Pi but retain axiom)
            Set<OWLClass> childrenOfPrimitive = subOntologyReasoningService.getDirectDescendants(primitive);
            childrenOfPrimitive.remove(df.getOWLNothing());
            //change child parents to exclude the removed primitive(s)
            for (OWLClass child : childrenOfPrimitive) {
                if(!primitivesToRemove.contains(child)) {
                    //gather existing parents for child
                    Set<OWLClass> otherParents = subOntologyReasoningService.getDirectAncestors(child);
                    otherParents.removeAll(primitivesToRemove);

                    System.out.println("setting child: " + child + " as child of primitive parents: " + parentsOfPrimitive);

                    List<OWLClass> newParentsOfChild = new ArrayList<>();
                    newParentsOfChild.addAll(parentsOfPrimitive);
                    newParentsOfChild.addAll(otherParents);

                    if (newParentsOfChild.size() == 1) {
                        man.addAxiom(subOntology, df.getOWLSubClassOfAxiom(child, newParentsOfChild.get(0)));
                        continue;
                    }

                    man.addAxiom(subOntology, df.getOWLSubClassOfAxiom(child, df.getOWLObjectIntersectionOf(new HashSet<>(newParentsOfChild))));
                }
            }
            //remove axioms for this primitive
            man.removeAxioms(subOntology, axiomsToRemove);
        }
    }

    //private void computeNNFDefinitions(Set<OWLClass> classes, Set<RedundancyOptions> redundancyOptions) throws ReasonerException, OWLOntologyCreationException {
    private void generateNNFs(Set<RedundancyOptions> redundancyOptions) throws ReasonerException, OWLOntologyCreationException {
        System.out.println("Computing necessary normal form (inferred relationships) for subontology entities.");

        IntroducedNameHandler subOntologyNamer = new IntroducedNameHandler(subOntology);
        OWLOntology subOntologyWithNamings = subOntologyNamer.returnOntologyWithNamings();
        OntologyReasoningService subOntologyReasoningService = new OntologyReasoningService(subOntologyWithNamings);
        subOntologyReasoningService.classifyOntology();
        DefinitionGenerator nnfDefinitionsGenerator = new DefinitionGeneratorNNF(subOntology, subOntologyReasoningService, subOntologyNamer);

        Set<OWLClass> classes = subOntology.getClassesInSignature();
        Set<OWLObjectProperty> props = subOntology.getObjectPropertiesInSignature();

        classes.remove(df.getOWLNothing());
        for(OWLClass cls:classes) {
            nnfDefinitionsGenerator.generateDefinition(cls, nnfRedundancyOptions);
        }
        nnfDefinitions.addAll(nnfDefinitionsGenerator.getAllGeneratedDefinitions());

        for(OWLObjectProperty prop:props) {
            System.out.println("Generating defs for property: " + prop);
            nnfDefinitions.addAll(nnfDefinitionsGenerator.generatePropertyDefinition(prop));
        }
        man.addAxioms(nnfOntology, nnfDefinitions);
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
            if(focusConcepts.contains(cls)) {
                anno = df.getOWLAnnotation(df.getRDFSComment(), df.getOWLLiteral("Focus concept"));
            }
            else if(definedSupportingConcepts.contains(cls)) {
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
    public Set<OWLClass> getSupportingClassesWithAddedDefinitions() {return definedSupportingConcepts;}
    public int getNumberOfClassesAddedDuringSignatureExpansion() { return additionalConceptsInExpandedSignature.size();}
    public int getNumberOfAdditionalSupportingClassDefinitionsAdded() {return additionalSupportingConceptDefinitions.size();}
    public Set<OWLClass> getFocusConcepts() {return focusConcepts;}
}
