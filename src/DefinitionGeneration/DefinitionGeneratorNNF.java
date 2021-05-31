package DefinitionGeneration;

import Classification.OntologyReasoningService;
import NamingApproach.IntroducedNameHandler;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.Imports;

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

        System.out.println("Computing def for INPUT CLASS: " + inputClass);

        System.out.println("RBOX FOR ONT: " + super.getBackgroundOntology().getRBoxAxioms(Imports.fromBoolean(false)).toString());

        Set<OWLClass> parentNamedClasses = new HashSet<OWLClass>();
        parentNamedClasses.addAll(reasonerService.getDirectAncestors(inputClass));

        //remove all introduced classes to name PVs and name GCIs
        parentNamedClasses.removeAll(ancestorRenamedPVs);
        parentNamedClasses.removeAll(extractNamedGCIs(parentNamedClasses));

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

        Set<OWLClassExpression> nonRedundantAncestors = new HashSet<OWLClassExpression>();
        nonRedundantAncestors.addAll(reducedParentNamedClasses);
        nonRedundantAncestors.addAll(reducedAncestorPVs);

        constructDefinitionAxiom(inputClass, nonRedundantAncestors);

        System.out.println("INPUT CLASS: " + inputClass);
        System.out.println("Nonredundant ancestors: " + nonRedundantAncestors);

    }
}
