package org.snomed.ontology.extraction.definitiongeneration;

import org.snomed.ontology.extraction.classification.OntologyReasoningService;
import org.snomed.ontology.extraction.naming.IntroducedNameHandler;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.search.EntitySearcher;

import java.util.*;

public abstract class DefinitionGenerator {

    protected OWLOntology sourceOntology;
    protected OntologyReasoningService reasonerService;
    protected IntroducedNameHandler namer;
    private OWLOntologyManager man;
    protected OWLDataFactory df;
    protected List<Set<OWLAxiom>> generatedDefinitions;
    protected List<OWLAxiom> gciDefinitions;
    protected Set<OWLClassExpression> latestNecessaryConditions;
    protected Set<OWLAxiom> undefinedClasses;

    public DefinitionGenerator(OWLOntology inputOntology, OntologyReasoningService reasonerService, IntroducedNameHandler namer) {
        sourceOntology = inputOntology;
        this.reasonerService = reasonerService;
        this.namer = namer;
        man = OWLManager.createOWLOntologyManager();
        df = man.getOWLDataFactory();
        generatedDefinitions = new ArrayList<>();
        gciDefinitions = new ArrayList<>();
        undefinedClasses = new HashSet<>();
    }

    public abstract void generateDefinition(OWLClass cls, Set<RedundancyOptions> redundancyOptions);

    public Set<OWLClass> reduceClassSet(Set<OWLClass> inputClassSet) {
        inputClassSet = reasonerService.eliminateWeakerClasses(inputClassSet);
        return (inputClassSet);
    }

    //Eliminates redundant PVs nested under role groups. Assumes max nesting is RG(R some C ...).
    public Set<OWLObjectSomeValuesFrom> eliminateRoleGroupRedundancies(Set<OWLObjectSomeValuesFrom> inputPVs) {
        Set<OWLObjectSomeValuesFrom> reducedInputPVs = new HashSet<>();

        for(OWLObjectSomeValuesFrom pv:inputPVs) {
            if(pv.getFiller() instanceof OWLObjectIntersectionOf) {
                Set<OWLObjectSomeValuesFrom> pvFillers = new HashSet<>();
                for(OWLClassExpression filler:pv.getFiller().asConjunctSet()) {
                    if(filler instanceof OWLObjectSomeValuesFrom) {
                        pvFillers.add((OWLObjectSomeValuesFrom) filler);
                    }
                    else {
                        System.out.println("NAMED CLASS FOUND IN ROLE GROUP!");
                    }
                }

                Set<OWLObjectSomeValuesFrom> reducedFillers = replaceNamesWithPVs(reduceClassSet(replacePVsWithNames(pvFillers)));

                reducedInputPVs.add(df.getOWLObjectSomeValuesFrom(pv.getProperty(), df.getOWLObjectIntersectionOf(reducedFillers)));
                continue;
            }
            //if not a role group
            reducedInputPVs.add(pv);
        }
        return reducedInputPVs;
    }

    //not currently needed
    /*
    public void computeSubPropertiesOfReflexiveProperties() {
        System.out.println("Computing reflexive properties.");
        EntitySearcher searcher = new EntitySearcher();

        //get declared reflexive properties
        for(OWLObjectProperty prop:backgroundOntology.getObjectPropertiesInSignature()) {
            if(searcher.isReflexive(prop, backgroundOntology)) {
                subPropertiesOfreflexiveProperties.add(prop);
            }
        }
        ListIterator<OWLObjectPropertyExpression> reflexiveIterator = subPropertiesOfreflexiveProperties.listIterator();
        List<OWLObjectPropertyExpression> additionalReflexiveProperties = new ArrayList<OWLObjectPropertyExpression>();

        //recursively add all subproperties of reflexive properties as reflexive (ELK does not support finding superproperties)
        while(reflexiveIterator.hasNext()) {
            OWLObjectPropertyExpression prop = reflexiveIterator.next();
            System.out.println("Current prop: " + prop.toString());
            for(OWLSubObjectPropertyOfAxiom propAx:backgroundOntology.getObjectSubPropertyAxiomsForSuperProperty(prop)) {
                System.out.println("Propax: " + propAx.toString());
                if(!additionalReflexiveProperties.contains(propAx.getSubProperty())) {
                    reflexiveIterator.add(propAx.getSubProperty());
                    reflexiveIterator.previous();
                    additionalReflexiveProperties.add(propAx.getSubProperty());
                    System.out.println("Reflexive property added: " + propAx.getSubProperty().toString());
                }
            }
        }
        subPropertiesOfreflexiveProperties.addAll(additionalReflexiveProperties);
    }
     */

    public Set<OWLObjectSomeValuesFrom> eliminateReflexivePVRedundancies(OWLClass inputClass, Set<OWLObjectSomeValuesFrom> inputPVs) {
        Set<OWLObjectSomeValuesFrom> reducedInputPVs = new HashSet<>();

        //retrieve reflexive PVs in definition
        for(OWLObjectSomeValuesFrom pv:inputPVs) {
            boolean isReflexiveProperty = checkIfReflexiveProperty(pv.getProperty().asOWLObjectProperty());
            if(isReflexiveProperty) {
                if(reasonerService.getAncestors(inputClass).contains(pv.getFiller()) || pv.getFiller().equals(inputClass)) {
                    continue;
                }
            }
            reducedInputPVs.add(pv);
        }
        return reducedInputPVs;
    }

    public boolean checkIfReflexiveProperty(OWLObjectPropertyExpression r) {
        return EntitySearcher.isReflexive(r, sourceOntology);
    }

    protected Set<OWLClass> extractNamedPVs(Set<OWLClass> classes) {
        Set<OWLClass> renamedPVs = new HashSet<>();

        for (OWLClass cls : classes) {
            if (namer.isNamedPV(cls)) {
                renamedPVs.add(cls);
            }
        }
        return renamedPVs;
    }

    protected Set<OWLClass> extractNamedGCIs(Set<OWLClass> classes) {
        Set<OWLClass> namedGCIs =  new HashSet<>();

        for(OWLClass cls : classes) {
            if(namer.isNamedGCI(cls)) {
                namedGCIs.add(cls);
            }
        }
        return namedGCIs;
    }

    public Set<OWLObjectSomeValuesFrom> replaceNamesWithPVs(Set<OWLClass> classes) {
        return namer.retrievePVsFromNames(classes);
    }

    protected Set<OWLClass> replacePVsWithNames(Set<OWLObjectSomeValuesFrom> pvs) {
        return namer.retrieveNamesFromPVs(pvs);
    }

    //protected void constructDefinition(OWLClass definedClass, Set<Set<OWLClassExpression>> definingConditionsByAxiom) {
    protected void constructDefinition(OWLClass definedClass, Map<Set<OWLClassExpression>, Boolean> definingConditionsByAxiom) {
        Set<OWLAxiom> definitionAxioms = new HashSet<>();

        //for(Set<OWLClassExpression> definingConditions:definingConditionsByAxiom) {
        for(Map.Entry<Set<OWLClassExpression>, Boolean> entry:definingConditionsByAxiom.entrySet()) {
            Set<OWLClassExpression> definingConditions = entry.getKey();
            Boolean isEquivalence = entry.getValue();
            definingConditions.remove(df.getOWLThing());
            definingConditions.remove(df.getOWLNothing());
            if (definingConditions.size() == 0) {
                continue;
            }
            OWLAxiom definingAxiom;
            if (definingConditions.size() == 1) {
                OWLClassExpression definingCondition = (new ArrayList<>(definingConditions)).get(0);
                //if (!sourceOntology.getEquivalentClassesAxioms(definedClass).isEmpty()) {
                if(isEquivalence) {
                    definingAxiom = df.getOWLEquivalentClassesAxiom(definedClass, definingCondition);
                } else {
                    definingAxiom = df.getOWLSubClassOfAxiom(definedClass, definingCondition);
                }
            } else {
                //if (!sourceOntology.getEquivalentClassesAxioms(definedClass).isEmpty()) {
                if(isEquivalence) {
                    definingAxiom = df.getOWLEquivalentClassesAxiom(definedClass, df.getOWLObjectIntersectionOf(definingConditions));

                } else {
                    definingAxiom = df.getOWLSubClassOfAxiom(definedClass, df.getOWLObjectIntersectionOf(definingConditions));
                }
            }

            latestNecessaryConditions = definingConditions;
            //store gci definitions separately. //TODO: improve in line with multi-axiom handling
            if (namer.isNamedGCI(definedClass)) {
                System.out.println("GCI DEFINITION ADDED: " + definingAxiom);
                gciDefinitions.add(definingAxiom);
                continue;
            }
            //generatedDefinitions.add(definingAxiom);
            definitionAxioms.add(definingAxiom);
            //return;
        }

        //make generatedDefinitions a set of sets, go from there? //check "get latest necessary conditions"
        if(definitionAxioms.size() == 0 && !namer.isNamedGCI(definedClass)) {
            System.out.println("Undefined class: " + definedClass);
            undefinedClasses.add(df.getOWLSubClassOfAxiom(df.getOWLThing(), definedClass));
            return;
        }
        generatedDefinitions.add(definitionAxioms);
        return;
    }

    public Set<OWLAxiom> getAllGeneratedDefinitions() {
        Set<OWLAxiom> allDefinitions = new HashSet<>();
        for(Set<OWLAxiom> definitionsForClass:generatedDefinitions) {
            allDefinitions.addAll(definitionsForClass);
        }
        return allDefinitions;
    }

    public Set<OWLAxiom> getLastDefinitionGenerated() {
        Set<OWLAxiom> latestDefinition = null;
        if (generatedDefinitions != null && !generatedDefinitions.isEmpty()) {
             latestDefinition = generatedDefinitions.get(generatedDefinitions.size()-1);
        }
        return latestDefinition;
    }

    public void removeLastDefinition() {
        generatedDefinitions.remove(generatedDefinitions.get(generatedDefinitions.size()-1));
    }

    public Set<OWLClassExpression> getLatestNecessaryConditions() {
        return this.latestNecessaryConditions;
    }
    public Set<OWLAxiom> getUndefinedClassAxioms() { return this.undefinedClasses; }
    protected OWLOntology getSourceOntology() { return this.sourceOntology; }

    //currently not storing property definitions, may make sense to combine these two generateDefinitions to operate simply over entities and decide based on type
    public Set<OWLAxiom> generatePropertyDefinition(OWLObjectProperty propToDefine) {
        Set<OWLSubObjectPropertyOfAxiom> propAxioms = sourceOntology.getAxioms(AxiomType.SUB_OBJECT_PROPERTY);
        //collect stated super properties of property to define
        Set<OWLObjectPropertyExpression> statedSuperProps = new HashSet<>();
        for(OWLSubObjectPropertyOfAxiom ax:propAxioms) {
            if(ax.getSubProperty().equals(propToDefine)) {
                statedSuperProps.add(ax.getSuperProperty());
            }
        }

        //eliminate non-direct super properties
        Set<OWLObjectPropertyExpression> otherSuperProps = new HashSet<>();
        Set<OWLObjectPropertyExpression> directSuperProps = new HashSet<>();

        for(OWLObjectPropertyExpression superProp:statedSuperProps) {
            otherSuperProps.remove(superProp);
            if(isDirectSuperProperty(superProp, otherSuperProps)) {
                directSuperProps.add(superProp);
            }
            otherSuperProps.add(superProp);
        }

        //return set of inclusion axioms of form r <= s for each direct super property s
        Set<OWLAxiom> definitionalAxioms = new HashSet<>();
        for(OWLObjectPropertyExpression directSuperProp:directSuperProps) {
            definitionalAxioms.add(df.getOWLSubObjectPropertyOfAxiom(propToDefine, directSuperProp));
        }
        return definitionalAxioms;
    }

    private boolean isDirectSuperProperty(OWLObjectPropertyExpression superProperty, Set<OWLObjectPropertyExpression> otherSuperProperties) {
        Set<OWLSubObjectPropertyOfAxiom> propAxioms = sourceOntology.getAxioms(AxiomType.SUB_OBJECT_PROPERTY);
        for (OWLObjectPropertyExpression otherProp : otherSuperProperties) {
            for (OWLSubObjectPropertyOfAxiom propAx : propAxioms) {
                if (propAx.getSubProperty().equals(superProperty) && otherSuperProperties.contains(propAx.getSuperProperty())) {
                    return false;
                }
            }
        }
        return true;
    }
}
