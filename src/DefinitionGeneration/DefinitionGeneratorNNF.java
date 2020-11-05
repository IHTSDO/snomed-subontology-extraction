package DefinitionGeneration;

import Classification.OntologyReasoningService;
import NamingApproach.PropertyValueNamer;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.search.EntitySearcher;

import java.util.*;

public class DefinitionGeneratorNNF extends DefinitionGenerator {

    public DefinitionGeneratorNNF(OWLOntology inputOntology, OntologyReasoningService reasonerService, PropertyValueNamer namer) {
        super(inputOntology, reasonerService, namer);
        //this.computeReflexiveProperties();
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

        if(redundancyOptions.contains(RedundancyOptions.eliminateRoleGroupRedundancy) == true) {
            reducedAncestorPVs = eliminateRoleGroupRedundancies(reducedAncestorPVs);
        }


        Set<OWLClassExpression> nonRedundantAncestors = new HashSet<OWLClassExpression>();
        nonRedundantAncestors.addAll(reducedParentNamedClasses);
        nonRedundantAncestors.addAll(reducedAncestorPVs);

        constructNecessaryDefinitionAxiom(inputClass, nonRedundantAncestors);

    }
}
