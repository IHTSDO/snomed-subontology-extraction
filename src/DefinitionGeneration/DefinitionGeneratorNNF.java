package DefinitionGeneration;

import Classification.OntologyReasoningService;
import NamingApproach.PropertyValueNamer;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.search.EntitySearcher;

import java.util.*;

public class DefinitionGeneratorNNF extends DefinitionGenerator {

    public DefinitionGeneratorNNF(OWLOntology inputOntology, OntologyReasoningService reasonerService, PropertyValueNamer namer) {
        super(inputOntology, reasonerService, namer);
    }

    @Override
    //TODO: make version of this for groups of signatures -- identify everything that can be done once, and only do it once (efficiency). Top down?
    public void generateDefinition(OWLClass inputClass) {
        this.generateDefinition(inputClass, new HashSet<RedundancyOptions>());
    }

    public void generateDefinition(OWLClass inputClass, Set<RedundancyOptions> redundancyOptions) {
        Set<OWLClass> ancestors = reasonerService.getAncestorClasses(inputClass);
        System.out.println("Class: " + inputClass + ", ancestors: " + ancestors);
        Set<OWLClass> ancestorRenamedPVs = extractNamedPVs(ancestors);
        System.out.println("Class: " + inputClass + ", ancestor PVs: " + ancestorRenamedPVs);

        Set<OWLClass> parentNamedClasses = new HashSet<OWLClass>();
        parentNamedClasses.addAll(reasonerService.getParentClasses(inputClass));

        parentNamedClasses.removeAll(ancestorRenamedPVs); //TODO: needs testing.

        //TODO: needs to be done before rest of redundancy removal, due also to transitivity?
        if(redundancyOptions.contains(RedundancyOptions.eliminatereflexivePVRedundancy) == true) {
            Set<OWLObjectSomeValuesFrom> ancestorPVs = eliminateReflexivePVRedundancies(replaceNamesWithPVs(ancestorRenamedPVs), inputClass);
            ancestorRenamedPVs = replacePVsWithNames(ancestorPVs);
        }

        Set<OWLClass> reducedParentNamedClasses = reduceClassSet(parentNamedClasses);
        Set<OWLObjectSomeValuesFrom> reducedAncestorPVs = replaceNamesWithPVs(reduceClassSet(ancestorRenamedPVs));

        //if(redundancyOptions.contains(RedundancyOptions.eliminatereflexivePVRedundancy) == true) {
        //    reducedAncestorPVs = eliminateReflexivePVRedundancies(reducedAncestorPVs, inputClass); //TODO: parent or reduced?
        //}
        if(redundancyOptions.contains(RedundancyOptions.eliminateRoleGroupRedundancy) == true) {
            reducedAncestorPVs = eliminateRoleGroupRedundancies(reducedAncestorPVs);
        }


        Set<OWLClassExpression> nonRedundantAncestors = new HashSet<OWLClassExpression>();
        nonRedundantAncestors.addAll(reducedParentNamedClasses);
        nonRedundantAncestors.addAll(reducedAncestorPVs);

        constructNecessaryDefinitionAxiom(inputClass, nonRedundantAncestors);

    }

    //TODO: max nesting is RG(R some C ...) correct? If so, no "depth" parameter needed here.
    private Set<OWLObjectSomeValuesFrom> eliminateRoleGroupRedundancies(Set<OWLObjectSomeValuesFrom> inputPVs) {
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

    private Set<OWLObjectSomeValuesFrom> eliminateReflexivePVRedundancies(Set<OWLObjectSomeValuesFrom> inputPVs, OWLClass inputClass) {
        Set<OWLObjectSomeValuesFrom> reducedInputPVs = new HashSet<OWLObjectSomeValuesFrom>();

        EntitySearcher searcher = new EntitySearcher();

        //retrieve reflexive PVs in definition
        System.out.println("Checking reflexive PVs.");

        for(OWLObjectSomeValuesFrom pv:inputPVs) {
            if (searcher.isReflexive(pv.getProperty(), backgroundOntology) == true
                    && (reasonerService.getAncestorClasses(inputClass).contains(pv.getFiller()) || pv.getFiller().equals(inputClass))) {
                System.out.println("PV: " + pv + " is reflexive redundancy.");
                continue;
            }
            System.out.println("PV: " + pv + " is NOT reflexive redundancy.");
            reducedInputPVs.add(pv);
        }
        return reducedInputPVs;
    }

    /*
    private Set<OWLObjectSomeValuesFrom> eliminateReflexivePVRedundancies(Set<OWLObjectSomeValuesFrom> inputPVs, OWLClass inputClass, Set<OWLClass> parentNamedClasses) {
        //TODO: refactor, some of this is confusing to follow code wise + inefficient
        Set<OWLObjectSomeValuesFrom> reducedInputPVs = new HashSet<OWLObjectSomeValuesFrom>();

        EntitySearcher searcher = new EntitySearcher();

        //retrieve reflexive PVs in definition
        Set<OWLClass> redundantReflexivePVNames = new HashSet<OWLClass>();

        Set<OWLObjectSomeValuesFrom> reflexivePVs = new HashSet<OWLObjectSomeValuesFrom>();
        for(OWLObjectSomeValuesFrom pv:inputPVs) {
            OWLObjectPropertyExpression prop = pv.getProperty();
            if(searcher.isReflexive(pv.getProperty(), backgroundOntology) == true) {
                //Begin by removing cases such as A <= exists r.A where A is reflexive
                if(!pv.getFiller().equals(inputClass)) {
                    reducedInputPVs.add(pv);
                }
                reflexivePVs.add(pv);
            }
            //if not reflexive, add to final set
            reducedInputPVs.add(pv); //EDIT: currently removing from set as needed.
        }

        //Now remove all reflexive redundancies where the filler is an ancestor
        Set<OWLClass> reflexivePVNames = replacePVsWithNames(reflexivePVs);
        for(OWLClass name:reflexivePVNames) {
            System.out.println("Reflexive PV name: " + name);
            if(reasonerService.weakerThanAtLeastOneOf(name, parentNamedClasses)) {
                redundantReflexivePVNames.add(name);
            }
            //System.out.println("PV: " + name + " is reflexive redundancy.");

        }

        reducedInputPVs.removeAll(replaceNamesWithPVs(redundantReflexivePVNames));

        return reducedInputPVs;
    }

     */
}
