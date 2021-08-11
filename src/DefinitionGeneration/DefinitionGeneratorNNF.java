package DefinitionGeneration;

import Classification.OntologyReasoningService;
import NamingApproach.IntroducedNameHandler;
import org.semanticweb.owlapi.model.*;

import java.util.*;

public class DefinitionGeneratorNNF extends DefinitionGenerator {

    public DefinitionGeneratorNNF(OWLOntology inputOntology, OntologyReasoningService reasonerService, IntroducedNameHandler namer) {
        super(inputOntology, reasonerService, namer);
        //this.computeReflexiveProperties();
    }

    public void generateDefinition(OWLClass inputClass) {
        //default: all redundancy elimination
        Set<RedundancyOptions> defaultOptions = new HashSet<RedundancyOptions>();
        defaultOptions.add(RedundancyOptions.eliminateLessSpecificRedundancy);
        defaultOptions.add(RedundancyOptions.eliminateReflexivePVRedundancy);
        defaultOptions.add(RedundancyOptions.eliminateRoleGroupRedundancy);
        this.generateDefinition(inputClass, defaultOptions);
    }

    public void generateDefinition(OWLClass inputClass, Set<RedundancyOptions> redundancyOptions) {
        //Need set of ancestors, split into classes and PVs, excluding all introduced names
        Set<OWLClass> ancestors = reasonerService.getAncestors(inputClass);
        Set<OWLClass> ancestorRenamedPVs = extractNamedPVs(ancestors);

        System.out.println("Computing NNF for INPUT CLASS: " + inputClass);

        Set<OWLClass> parentNamedClasses = new HashSet<OWLClass>();
        parentNamedClasses.addAll(reasonerService.getDirectAncestors(inputClass));

        //remove all introduced classes to name PVs and name GCIs
        parentNamedClasses.removeAll(ancestorRenamedPVs);

        //parentNamedClasses.removeAll(extractNamedGCIs(parentNamedClasses)); //TODO: 16-06-21 it takes the GCI *names* as direct ancestors, which are then being removed! Fix.
        List<OWLClass> parentsToCheckForGCIs = new ArrayList<OWLClass>(extractNamedGCIs(parentNamedClasses));
        ListIterator<OWLClass> iterator = parentsToCheckForGCIs.listIterator();
        while(iterator.hasNext()) {
            OWLClass parentToCheck = iterator.next();
            if(namer.isNamedGCI(parentToCheck)) {
                parentNamedClasses.remove(parentToCheck);
                for(OWLClass nextParent:reasonerService.getDirectAncestors(parentToCheck)) {
                    if(!namer.isNamedPV(nextParent)) {
                        parentNamedClasses.add(nextParent);
                        iterator.add(nextParent);
                        iterator.previous();
                    }
                }
            }
        }
        /*
        for(OWLClass gciNameParent:extractNamedGCIs(parentNamedClasses)) {
            parentNamedClasses.remove(gciNameParent);
            //parentNamedClasses.addAll(reasonerService.getDirectAncestors(gciNameParent));
            //parentNamedClasses.add(namer.retrieveSuperClassFromNamedGCI(gciNameParent));
            parentNamedClasses.addAll(reasonerService.getDirectAncestors(gciNameParent));
        }
         */

        Set<OWLClass> reducedParentNamedClasses = new HashSet<OWLClass>();
        Set<OWLObjectSomeValuesFrom> reducedAncestorPVs = new HashSet<OWLObjectSomeValuesFrom>();

        //OLD reflexivity handling: before
        //if(redundancyOptions.contains(RedundancyOptions.eliminateReflexivePVRedundancy)) {
        //    Set<OWLObjectSomeValuesFrom> ancestorPVs = eliminateReflexivePVRedundancies(replaceNamesWithPVs(ancestorRenamedPVs), inputClass);
        //    ancestorRenamedPVs = replacePVsWithNames(ancestorPVs); //t
        //}
        if(redundancyOptions.contains(RedundancyOptions.eliminateLessSpecificRedundancy)) {
            reducedParentNamedClasses = reduceClassSet(parentNamedClasses);
            reducedAncestorPVs = replaceNamesWithPVs(reduceClassSet(ancestorRenamedPVs));
        }
        else {
            reducedParentNamedClasses = parentNamedClasses;
            reducedAncestorPVs = replaceNamesWithPVs(ancestorRenamedPVs);
        }
        if(redundancyOptions.contains(RedundancyOptions.eliminateRoleGroupRedundancy)) {
            reducedAncestorPVs = eliminateRoleGroupRedundancies(reducedAncestorPVs);
        }
        if(redundancyOptions.contains(RedundancyOptions.eliminateReflexivePVRedundancy)) {
            reducedAncestorPVs = eliminateReflexivePVRedundancies(inputClass, reducedAncestorPVs);
        }

        if(inputClass.toString().contains("425576009")) {
            System.out.println("REDUCED PARENT CLASSES NNF: " + reducedParentNamedClasses);
        }

        Set<OWLClassExpression> nonRedundantAncestors = new HashSet<OWLClassExpression>();
        nonRedundantAncestors.addAll(reducedParentNamedClasses);
        nonRedundantAncestors.addAll(reducedAncestorPVs);

        //Set<Set<OWLClassExpression>> nonRedundantAncestorsSet = new HashSet<Set<OWLClassExpression>>();
        Map<Set<OWLClassExpression>, Boolean> nonRedundantAncestorsMap = new HashMap<Set<OWLClassExpression>, Boolean>();
        //nonRedundantAncestorsSet.add(nonRedundantAncestors);
        nonRedundantAncestorsMap.put(nonRedundantAncestors, false);
        constructDefinition(inputClass, nonRedundantAncestorsMap);

        System.out.println("INPUT CLASS: " + inputClass);
        System.out.println("Nonredundant ancestors: " + nonRedundantAncestors);

    }
}
