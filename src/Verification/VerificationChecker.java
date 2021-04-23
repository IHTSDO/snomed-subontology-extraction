package Verification;

import Classification.OntologyReasoningService;
import ExceptionHandlers.ReasonerException;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.util.OWLEntityRenamer;

import java.util.*;

public class VerificationChecker {
    private Map<OWLClass, Set<OWLClass>> latestSubOntologyClosureDiffs;
    private Map<OWLClass, Set<OWLClass>> latestSourceOntologyClosureDiffs;
    private OWLOntologyManager man;
    private OWLDataFactory df;

    public VerificationChecker() {
        latestSubOntologyClosureDiffs = new HashMap<OWLClass, Set<OWLClass>>();
        latestSourceOntologyClosureDiffs = new HashMap<OWLClass, Set<OWLClass>>();
        man = OWLManager.createOWLOntologyManager();
        df = man.getOWLDataFactory();
    }

    //TODO: refactor based on annotations for focus concepts
    public boolean satisfiesEquivalenceForFocusConcepts(Set<OWLClass> focusClasses, OWLOntology subOntology, OWLOntology sourceOntology) throws OWLOntologyCreationException {
        boolean satisfiesRequirement = true;
        //Takes as input the source ontology and a computed subontology.
        //TODO: rename the focus concepts in the subontology, store lookup for original name vs rename
        //TODO: add the renamed content to the source ontology, then check equivalence between original concepts and their subontology (renamed) versions
        //TODO: question -- is it sufficient to just add focus concept definitions from subontology to source?

        //OWLOntology subOntologyWithRenamings = man.createOntology(subOntology.getAxioms());

        OWLEntityRenamer renamer = new OWLEntityRenamer(man, new HashSet<OWLOntology>(Arrays.asList(subOntology)));
        for(OWLClass cls:focusClasses) {
            renamer.changeIRI(cls.getIRI(), IRI.create(cls.getIRI()+"_renaming"));
            System.out.println("New cls IRI: " + cls.getIRI());

        }





        return satisfiesRequirement;
    }

    public boolean satisfiesTransitiveClosureRequirement(OWLOntology subOntology, OWLOntology sourceOntology) throws ReasonerException {
        //compute the transitive closure wrt subsumption for all concepts within the subontology signature. Result wrt source and wrt sub should then be equal.
        boolean satisfiesRequirement = true;

        OntologyReasoningService sourceReasoner = new OntologyReasoningService(sourceOntology);
        sourceReasoner.classifyOntology();

        OntologyReasoningService subReasoner =  new OntologyReasoningService(subOntology);
        subReasoner.classifyOntology();

        //Obtain transitive closure for subOntology, store
        Map<OWLClass, Set<OWLClass>> subOntologyHierarchyMap = new HashMap<>(); //TODO: do we need equivalent classes nodeset as in OWLReasoner? Shouldn't for SCT.
        System.out.println("Getting hierarchy for subontology.");
        for(OWLClass cls:subOntology.getClassesInSignature()) {
            Set<OWLClass> children = subReasoner.getDirectSubClasses(cls);
            children.remove(df.getOWLNothing());
            subOntologyHierarchyMap.put(cls, children);
        }

        Map<OWLClass, Set<OWLClass>> sourceOntologyHierarchyMap = new HashMap<>();
        System.out.println("Getting hierarchy for source ontology, within subontology signature.");
        for(OWLClass cls:subOntology.getClassesInSignature()) {
            System.out.println("Checking transitive closure, computing source children for class: " + cls);
            List<OWLClass> nearestChildren = new ArrayList<OWLClass>(sourceReasoner.getDirectSubClasses(cls));
            ListIterator<OWLClass> childIterator = nearestChildren.listIterator();

            Set<OWLClass> nearestChildrenInSig = new HashSet<>();
            while(childIterator.hasNext()) {
                OWLClass child = childIterator.next();
                System.out.println("Current child: " + child);
                if(subOntology.getClassesInSignature().contains(child)) {
                    nearestChildrenInSig.add(child);
                    continue;
                }

                for(OWLClass nextDescendent:sourceReasoner.getDirectSubClasses(child)) {
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
