package SubOntologyExtraction;

import Classification.OntologyReasoningService;
import DefinitionGeneration.DefinitionGenerator;
import DefinitionGeneration.DefinitionGeneratorAbstract;
import DefinitionGeneration.DefinitionGeneratorNNF;
import DefinitionGeneration.RedundancyOptions;
import ExceptionHandlers.ReasonerException;
import NamingApproach.PropertyValueNamer;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.Imports;

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
    private PropertyValueNamer sourceOntologyNamer;
    private Set<OWLAxiom> focusConceptDefinitions;
    private Set<OWLAxiom> nnfDefinitions;
    private Set<OWLClass> focusClasses;
    private OWLOntology subOntology;
    private OWLOntology nnfOntology;
    private OWLClass sctTop;
    //TODO: temp variable, implement stats handler
    //classes and definitions added during signature expansion
    private Set<OWLClass> definedSupportingClasses = new HashSet<OWLClass>();
    private List<OWLClass> additionalClassesInExpandedSignature = new ArrayList<OWLClass>();
    private Set<OWLAxiom> additionalSupportingClassDefinitionsAdded = new HashSet<OWLAxiom>();
    private DefinitionGenerator abstractDefinitionsGenerator;
    private Set<RedundancyOptions> redundancyOptions;

    public SubOntologyExtractor(OWLOntology backgroundOntology) throws OWLOntologyCreationException, ReasonerException {
        this.sourceOntology = backgroundOntology;
        man = backgroundOntology.getOWLOntologyManager();
        df = man.getOWLDataFactory();
        sctTop = df.getOWLClass(IRI.create("http://snomed.info/id/138875005"));

        System.out.println("Initialising ELK taxonomy graph.");
        this.renamePVsAndClassify();
        focusConceptDefinitions = new HashSet<OWLAxiom>();
        nnfDefinitions = new HashSet<OWLAxiom>(); //TODO: add "memory" of generated defs somewhere?

        abstractDefinitionsGenerator = new DefinitionGeneratorAbstract(sourceOntology, sourceOntologyReasoningService, sourceOntologyNamer);
    }

    private void renamePVsAndClassify() throws OWLOntologyCreationException, ReasonerException {
        sourceOntologyNamer = new PropertyValueNamer();
        OWLOntology backgroundOntologyWithRenamings = sourceOntologyNamer.returnOntologyWithNamedPropertyValues(sourceOntology);
        sourceOntologyReasoningService = new OntologyReasoningService(backgroundOntologyWithRenamings);
        sourceOntologyReasoningService.classifyOntology();
    }

    public void computeSubontology(Set<OWLClass> classesToDefine) throws OWLException, ReasonerException {
        Set<RedundancyOptions> defaultOptions = new HashSet<RedundancyOptions>();
        defaultOptions.add(RedundancyOptions.eliminateLessSpecificRedundancy);
        defaultOptions.add(RedundancyOptions.eliminateReflexivePVRedundancy); //TODO: should this be default, or not?
        defaultOptions.add(RedundancyOptions.eliminateRoleGroupRedundancy);
        this.computeSubontology(classesToDefine, defaultOptions);
    }

    //TODO: refactor, split
    //TODO: a lot of redundancy, fine for testing purposes but needs streamlining.
    public void computeSubontology(Set<OWLClass> classesToDefine, Set<RedundancyOptions> inputRedundancyOptions) throws OWLException, ReasonerException {
        focusClasses = classesToDefine;
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
        //add gci axioms for relevant classes
        addGCIAxioms();

        //add property inclusion axioms for properties
        addPropertyInclusions();

        //TODO: which?
        computeRequiredSupportingClassDefinitions();

        //add grouper classes
        addGrouperClasses();

        //add atomic class hierarchy where needed. Classes in the focus set, or classes whose definitions have been added, do not need to be considered here.
        addAtomicClassHierarchy();

        //add top level classes
        addTopLevelClasses();

        //add top level roles -- //TODO: 12-01-2021, add role hierarchy needed. Initial: drag in all subobjectproperty, chains wrt to signature.
        //addTopLevelRoles();

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
        System.out.println("Adding additional grouper concepts (beneath SCT top)");
        for(OWLClass cls:topLevelSCTGroupers) {
            if(!Collections.disjoint(subOntology.getClassesInSignature(), sourceOntologyReasoningService.getDescendantClasses(cls))) {
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

    private void computeRequiredSupportingClassDefinitions() throws OWLOntologyCreationException {
        ArrayList<OWLClass> supportingClasses = new ArrayList<OWLClass>(subOntology.getClassesInSignature());
        supportingClasses.removeAll(focusClasses);

        //gathering PVs occurring in the subontology, required for role chain issue //TODO: improve code here, rough.
        //start by adding the names of all PVs in definitions of focus concepts to the supportingClass set
        //in this way, up to checking fillers etc, can treat concepts and PVs in exactly the same way?
        Set<OWLObjectSomeValuesFrom> pvsInFocusDefinitions = new HashSet<OWLObjectSomeValuesFrom>();
        for(OWLAxiom ax:focusConceptDefinitions) {
            if(ax instanceof OWLSubClassOfAxiom) {
                Set<OWLClassExpression> conjuncts = new HashSet<OWLClassExpression>(((OWLSubClassOfAxiom) ax).getSuperClass().asConjunctSet());
                for(OWLClassExpression conj:conjuncts) {
                    if(conj instanceof OWLObjectSomeValuesFrom) {
                        pvsInFocusDefinitions.add((OWLObjectSomeValuesFrom) conj);
                    }
                }
            }
            else if(ax instanceof OWLEquivalentClassesAxiom) {
                Set<OWLSubClassOfAxiom> axs = ((OWLEquivalentClassesAxiom) ax).asOWLSubClassOfAxioms();
                for(OWLSubClassOfAxiom subAx:axs) { //should only contain A == B and C and R some C and R some D, so can just add all ObjectSomeValueOfs
                    if(subAx.getSubClass().asConjunctSet().size() == 1) {
                        Set<OWLClassExpression> conjuncts = new HashSet<OWLClassExpression>(subAx.getSuperClass().asConjunctSet());
                        for(OWLClassExpression conj:conjuncts) {
                            if(conj instanceof OWLObjectSomeValuesFrom) {
                                pvsInFocusDefinitions.add((OWLObjectSomeValuesFrom)conj);
                            }
                        }
                    }
                }
            }
        }

        Set<OWLObjectProperty> chainProperties = new HashSet<OWLObjectProperty>();
        for(OWLSubPropertyChainOfAxiom chainAx:sourceOntology.getAxioms(AxiomType.SUB_PROPERTY_CHAIN_OF)) {
            chainProperties.addAll(chainAx.getObjectPropertiesInSignature()); //TODO: check this isn't too much! May only need some of r o s <= r cases?
        }
        for(OWLTransitiveObjectPropertyAxiom transAx:sourceOntology.getAxioms(AxiomType.TRANSITIVE_OBJECT_PROPERTY)) {
            chainProperties.addAll(transAx.getObjectPropertiesInSignature());
        }

        for(OWLObjectSomeValuesFrom pv:pvsInFocusDefinitions) {
            //if(sourceOntologyNamer.getPvNamingMap().containsKey(pv) && chainProperties.contains(pv.getProperty())) {
                OWLClass pvName = sourceOntologyNamer.getPvNamingMap().get(pv);
                supportingClasses.add(pvName);
                System.out.println("pvName: " + pvName);
            //}
        }

        System.out.println("FOCUS DEF PVS NUM: " + pvsInFocusDefinitions.size());
        System.out.println("CHAIN PROPERTIES NUM: " + chainProperties.size());

        ListIterator<OWLClass> supportingClassIterator = supportingClasses.listIterator();
        List<OWLClass> additionalSupportingClasses = new ArrayList<OWLClass>();

        while(supportingClassIterator.hasNext()) {
            OWLClass suppCls = supportingClassIterator.next();
            System.out.println("Checking cls: " + suppCls.toString());

            //TODO: check possible avenues of subsumption for each language feature (role chains, hierarchies...)
            Set<OWLClass> descendents = sourceOntologyReasoningService.getDescendantClasses(suppCls);
                //pv case

                if (sourceOntologyNamer.isNamedPV(suppCls)) {
                    System.out.println("checking pv case.");
                    OWLObjectSomeValuesFrom pv = sourceOntologyNamer.getNamingPvMap().get(suppCls);

                    //TODO: handle rolegroup better 609096000
                    System.out.println("PV property: " + pv.getProperty());
                    //System.out.println("Chain properties: " + chainProperties);
                    if(chainProperties.contains(pv.getProperty()) || pv.getProperty().toString().contains("609096000")) {
                    //if(pv.getProperty().toString().contains("609096000") || pv.getProperty().toString().contains("246075003") || pv.getProperty().toString().contains("738774007") || pv.getProperty().toString().contains("127489000")) {
                        System.out.println("Chain property detected, adding filler supporting concept definition.");
                        OWLClassExpression filler = pv.getFiller();
                        for(OWLClassExpression conj:filler.asConjunctSet()) {
                            if(conj instanceof OWLClass) {
                                definedSupportingClasses.add((OWLClass)conj);
                                abstractDefinitionsGenerator.generateDefinition((OWLClass)conj, redundancyOptions);
                                additionalSupportingClassDefinitionsAdded.add(abstractDefinitionsGenerator.getLastDefinitionGenerated());

                                //TODO: refactor, repeated
                                Set<OWLClass> defClasses = abstractDefinitionsGenerator.getLastDefinitionGenerated().getClassesInSignature();
                                for (OWLClass defClass : defClasses) {
                                    if (!subOntology.getClassesInSignature().contains(defClass) && !definedSupportingClasses.contains(defClass)) {
                                        supportingClassIterator.add(defClass);
                                        additionalSupportingClasses.add(defClass);
                                        supportingClassIterator.previous();
                                    }
                                }
                            }
                            else if(conj instanceof OWLObjectSomeValuesFrom) {
                                supportingClassIterator.add(sourceOntologyNamer.getPvNamingMap().get(conj));
                                supportingClassIterator.previous();
                            }
                        }
                    }
               } else { //concept case

                    if (!Collections.disjoint(focusClasses, descendents)) {

                        abstractDefinitionsGenerator.generateDefinition(suppCls, redundancyOptions);
                    OWLAxiom suppClsDefinition = abstractDefinitionsGenerator.getLastDefinitionGenerated();

                    //TODO: possibly change to include checking for equivalence between atomic concepts(??)
                    boolean nonAtomicInclusionOrInvolvesFocus = false;
                    if (suppClsDefinition instanceof OWLSubClassOfAxiom) {
                        for (OWLClassExpression conj : ((OWLSubClassOfAxiom) suppClsDefinition).getSuperClass().asConjunctSet()) {
                            if (conj instanceof OWLObjectSomeValuesFrom || focusClasses.contains(conj)) {
                                nonAtomicInclusionOrInvolvesFocus = true;
                            }
                        }
                    } else {
                        nonAtomicInclusionOrInvolvesFocus = true;
                    }
                    if (nonAtomicInclusionOrInvolvesFocus) {
                        definedSupportingClasses.add(suppCls);
                        additionalSupportingClassDefinitionsAdded.add(suppClsDefinition);
                        System.out.println("Adding definition for supporting class: " + suppCls.toString());

                        Set<OWLClass> defClasses = suppClsDefinition.getClassesInSignature();
                        for (OWLClass defClass : defClasses) {
                            if (!subOntology.getClassesInSignature().contains(defClass) && !definedSupportingClasses.contains(defClass)) {
                                supportingClassIterator.add(defClass);
                                additionalSupportingClasses.add(defClass);
                                supportingClassIterator.previous();
                            }
                        }
                    }
                }
            }
        }
        additionalClassesInExpandedSignature = additionalSupportingClasses;
        System.out.println("Supporting class definitions added " + additionalSupportingClasses.size() + " new classes.");
        System.out.println("Added classes: " + additionalSupportingClasses);

        man.addAxioms(subOntology, additionalSupportingClassDefinitionsAdded);

        additionalClassesInExpandedSignature = additionalSupportingClasses;
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
                if (!sourceOntologyNamer.isNamedPV(ancestor) && subOntology.getClassesInSignature().contains(ancestor) && !subOntologyAncestors.contains(ancestor)) {
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
    }
     */

    private void computeNNFDefinitions(Set<OWLClass> classes, Set<RedundancyOptions> redundancyOptions) throws ReasonerException, OWLOntologyCreationException {
        System.out.println("Computing necessary normal form (inferred relationships).");
        OWLOntology subOntologyWithRenamings = sourceOntologyNamer.returnOntologyWithNamedPropertyValues(subOntology);
        OntologyReasoningService subOntologyReasoningService = new OntologyReasoningService(subOntologyWithRenamings);
        subOntologyReasoningService.classifyOntology();
        DefinitionGenerator nnfDefinitionsGenerator = new DefinitionGeneratorNNF(subOntology, subOntologyReasoningService, sourceOntologyNamer);

        classes.remove(df.getOWLNothing());
        for(OWLClass cls:classes) {
            nnfDefinitionsGenerator.generateDefinition(cls, redundancyOptions);
        }
        nnfDefinitions.addAll(nnfDefinitionsGenerator.getGeneratedDefinitions());
    }

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
                //if ("skos:prefLabel".equals(as.getProperty().toString()) || "rdfs:label".equals(as.getProperty().toString())) {
                    annotationAssertionAxioms.add(as);
                //}
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
