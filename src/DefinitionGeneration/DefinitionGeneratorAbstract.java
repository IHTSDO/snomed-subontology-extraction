package DefinitionGeneration;

import Classification.OntologyReasoningService;
import NamingApproach.IntroducedNameHandler;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom;
import org.semanticweb.owlapi.model.OWLOntology;

import java.util.*;

public class DefinitionGeneratorAbstract extends DefinitionGenerator {

    public DefinitionGeneratorAbstract(OWLOntology inputOntology, OntologyReasoningService reasonerService, IntroducedNameHandler namer) {
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
        //separate ancestors into classes and PVs (represented by new name classes)
        Set<OWLClass> ancestors = reasonerService.getAncestors(inputClass);
        Set<OWLClass> ancestorRenamedPVs = extractNamedPVs(ancestors);
        Set<OWLClass> primitiveAncestors = new HashSet<OWLClass>();
        primitiveAncestors.addAll(computeClosestPrimitiveAncestors(inputClass));

        //remove classes representing introduced names
        primitiveAncestors.removeAll(ancestorRenamedPVs);
        primitiveAncestors.removeAll(extractNamedGCIs(primitiveAncestors)); //TODO: 09-04-2021, not needed?

        //GCI handling: computing authoring form of GCI requires naming the LHS, meaning GCIName <= originalGCIClass, which is undesirable.
        if(namer.isNamedGCI(inputClass)) {
            OWLClass originalGCIConcept = namer.retrieveSuperClassFromNamedGCI(inputClass);
            primitiveAncestors.remove(originalGCIConcept);
            primitiveAncestors.addAll(computeClosestPrimitiveAncestors(originalGCIConcept));
        }

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
        if(redundancyOptions.contains(RedundancyOptions.eliminateReflexivePVRedundancy)) {
            reducedAncestorPVs = eliminateReflexivePVRedundancies(inputClass, reducedAncestorPVs);
        }
        if(redundancyOptions.contains(RedundancyOptions.eliminateSufficientProximalGCIs)) {
            reducedParentNamedClasses = eliminateSufficientProximalGCIConcepts(inputClass, reducedParentNamedClasses);
        }

        Set<OWLClassExpression> nonRedundantAncestors = new HashSet<OWLClassExpression>();
        nonRedundantAncestors.addAll(reducedParentNamedClasses);
        nonRedundantAncestors.addAll(reducedAncestorPVs);

        constructDefinitionAxiom(inputClass, nonRedundantAncestors);
    }

    //possibly quicker than taking all primitive ancestors & redundancy checking?
    private Set<OWLClass> computeClosestPrimitiveAncestors(OWLClass classToDefine) {
        List<OWLClass> currentClassesToExpand = new ArrayList<OWLClass>();
        Set<OWLClass> closestPrimitives = new HashSet<OWLClass>();

        currentClassesToExpand.add(classToDefine);

        ListIterator<OWLClass> iterator = currentClassesToExpand.listIterator();
        while(iterator.hasNext()) {
            OWLClass cls = iterator.next();
            Set<OWLClass> parentClasses = reasonerService.getDirectAncestors(cls);
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

    //type 1 and type 2 GCI subconcepts:
    //      *type 1: classToDefine is subconcept of a sufficiency condition of a GCI concept. Here: replace GCI concept with next proximal primitives
    //      *type 2: classToDefine is subconcept of a necessary condition of a GCI concept. Here: normal authoring form, no change.
    private Set<OWLClass> eliminateSufficientProximalGCIConcepts(OWLClass classToDefine, Set<OWLClass> parentClasses) {
        Set<OWLClass> newProximalPrimitiveParents = new HashSet<>();
        ListIterator<OWLClass> parentIterator = new ArrayList<>(parentClasses).listIterator();

        //for(OWLClass parent:parentClasses) {
        while(parentIterator.hasNext()) {
            OWLClass parent = parentIterator.next();
            //for each parent A, check if occurs in axiom of form C <= A, where C is a complex concept
            if(namer.hasAssociatedGCIs(parent)) {
               //for each associated GCI, check type 1 or type 2 relationship for classToDefine
                boolean isTypeOne = false;
                for(OWLClass gciName:namer.returnNamesOfGCIsForSuperConcept(parent)) {
                    if(reasonerService.getAncestors(classToDefine).contains(gciName)) {
                        //type 1 -- add proximal primitives of GCI concept to parents for classToDefine, replacing GCI concept.
                        isTypeOne = true;
                        Set<OWLClass> gciProximalPrimitives = computeClosestPrimitiveAncestors(parent);
                        for(OWLClass proximalPrimitive:gciProximalPrimitives) {
                            newProximalPrimitiveParents.add(proximalPrimitive);
                            parentIterator.previous();
                        }
                        break;
                    }
                }
                //type 2 -- retain GCI concept in proximal primitive parent set
                if(!isTypeOne) {
                    newProximalPrimitiveParents.add(parent);
                    continue;
                }
            }
            //if not GCI concept, retain proximal primitive parent
            newProximalPrimitiveParents.add(parent);
        }

        return newProximalPrimitiveParents;
    }

}

