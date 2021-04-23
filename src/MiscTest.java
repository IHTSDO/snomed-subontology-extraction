import Classification.OntologyReasoningService;
import ExceptionHandlers.ReasonerException;
import ResultsWriters.OntologySaver;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.FunctionalSyntaxDocumentFormat;
import org.semanticweb.owlapi.formats.OWLXMLDocumentFormat;
import org.semanticweb.owlapi.io.OWLOntologyDocumentTarget;
import org.semanticweb.owlapi.model.*;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class MiscTest {

    public static void main(String[] args) throws OWLOntologyCreationException, OWLOntologyStorageException, ReasonerException {
        /*
        String inputPath = "E:/Users/warren/Documents/aPostdoc/subontologies/medicinal-products/";
        OWLOntology prevOntology = OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(new File(inputPath + "medicinal_products_prev.owl"));
        OWLOntology updatedOntology = OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(new File(inputPath + "medicinal_products_latest.owl"));

        System.out.println("Prev num axioms: " + prevOntology.getLogicalAxiomCount());
        System.out.println("Update num axioms: " + updatedOntology.getLogicalAxiomCount());

        OWLOntologyManager man = OWLManager.createOWLOntologyManager();
        OWLOntology diffInPrev = man.createOntology(prevOntology.getAxioms());
        OWLOntology diffInUpdate = man.createOntology(updatedOntology.getAxioms());

        man.removeAxioms(diffInPrev, updatedOntology.getLogicalAxioms());
        man.removeAxioms(diffInUpdate, prevOntology.getLogicalAxioms());

        OWLOntology diffOverNewClasses = man.createOntology(updatedOntology.getAxioms());
        Set<OWLClass> newClasses = updatedOntology.getClassesInSignature();
        newClasses.removeAll(prevOntology.getClassesInSignature());
        Set<OWLClass> oldClasses = prevOntology.getClassesInSignature();
        oldClasses.removeAll(newClasses);
        for(OWLAxiom ax:updatedOntology.getLogicalAxioms()) {
            if(!Collections.disjoint(ax.getClassesInSignature(), oldClasses)) {
                man.removeAxiom(diffOverNewClasses, ax);
            }
        }
        int i=0;
        for(OWLAxiom ax:diffInUpdate.getLogicalAxioms()) {
            if(Collections.disjoint(ax.getClassesInSignature(), newClasses)) {
                i++;
            }
        }
        System.out.println("Num new axioms containing no new classes: " + i);
        for(OWLClass cls:prevOntology.getClassesInSignature()) {
            int j=0;
            for(OWLAxiom ax:diffInUpdate.getLogicalAxioms()) {
                if(ax.getClassesInSignature().contains(cls)) {
                    j++;
                }
            }
            int k=0;
            for(OWLAxiom ax:diffInPrev.getLogicalAxioms()) {
                if(ax.getClassesInSignature().contains(cls)) {
                    k++;
                }
            }
            if(j-k > 5) {
                System.out.println("Diff in num of axioms for class: " + cls + " is: " + (j - k));
            }

        }
        //Set<OWLEntity> entitiesOutsideDiff = ontology
        //for(OWLEntity ent:)

        System.out.println("Number of additional axioms in updated ontology: " + (updatedOntology.getLogicalAxiomCount()-prevOntology.getLogicalAxiomCount()));
        System.out.println("Number of additional axioms over new classes: " + diffOverNewClasses.getLogicalAxiomCount());

        OntologySaver.saveOntology(diffInPrev,inputPath + "axioms_diff_inPrev.owl");
        OntologySaver.saveOntology(diffInUpdate,inputPath + "axioms_diff_inLatest.owl");
        OntologySaver.saveOntology(diffOverNewClasses, inputPath + "axioms_over_newClasses.owl");

         */
        OWLOntologyManager man = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = man.getOWLDataFactory();
        OWLOntology ont = man.createOntology();
        String IOR = "example.com/#";
        String IOR2 = "examples.com/#";
        OWLClass A1 = df.getOWLClass(IRI.create(IOR + "A1"));
        OWLClass A2 = df.getOWLClass(IRI.create(IOR + "A2"));
        OWLClass A3 = df.getOWLClass(IRI.create(IOR + "A3"));
        OWLClass A4 = df.getOWLClass(IRI.create(IOR + "A4"));
        OWLClass B1 = df.getOWLClass(IRI.create(IOR + "B1"));
        OWLClass B2 = df.getOWLClass(IRI.create(IOR + "B2"));
        OWLClass B3 = df.getOWLClass(IRI.create(IOR + "B3"));
        OWLClass B4 = df.getOWLClass(IRI.create(IOR + "B4"));
        OWLClass B5 = df.getOWLClass(IRI.create(IOR2 + "Bone Fracture"));
        OWLClass B5name = df.getOWLClass(IRI.create("Bone"));

        OWLSubClassOfAxiom ax1 = df.getOWLSubClassOfAxiom(A1, df.getOWLObjectIntersectionOf(A2, B1));
        OWLSubClassOfAxiom ax2 = df.getOWLSubClassOfAxiom(A2, df.getOWLObjectIntersectionOf(A3, B2));
        OWLSubClassOfAxiom ax3 = df.getOWLSubClassOfAxiom(A3, df.getOWLObjectIntersectionOf(A4, B3));
        OWLSubClassOfAxiom ax4 = df.getOWLSubClassOfAxiom(B1, B2);
        OWLSubClassOfAxiom ax5 = df.getOWLSubClassOfAxiom(B2, B3);
        OWLSubClassOfAxiom ax6 = df.getOWLSubClassOfAxiom(B3, B4);
        OWLSubClassOfAxiom ax7 = df.getOWLSubClassOfAxiom(B4, B5);

        man.addAxioms(ont, new HashSet<OWLAxiom>(Arrays.asList(ax1,ax2,ax3,ax4,ax5,ax6,ax7)));

        String outputPath = "E:/Users/warren/Documents/aPostdoc/module-vs-subontology-tests/";

        OntologyReasoningService reasoningService = new OntologyReasoningService(ont);
        //reasoningService.classifyOntology();
        System.out.println("Descendents B4: " + reasoningService.getAncestorClasses(B4).toString());
        System.out.println("Descendents B5: " + reasoningService.getDescendantClasses(B5name));

        //OntologySaver.saveOntology(ont, outputPath+"example2.owl");
    }
}
