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
    private static String snomedIRI = "http://snomed.info/id/";
    //private OWLOntologyManager man;
    //private OWLDataFactory df;

    public VerificationChecker() {
        latestSubOntologyClosureDiffs = new HashMap<OWLClass, Set<OWLClass>>();
        latestSourceOntologyClosureDiffs = new HashMap<OWLClass, Set<OWLClass>>();
        nonEquivalentFocusClasses = new HashSet<OWLClass>();
        //man = OWLManager.createOWLOntologyManager();
        //df = man.getOWLDataFactory();
    }

     //TODO: temp test ver
    public boolean namedFocusConceptsSatisfyEquivalence(Set<OWLClass> focusClasses, OWLOntology subOntology, OWLOntology sourceOntology) throws OWLOntologyCreationException, ReasonerException, OWLOntologyStorageException {
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

        //group focus concepts into primitive and named
        OntologyReasoningService reasoningService = new OntologyReasoningService(sourceOntologyWithRenamedSubOntology);
        reasoningService.classifyOntology();
        Set<OWLClass> primitiveFocusClasses = new HashSet<OWLClass>();
        Set<OWLClass> namedFocusClasses = new HashSet<OWLClass>();
        for(OWLClass cls:focusClasses) {
            if(reasoningService.isPrimitive(cls)) {
                primitiveFocusClasses.add(cls);
            }
            else {
                namedFocusClasses.add(cls);
            }
        }

        //named case
        for(OWLClass focusCls:namedFocusClasses) {
            System.out.println("Named focus classes covered: " + i + "/"+namedFocusClasses.size() + " num failed: " + namedFailures.size());

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

            boolean isEquivalent = false;
            //TODO: split methods + improve readability
            reasoningService.setNewSourceOntologyAndClassify(sourceOntologyWithRenamedSubOntology);

            System.out.println("Named focus: " + focusCls);
            System.out.println("and equivs: " + reasoningService.getEquivalentClasses(focusCls));

            if(reasoningService.getEquivalentClasses(focusCls).contains(focusClassRenamingMap.get(focusCls))) {
                isEquivalent = true;
            }
            //if(!reasoningService.getEquivalentClasses(focusCls).contains(focusClassRenamingMap.get(focusCls))) {
            if(!isEquivalent) {
                System.out.println("Focus cls not equivalent to renaming: " + focusCls);
                nonEquivalentCases.add(focusCls);
                satisfiesRequirement = false;
                namedFailures.add(focusCls);
            }

            //reset the sourceOntology with renamings instance
            man.removeAxioms(sourceOntologyWithRenamedSubOntology, addedRenamingAxioms);
            i++;
            man.removeOntology(subOntologyRenamed);
        }

        //primitive case
        /* //TODO: test 1 -- failed
        i=0;
        for(OWLClass focusCls:primitiveFocusClasses) {
            System.out.println("Primitive classes covered: " + i + "/"+primitiveFocusClasses.size() + " num failed: " + primitiveFailures.size());

            //create ontology instance to rename class in
            OWLOntology subOntologyRenamed = man.createOntology(subOntology.getAxioms());
            OWLEntityRenamer renamer = new OWLEntityRenamer(man, new HashSet<OWLOntology>(Arrays.asList(subOntologyRenamed)));
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

            boolean isEquivalent = false;
            //TODO: split methods + improve readability

            OWLClass equivClassFocus = df.getOWLClass(IRI.create(focusCls.getIRI() + "_equiv"));
            OWLClass equivClassRenamedFocus = df.getOWLClass(IRI.create(focusClassRenamingMap.get(focusCls).getIRI() + "_equiv"));
            OWLEquivalentClassesAxiom equivFocus = df.getOWLEquivalentClassesAxiom(focusCls, equivClassFocus);
            //OWLEquivalentClassesAxiom equivRenamedFocus = df.getOWLEquivalentClassesAxiom(focusClassRenamingMap.get(focusCls), equivClassRenamedFocus);

           // man.addAxioms(sourceOntologyWithRenamedSubOntology, new HashSet<OWLAxiom>(Arrays.asList(equivFocus, equivRenamedFocus)));
           // addedRenamingAxioms.add(equivFocus);
           // addedRenamingAxioms.add(equivRenamedFocus);

            //classify the source ontology with these new renaming axioms
            reasoningService.setNewSourceOntologyAndClassify(sourceOntologyWithRenamedSubOntology);

            System.out.println("Primitive focus: " + focusCls);
            System.out.println("Named equiv: " + equivClassFocus + " and equivs: " + reasoningService.getEquivalentClasses(equivClassFocus));

            //compare the two introduced equivalent classes
            if(reasoningService.getEquivalentClasses(equivClassFocus).contains(equivClassRenamedFocus)) {
                isEquivalent = true;
            }
            //if(!reasoningService.getEquivalentClasses(focusCls).contains(focusClassRenamingMap.get(focusCls))) {
            if(!isEquivalent) {
                System.out.println("Focus cls not equivalent to renaming: " + focusCls);
                nonEquivalentCases.add(focusCls);
                satisfiesRequirement = false;
                primitiveFailures.add(focusCls);
            }

            //reset the sourceOntology with renamings instance
            man.removeAxioms(sourceOntologyWithRenamedSubOntology, addedRenamingAxioms);
            i++;
            man.removeOntology(subOntologyRenamed);
        }
         */

        //primitive case //TODO: test 2 - does not work either.
        for(OWLClass focusCls:primitiveFocusClasses) {
            System.out.println("Primitive classes covered: " + i + "/" + primitiveFocusClasses.size() + " num failed: " + primitiveFailures.size());

            //create ontology instance to rename class in
            OWLOntology subOntologyRenamed = man.createOntology(subOntology.getAxioms());
            OWLEntityRenamer renamer = new OWLEntityRenamer(man, new HashSet<OWLOntology>(Arrays.asList(subOntologyRenamed)));
            subOntologyRenamed.getOWLOntologyManager().applyChanges(renamer.changeIRI(focusCls, IRI.create(focusCls.getIRI() + "_renamed")));
            focusClassRenamingMap.put(focusCls, df.getOWLClass(IRI.create(focusCls.getIRI() + "_renamed")));

            //add renaming axioms for the given focus class only
            Set<OWLAxiom> addedRenamingAxioms = new HashSet<OWLAxiom>();
            for (OWLAxiom ax : subOntologyRenamed.getAxioms()) {
                //if axiom contains occurrence of the renaming, add it
                if (ax.getClassesInSignature().contains(focusClassRenamingMap.get(focusCls))) {
                    addedRenamingAxioms.add(ax);
                }
            }
            man.addAxioms(sourceOntologyWithRenamedSubOntology, addedRenamingAxioms);

            OWLNamedIndividual a = df.getOWLNamedIndividual(IRI.create(snomedIRI + "a"));
            OWLClassAssertionAxiom as1 = df.getOWLClassAssertionAxiom(df.getOWLObjectIntersectionOf(df.getOWLObjectComplementOf(focusCls), focusClassRenamingMap.get(focusCls)), a);
            OWLClassAssertionAxiom as2 = df.getOWLClassAssertionAxiom(df.getOWLObjectIntersectionOf(df.getOWLObjectComplementOf(focusClassRenamingMap.get(focusCls)), focusCls), a);

            //test direction 1
            boolean direction1 = false;
            man.addAxiom(sourceOntologyWithRenamedSubOntology, as1);
            reasoningService.setNewSourceOntologyAndClassify(sourceOntologyWithRenamedSubOntology);

            direction1 = reasoningService.isConsistent();

            //test direction 2
            boolean direction2 = false;
            man.removeAxiom(sourceOntologyWithRenamedSubOntology, as1);
            man.addAxiom(sourceOntologyWithRenamedSubOntology, as2);

            reasoningService.setNewSourceOntologyAndClassify(sourceOntologyWithRenamedSubOntology);
            direction2 = reasoningService.isConsistent();

            if(direction1 || direction2) { //then not equivalent?
                System.out.println("Focus cls not equivalent to renaming: " + focusCls);
                nonEquivalentCases.add(focusCls);
                satisfiesRequirement = false;
                primitiveFailures.add(focusCls);
            }
        }

        nonEquivalentFocusClasses = nonEquivalentCases;

        System.out.println("Total failures: " + nonEquivalentCases.size()+"/"+focusClasses.size());
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
