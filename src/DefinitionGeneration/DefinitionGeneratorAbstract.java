package DefinitionGeneration;

import Classification.OntologyReasoningService;
import NamingApproach.PropertyValueNamer;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom;
import org.semanticweb.owlapi.model.OWLOntology;

import java.util.*;

public class DefinitionGeneratorAbstract extends DefinitionGenerator {

    public DefinitionGeneratorAbstract(OWLOntology inputOntology, OntologyReasoningService reasonerService, PropertyValueNamer namer) {
        super(inputOntology, reasonerService, namer);
    }

    //TODO: refactor, move to super (?)
    public void generateDefinition(OWLClass inputClass) {
        //default: all redundancy removed
        Set<RedundancyOptions> defaultOptions = new HashSet<RedundancyOptions>();
        defaultOptions.add(RedundancyOptions.eliminateLessSpecificRedundancy);
        defaultOptions.add(RedundancyOptions.eliminateReflexivePVRedundancy);
        defaultOptions.add(RedundancyOptions.eliminateRoleGroupRedundancy);
        this.generateDefinition(inputClass, defaultOptions);
        this.generateDefinition(inputClass, defaultOptions);
    }

    //TODO: move to super, code duplication with NNF
    public void generateDefinition(OWLClass inputClass, Set<RedundancyOptions> redundancyOptions) {
        Set<OWLClass> ancestors = reasonerService.getAncestorClasses(inputClass);
        Set<OWLClass> ancestorRenamedPVs = extractNamedPVs(ancestors);
        Set<OWLClass> primitiveAncestors = new HashSet<OWLClass>();
        primitiveAncestors.addAll(computeClosestPrimitiveAncestors(inputClass));

        primitiveAncestors.removeAll(ancestorRenamedPVs);

        Set<OWLClass> reducedParentNamedClasses = new HashSet<OWLClass>();
        Set<OWLObjectSomeValuesFrom> reducedAncestorPVs = new HashSet<OWLObjectSomeValuesFrom>();
        //if(redundancyOptions.contains(RedundancyOptions.eliminateReflexivePVRedundancy)) {
        //    Set<OWLObjectSomeValuesFrom> ancestorPVs = eliminateReflexivePVRedundancies(replaceNamesWithPVs(ancestorRenamedPVs), inputClass);
        //    ancestorRenamedPVs = replacePVsWithNames(ancestorPVs); //t
        //}
        if(redundancyOptions.contains(RedundancyOptions.eliminateLessSpecificRedundancy)) {
            reducedParentNamedClasses = reduceClassSet(primitiveAncestors);
            reducedAncestorPVs = replaceNamesWithPVs(reduceClassSet(ancestorRenamedPVs));
        }
        else {
            reducedParentNamedClasses = primitiveAncestors;
            reducedAncestorPVs = replaceNamesWithPVs(ancestorRenamedPVs);
        }
        if(redundancyOptions.contains(RedundancyOptions.eliminateRoleGroupRedundancy)) {
            reducedAncestorPVs = eliminateRoleGroupRedundancies(reducedAncestorPVs);
        }
        //TODO: needs to be done before rest of redundancy removal, due also to transitivity?
        if(redundancyOptions.contains(RedundancyOptions.eliminateReflexivePVRedundancy)) {
            reducedAncestorPVs = eliminateReflexivePVRedundancies(reducedAncestorPVs, inputClass);
        }

        Set<OWLClassExpression> nonRedundantAncestors = new HashSet<OWLClassExpression>();
        nonRedundantAncestors.addAll(reducedParentNamedClasses);
        nonRedundantAncestors.addAll(reducedAncestorPVs);

        constructDefinitionAxiom(inputClass, nonRedundantAncestors);
    }

    //possibly quicker than taking all primitive ancestors & redundancy checking?
    public Set<OWLClass> computeClosestPrimitiveAncestors(OWLClass classToDefine) {
        List<OWLClass> currentClassesToExpand = new ArrayList<OWLClass>();
        Set<OWLClass> closestPrimitives = new HashSet<OWLClass>();

        currentClassesToExpand.add(classToDefine);

        ListIterator<OWLClass> iterator = currentClassesToExpand.listIterator();
        while(iterator.hasNext()) {
            OWLClass cls = iterator.next();
            Set<OWLClass> parentClasses = reasonerService.getParentClasses(cls);
            Set<OWLClass> namedPVs = extractNamedPVs(parentClasses);
            parentClasses.removeAll(namedPVs);
            for(OWLClass parent:parentClasses) {
                //If is primitive, add to returned set
                if(reasonerService.isPrimitive(parent)) {
                    closestPrimitives.add(parent);
                    continue;
                }
                //If not primitive, add to check
                iterator.add(parent);
                iterator.previous();

            }
        }

        return closestPrimitives;
    }
}

