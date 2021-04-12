package Verification;

import Classification.OntologyReasoningService;
import ExceptionHandlers.ReasonerException;
import ResultsWriters.MapPrinter;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import java.util.*;

public class VerificationChecker {
    private Map<OWLClass, Set<OWLClass>> latestSubOntologyClosureDiffs;
    private Map<OWLClass, Set<OWLClass>> latestSourceOntologyClosureDiffs;

    public VerificationChecker() {
        latestSubOntologyClosureDiffs = new HashMap<OWLClass, Set<OWLClass>>();
        latestSourceOntologyClosureDiffs = new HashMap<OWLClass, Set<OWLClass>>();
    }

    public boolean satisfiesEquivalenceForFocusConcepts(OWLOntology subOntology, OWLOntology sourceOntology) {
        boolean satisfiesRequirement = true;
        //Takes as input the source ontology and a computed subontology.
        //TODO: rename the focus concepts in the subontology, store lookup for original name vs rename
        //TODO: add the renamed content to the source ontology, then check equivalence between original concepts and their subontology (renamed) versions
        //TODO: question -- is it sufficient to just add focus concept definitions from subontology to source?
        return satisfiesRequirement;
    }

    public boolean satisfiesTransitiveClosureRequirement(OWLOntology subOntology, OWLOntology sourceOntology) throws ReasonerException {
        //TODO: here, need to compute the transitive closure wrt subsumption for all concepts within the subontology signature. Result wrt source and wrt sub should then be equal.
        //TODO: main question, how best to do this for the source ontology?
        boolean satisfiesRequirement = true;

        OntologyReasoningService sourceReasoner = new OntologyReasoningService(sourceOntology);
        sourceReasoner.classifyOntology();

        OntologyReasoningService subReasoner =  new OntologyReasoningService(subOntology);
        subReasoner.classifyOntology();

        OWLOntologyManager man = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = man.getOWLDataFactory();
        //Obtain transitive closure for subOntology, store
        Map<OWLClass, Set<OWLClass>> subOntologyHierarchyMap = new HashMap<>(); //TODO: do we need equivalent classes nodeset as in OWLReasoner? Shouldn't for SCT.
        System.out.println("Getting hierarchy for subontology.");
        for(OWLClass cls:subOntology.getClassesInSignature()) {
            Set<OWLClass> children = subReasoner.getChildClasses(cls);
            children.remove(df.getOWLNothing());
            subOntologyHierarchyMap.put(cls, children);
        }

        Map<OWLClass, Set<OWLClass>> sourceOntologyHierarchyMap = new HashMap<>();
        System.out.println("Getting hierarchy for source ontology, within subontology signature.");
        for(OWLClass cls:subOntology.getClassesInSignature()) {
            List<OWLClass> nearestChildren = new ArrayList<OWLClass>(sourceReasoner.getChildClasses(cls));
            ListIterator<OWLClass> childIterator = nearestChildren.listIterator();

            Set<OWLClass> nearestChildrenInSig = new HashSet<>();
            while(childIterator.hasNext()) {
                OWLClass child = childIterator.next();
                if(subOntology.getClassesInSignature().contains(child)) {
                    nearestChildrenInSig.add(child);
                    continue;
                }

                for(OWLClass nextDescendent:sourceReasoner.getChildClasses(child)) {
                    childIterator.add(nextDescendent);
                    childIterator.previous();
                }
            }

            nearestChildrenInSig = sourceReasoner.eliminateStrongerClasses(nearestChildrenInSig); //TODO: needed??
            sourceOntologyHierarchyMap.put(cls, nearestChildrenInSig);
            nearestChildrenInSig.remove(df.getOWLNothing());
        }

        //perform check
        Map<OWLClass, Set<OWLClass>> diffInSubOnt = new HashMap<OWLClass, Set<OWLClass>>();
        Map<OWLClass, Set<OWLClass>> diffInSourceOnt = new HashMap<OWLClass, Set<OWLClass>>();

        int numCasesNotSatisfied = 0;
        for(OWLClass cls:subOntology.getClassesInSignature()) {
            if(!subOntologyHierarchyMap.get(cls).equals(sourceOntologyHierarchyMap.get(cls))) {
                System.out.println("Requirement not satisfied for class: " + cls);
                Set<OWLClass> diffSubOntClasses = subOntologyHierarchyMap.get(cls);
                diffSubOntClasses.removeAll(sourceOntologyHierarchyMap.get(cls));
                Set<OWLClass> diffSourceOntClasses = sourceOntologyHierarchyMap.get(cls);
                diffSourceOntClasses.removeAll(subOntologyHierarchyMap.get(cls));

                diffInSubOnt.put(cls, diffSubOntClasses);
                diffInSourceOnt.put(cls, diffSourceOntClasses);
                satisfiesRequirement = false;
                numCasesNotSatisfied++;
            }
        }
        System.out.println("Num differing: " + numCasesNotSatisfied);
        System.out.println("Differing classes, sub: " + diffInSubOnt.keySet());
        System.out.println("Differing classes, source: " + diffInSourceOnt.keySet());

        latestSubOntologyClosureDiffs = diffInSubOnt;
        latestSourceOntologyClosureDiffs = diffInSourceOnt;

        return satisfiesRequirement;
    }

    public Map<OWLClass, Set<OWLClass>> getLatestSubOntologyClosureDiffs() {
        return latestSubOntologyClosureDiffs;
    }
    public Map<OWLClass, Set<OWLClass>> getLatestSourceOntologyClosureDiffs() {
        return latestSourceOntologyClosureDiffs;
    }
}
