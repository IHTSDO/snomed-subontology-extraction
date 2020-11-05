package DefinitionGeneration;

import Classification.OntologyReasoningService;
import NamingApproach.PropertyValueNamer;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.search.EntitySearcher;

import java.util.*;

public abstract class DefinitionGenerator {

    protected OWLOntology backgroundOntology;
    protected OntologyReasoningService reasonerService;
    private PropertyValueNamer namer;
    private OWLOntologyManager man;
    protected OWLDataFactory df;
    //protected List<OWLObjectPropertyExpression> subPropertiesOfreflexiveProperties;
    protected Set<OWLAxiom> generatedDefinitions;
    protected Set<OWLAxiom> undefinedClasses;

    public DefinitionGenerator(OWLOntology inputOntology, OntologyReasoningService reasonerService, PropertyValueNamer namer) {
        backgroundOntology = inputOntology;
        this.reasonerService = reasonerService;
        this.namer = namer;
        man = OWLManager.createOWLOntologyManager();
        df = man.getOWLDataFactory();
        //subPropertiesOfreflexiveProperties = new ArrayList<OWLObjectPropertyExpression>();
        generatedDefinitions = new HashSet<OWLAxiom>();
        undefinedClasses = new HashSet<OWLAxiom>(); //TODO: add "memory" of generated defs somewhere?
    }

    public abstract void generateDefinition(OWLClass cls);

    public Set<OWLClass> reduceClassSet(Set<OWLClass> inputClassSet) {
        Set<OWLClass> redundantClasses = new HashSet<OWLClass>();

        Set<OWLClass> otherClasses = new HashSet<OWLClass>(inputClassSet);
        for (OWLClass cls : inputClassSet) {
            otherClasses.remove(cls);
            if (reasonerService.weakerThanAtLeastOneOf(cls, otherClasses)) {
                redundantClasses.add(cls);
            }
            otherClasses.add(cls); //retain redundancies to check against (?)
            // TODO: check, would be problematic if we have equivalent named classes or PVs, since this will mean both are removed. Is this ever the case with SCT?
        }   // TODO:...but if A |= B, then we have B |= C, via this approach we can safely remove them as we identify them? DOUBLE CHECK.

        inputClassSet.removeAll(redundantClasses);
        //inputClassSet.remove(df.getOWLThing());
        //inputClassSet.remove(df.getOWLNothing());
        return (inputClassSet); //TODO: return as list or set?
    }

    //TODO: max nesting is RG(R some C ...) correct? If so, no "depth" parameter needed here.
    Set<OWLObjectSomeValuesFrom> eliminateRoleGroupRedundancies(Set<OWLObjectSomeValuesFrom> inputPVs) {
        Set<OWLObjectSomeValuesFrom> reducedInputPVs = new HashSet<OWLObjectSomeValuesFrom>();

        for(OWLObjectSomeValuesFrom pv:inputPVs) {
            if(pv.getFiller() instanceof OWLObjectIntersectionOf) {
                Set<OWLObjectSomeValuesFrom> pvFillers = new HashSet<OWLObjectSomeValuesFrom>();
                for(OWLClassExpression filler:pv.getFiller().asConjunctSet()) { //TODO: note, unnecessary if we know all role groups only contain pvs
                    if(filler instanceof OWLObjectSomeValuesFrom) {
                        pvFillers.add((OWLObjectSomeValuesFrom) filler);
                    }
                    else {
                        System.out.println("NAMED CLASS FOUND IN ROLE GROUP!");
                    }
                }

                Set<OWLClass> fillerNames = replacePVsWithNames(pvFillers);
                Set<OWLObjectSomeValuesFrom> reducedFillers = replaceNamesWithPVs(reduceClassSet(replacePVsWithNames(pvFillers)));

                reducedInputPVs.add(df.getOWLObjectSomeValuesFrom(pv.getProperty(), df.getOWLObjectIntersectionOf(reducedFillers)));
                continue;
            }
            //if not a role group
            reducedInputPVs.add(pv);
        }
        return reducedInputPVs;
    }

    //TODO: use of this?
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

        //TODO: recursively add all subproperties of reflexive properties as reflexive (ELK does not support finding superproperties?
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

    Set<OWLObjectSomeValuesFrom> eliminateReflexivePVRedundancies(Set<OWLObjectSomeValuesFrom> inputPVs, OWLClass inputClass) {
        Set<OWLObjectSomeValuesFrom> reducedInputPVs = new HashSet<OWLObjectSomeValuesFrom>();

        //retrieve reflexive PVs in definition
        for(OWLObjectSomeValuesFrom pv:inputPVs) {
            boolean isReflexiveProperty = checkIfReflexiveProperty(pv.getProperty().asOWLObjectProperty());
            if (isReflexiveProperty == true) {
                if(reasonerService.getAncestorClasses(inputClass).contains(pv.getFiller()) || pv.getFiller().equals(inputClass)) {
                    System.out.println("Is reflexive redundancy: " + pv);
                    continue;
                }
            }
            reducedInputPVs.add(pv);
        }
        return reducedInputPVs;
    }

    public boolean checkIfReflexiveProperty(OWLObjectPropertyExpression r) {
        EntitySearcher searcher = new EntitySearcher();
        if(searcher.isReflexive(r, backgroundOntology)) {
            //System.out.println("Property: " + r.toString() + " is reflexive.");
            return true;
        }
        return false;
    }

    //TODO: refactor, some of these bit redundant with renamer.
    public Set<OWLClass> extractNamedPVs(Set<OWLClass> classes) {
        Set<OWLClass> renamedPVs = new HashSet<OWLClass>();

        for (OWLClass cls : classes) {
            if (namer.isNamedPV(cls) == true) {
                renamedPVs.add(cls);
            }
        }
        return renamedPVs;
    }

    public Set<OWLClass> extractNamedClasses(Set<OWLClass> classes) {
        Set<OWLClass> namedClasses = new HashSet<OWLClass>();

        for (OWLClass cls : classes) {
            if (namer.isNamedPV(cls) == false) {
                namedClasses.add(cls);
            }
        }
        return namedClasses;
    }

    public Set<OWLObjectSomeValuesFrom> replaceNamesWithPVs(Set<OWLClass> classes) {
        Set<OWLObjectSomeValuesFrom> pvs = namer.retrievePVsFromNames(classes);
        return pvs;
    }

    public Set<OWLClass> replacePVsWithNames(Set<OWLObjectSomeValuesFrom> pvs) {
        return namer.retrieveNamesForPVs(pvs);
    }

    protected void constructNecessaryDefinitionAxiom(OWLClass definedClass, Set<OWLClassExpression> definingConditions) {
        //Optional<OWLAxiom> definition = null;
        definingConditions.remove(df.getOWLThing());
        definingConditions.remove(df.getOWLNothing());
        System.out.println("cls: " + definedClass);
        System.out.println("definingConditions: " + definingConditions);
        if (definingConditions.size() == 0) {
            System.out.println("No necessary conditions for class: " + definedClass);
            //no necessary condition for class (it is "top level class". Return top <= class. TODO: better way?
            undefinedClasses.add(df.getOWLSubClassOfAxiom(df.getOWLThing(), definedClass));
            return;
        }
        else if(definingConditions.size() == 1) {
            OWLClassExpression definingCondition = (new ArrayList<OWLClassExpression>(definingConditions)).get(0);
            generatedDefinitions.add(df.getOWLSubClassOfAxiom(definedClass, definingCondition));
            return;
        }
        //TODO: handle case with 1 necessary condition, no conjunction in superclass?
        generatedDefinitions.add(df.getOWLSubClassOfAxiom(definedClass, df.getOWLObjectIntersectionOf(definingConditions)));
    }

    public Set<OWLAxiom> getGeneratedDefinitions() {
        return this.generatedDefinitions;
    }

    public Set<OWLAxiom> getUndefinedClassAxioms() { return this.undefinedClasses; }
}
