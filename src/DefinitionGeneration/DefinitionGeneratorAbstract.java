package DefinitionGeneration;

import Classification.OntologyReasoningService;
import NamingApproach.PropertyValueNamer;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom;
import org.semanticweb.owlapi.model.OWLOntology;

import java.util.*;

public class DefinitionGeneratorAbstract extends DefinitionGenerator {

    //private Set<OWLClass> closestPrimitiveAncestors;

    public DefinitionGeneratorAbstract(OWLOntology inputOntology, OntologyReasoningService reasonerService, PropertyValueNamer namer) {
        super(inputOntology, reasonerService, namer);
    }

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

    //possibly quicker than taking all primitive ancestors & redundancy checking?
    public Set<OWLClass> computeClosestPrimitiveAncestors(OWLClass classToDefine) {
        List<OWLClass> currentClassesToExpand = new ArrayList<OWLClass>();
        Set<OWLClass> closestPrimitives = new HashSet<OWLClass>();

        currentClassesToExpand.add(classToDefine);
        ListIterator<OWLClass> iterator = currentClassesToExpand.listIterator();
        while(iterator.hasNext()) {
            OWLClass cls = iterator.next();
            Set<OWLClass> parentClasses = reasonerService.getParentClasses(cls);
            for(OWLClass parent:parentClasses) {
                if(isPrimitive(parent) == true) {
                    closestPrimitives.add(parent);
                    continue;
                }
                iterator.add(parent);
            }
        }

        return closestPrimitives;
    }

    public boolean isPrimitive(OWLClass cls) {
        //TODO: for full SCT, could do this using fullyDefined IDs as in toolkit? Quicker?
        if(backgroundOntology.getEquivalentClassesAxioms(cls).isEmpty()) {
            return true;
        }
        return false;
    }

}

