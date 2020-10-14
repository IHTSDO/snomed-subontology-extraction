package DefinitionGeneration;

import Classification.OntologyReasoningService;
import RenamingApproach.PropertyValueRenamer;
import org.semanticweb.owlapi.model.*;

import java.util.*;

public class DefinitionGeneratorNNF extends DefinitionGenerator {


    public DefinitionGeneratorNNF(OWLOntology inputOntology, OntologyReasoningService reasonerService, PropertyValueRenamer renamer) {
        super(inputOntology, reasonerService, renamer);
    }

    @Override
    //TODO: make version of this for groups of signatures -- identify everything that can be done once, and only do it once (efficiency). Top down?
    public void generateDefinition(OWLClass inputClass) {
        Set<OWLClass> ancestors = reasonerService.getAncestorClasses(inputClass);
        System.out.println("Class: " + inputClass + ", ancestors: " + ancestors);
        Set<OWLClass> ancestorRenamedPVs = extractRenamedPVs(ancestors);
        System.out.println("Class: " + inputClass + ", ancestor PVs: " + ancestorRenamedPVs);

        Set<OWLClass> parentNamedClasses = new HashSet<OWLClass>();
        parentNamedClasses.addAll(reasonerService.getParentClasses(inputClass));

        parentNamedClasses.removeAll(ancestorRenamedPVs); //TODO: needs testing.

        Set<OWLClass> reducedParentNamedClasses = reduceClassSet(parentNamedClasses);
        Set<OWLObjectSomeValuesFrom> reducedAncestorPVs = replaceNamesWithPVs(reduceClassSet(ancestorRenamedPVs));

        Set<OWLClassExpression> nonRedundantAncestors = new HashSet<OWLClassExpression>();
        nonRedundantAncestors.addAll(reducedParentNamedClasses);
        nonRedundantAncestors.addAll(reducedAncestorPVs);

        constructNecessaryDefinitionAxiom(inputClass, nonRedundantAncestors);
    }
}
