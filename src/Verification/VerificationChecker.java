package Verification;

import Classification.OntologyReasoningService;
import ExceptionHandlers.ReasonerException;
import ResultsWriters.OntologySaver;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.util.OWLEntityRenamer;

import java.util.*;

public class VerificationChecker {
    private Map<OWLClass, Set<OWLClass>> latestSubOntologyClosureDiffs;
    private Map<OWLClass, Set<OWLClass>> latestSourceOntologyClosureDiffs;
    private Set<OWLClass> nonEquivalentFocusClasses;
    //private OWLOntologyManager man;
    //private OWLDataFactory df;

    public VerificationChecker() {
        latestSubOntologyClosureDiffs = new HashMap<OWLClass, Set<OWLClass>>();
        latestSourceOntologyClosureDiffs = new HashMap<OWLClass, Set<OWLClass>>();
        nonEquivalentFocusClasses = new HashSet<OWLClass>();
        //man = OWLManager.createOWLOntologyManager();
        //df = man.getOWLDataFactory();
    }

    /*
    public boolean satisfiesEquivalenceForFocusConcepts(Set<OWLClass> focusClasses, OWLOntology subOntology, OWLOntology sourceOntology) throws OWLOntologyCreationException, ReasonerException, OWLOntologyStorageException {
        OWLOntologyManager man = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = man.getOWLDataFactory();
        boolean satisfiesRequirement = true;

        OWLOntology subOntologyRenamed = man.createOntology();
        man.addAxioms(subOntologyRenamed, subOntology.getAxioms());

        //rename all focus concepts in subontology
        Map<OWLClass, OWLClass> focusClassRenamingMap = new HashMap<OWLClass, OWLClass>();
        OWLEntityRenamer renamer = new OWLEntityRenamer(man, new HashSet<OWLOntology>(Arrays.asList(subOntologyRenamed)));

        for(OWLClass cls:focusClasses) {
            System.out.println("Old cls IRI: " + cls.getIRI());
            subOntologyRenamed.getOWLOntologyManager().applyChanges(renamer.changeIRI(cls, IRI.create(cls.getIRI()+"_renamed")));
            focusClassRenamingMap.put(cls, df.getOWLClass(IRI.create(cls.getIRI()+"_renamed"))); //TODO: confirm this works as intended
        }

        //add all renaming axioms to source ontology. Note: begin by adding all axioms, since we require all non-logical axioms also, then remove irrelevant (non-focus/renamed) ones.
        OWLOntology sourceOntologyWithRenamedSubOntology = man.createOntology();
        man.addAxioms(sourceOntologyWithRenamedSubOntology, sourceOntology.getAxioms());
        man.addAxioms(sourceOntologyWithRenamedSubOntology, subOntologyRenamed.getAxioms());
        for(OWLAxiom ax:subOntologyRenamed.getAxioms()) {
            if(Collections.disjoint(ax.getClassesInSignature(), focusClasses)) {
                man.removeAxiom(sourceOntology, ax);
            }
        }

        OntologySaver.saveOntology(subOntologyRenamed, "E:/Users/warren/Documents/aPostdoc/subontologies/subOntologyRenamed.owl");
        OntologySaver.saveOntology(sourceOntologyWithRenamedSubOntology, "E:/Users/warren/Documents/aPostdoc/subontologies/sourceWithRenamings.owl");
        OWLOntology testOnt = man.createOntology();
        man.addAxioms(testOnt, subOntology.getAxioms());
        man.addAxioms(testOnt, subOntologyRenamed.getAxioms());
        OntologySaver.saveOntology(testOnt, "E:/Users/warren/Documents/aPostdoc/subontologies/subAndRenamingsTest.owl");


        //check equivalence for each focus concept.
        OntologyReasoningService reasoningService = new OntologyReasoningService(sourceOntologyWithRenamedSubOntology);
        reasoningService.classifyOntology();

        Set<OWLClass> nonEquivalentCases = new HashSet<OWLClass>();
        for(OWLClass focusCls:focusClasses) {
            //System.out.println("Focus cls: " + focusCls + " renaming: " + focusClassRenamingMap.get(focusCls));
            System.out.println("Focus cls equivalent cases: " + reasoningService.getEquivalentClasses(focusCls));
            System.out.println("Focus cls descendants: " + reasoningService.getDescendants(focusCls));
            System.out.println("Focus cls ancestors: " + reasoningService.getAncestors(focusCls));
            if(!reasoningService.getEquivalentClasses(focusCls).contains(focusClassRenamingMap.get(focusCls))) {
                System.out.println("Focus cls not equivalent to renaming: " + focusCls);
                nonEquivalentCases.add(focusCls);
                satisfiesRequirement = false;
            }
        }

        nonEquivalentFocusClasses = nonEquivalentCases;
        Set<OWLClass> primitiveFailures = new HashSet<OWLClass>();
        Set<OWLClass> namedFailures = new HashSet<OWLClass>();

        for(OWLClass cls:nonEquivalentCases) {
            if(reasoningService.isPrimitive(cls)) {
                primitiveFailures.add(cls);
            }
            else {
                namedFailures.add(cls);
            }
        }

        System.out.println("Of failures, num named: " + namedFailures.size());
        System.out.println("named failures: " + namedFailures);
        System.out.println("Of failures, num primitive: " + primitiveFailures.size());
        System.out.println("primitive failures: " + primitiveFailures);


        return satisfiesRequirement;
    }
     */

     //TODO: temp test ver
    public boolean satisfiesEquivalenceForFocusConcepts(Set<OWLClass> focusClasses, OWLOntology subOntology, OWLOntology sourceOntology) throws OWLOntologyCreationException, ReasonerException, OWLOntologyStorageException {
        OWLOntologyManager man = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = man.getOWLDataFactory();
        boolean satisfiesRequirement = true;

        //rename all focus concepts in subontology -- create instance to contain all renamings to draw from
        Map<OWLClass, OWLClass> focusClassRenamingMap = new HashMap<OWLClass, OWLClass>();

        //create instance of source ontology, ready for renaming additions
        OWLOntology sourceOntologyWithRenamedSubOntology = man.createOntology();
        man.addAxioms(sourceOntologyWithRenamedSubOntology, sourceOntology.getAxioms());

        Set<OWLClass> primitiveFailures = new HashSet<OWLClass>();
        Set<OWLClass> namedFailures = new HashSet<OWLClass>();

        Set<OWLClass> nonEquivalentCases = new HashSet<OWLClass>();
        int i = 0;
        OntologyReasoningService reasoningService = new OntologyReasoningService(sourceOntologyWithRenamedSubOntology);
        for(OWLClass focusCls:focusClasses) {
            System.out.println("Classes covered: " + i + "/"+focusClasses.size() + " num failed, primitive: " + primitiveFailures.size() + ", named: " + namedFailures.size());

            //create ontology instance to rename class in
            OWLOntology subOntologyRenamed = man.createOntology(subOntology.getAxioms());
            OWLEntityRenamer renamer = new OWLEntityRenamer(man, new HashSet<OWLOntology>(Arrays.asList(subOntologyRenamed)));
            System.out.println("Old cls IRI: " + focusCls.getIRI());
            subOntologyRenamed.getOWLOntologyManager().applyChanges(renamer.changeIRI(focusCls, IRI.create(focusCls.getIRI()+"_renamed")));
            focusClassRenamingMap.put(focusCls, df.getOWLClass(IRI.create(focusCls.getIRI()+"_renamed")));

            //add renaming axioms for the given focus class only
            Set<OWLAxiom> addedRenamingAxioms = new HashSet<OWLAxiom>();
            for(OWLAxiom ax:subOntologyRenamed.getAxioms()) {
                //if axiom contains occurrence of the renaming, add it
                if(ax.getClassesInSignature().contains(focusClassRenamingMap.get(focusCls))) {
                    addedRenamingAxioms.add(ax);
                }
            }
            man.addAxioms(sourceOntologyWithRenamedSubOntology, addedRenamingAxioms);

            //classify the source ontology with these new renaming axioms
            reasoningService.setNewSourceOntologyAndClassify(sourceOntologyWithRenamedSubOntology);
            
            if(!reasoningService.getEquivalentClasses(focusCls).contains(focusClassRenamingMap.get(focusCls))) {
                System.out.println("Focus cls not equivalent to renaming: " + focusCls);
                nonEquivalentCases.add(focusCls);
                satisfiesRequirement = false;
                if(reasoningService.isPrimitive(focusCls)) {
                    primitiveFailures.add(focusCls);
                }
                else {
                    namedFailures.add(focusCls);
                }
            }

            //reset the sourceOntology with renamings instance
            man.removeAxioms(sourceOntologyWithRenamedSubOntology, addedRenamingAxioms);
            i++;
            man.removeOntology(subOntologyRenamed);

        }
        nonEquivalentFocusClasses = nonEquivalentCases;

        System.out.println("Of failures, num named: " + namedFailures.size());
        System.out.println("named failures: " + namedFailures);
        System.out.println("Of failures, num primitive: " + primitiveFailures.size());
        System.out.println("primitive failures: " + primitiveFailures);


        return satisfiesRequirement;
    }

    public boolean satisfiesTransitiveClosureRequirement(OWLOntology subOntology, OWLOntology sourceOntology) throws ReasonerException {
        OWLOntologyManager man = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = man.getOWLDataFactory();
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
            Set<OWLClass> children = subReasoner.getDirectDescendants(cls);
            children.remove(df.getOWLNothing());
            subOntologyHierarchyMap.put(cls, children);
        }

        Map<OWLClass, Set<OWLClass>> sourceOntologyHierarchyMap = new HashMap<>();
        System.out.println("Getting hierarchy for source ontology, within subontology signature.");
        for(OWLClass cls:subOntology.getClassesInSignature()) {
            System.out.println("Checking transitive closure, computing source children for class: " + cls);
            List<OWLClass> nearestChildren = new ArrayList<OWLClass>(sourceReasoner.getDirectDescendants(cls));
            ListIterator<OWLClass> childIterator = nearestChildren.listIterator();

            Set<OWLClass> nearestChildrenInSig = new HashSet<>();
            while(childIterator.hasNext()) {
                OWLClass child = childIterator.next();
                System.out.println("Current child: " + child);
                //if in signature, store
                if(subOntology.getClassesInSignature().contains(child)) {
                    nearestChildrenInSig.add(child);
                    continue;
                }
                //if not in signature, look to next level of descendants
                for(OWLClass nextDescendent:sourceReasoner.getDirectDescendants(child)) {
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
    public Set<OWLClass> getFailedFocusClassEquivalenceCases() {
        return nonEquivalentFocusClasses;
    }
}
