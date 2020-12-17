package tools;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.OWLXMLDocumentFormat;
import org.semanticweb.owlapi.model.*;

import java.io.File;
import java.util.*;

//TODO: refactor this. Quick solution.
//TODO: add flag to optionally call this during definition generation &/or printing.
public class CheckComplexNesting {

    public static void main(String[] args) throws OWLOntologyCreationException, OWLOntologyStorageException {
        OWLOntologyManager man = OWLManager.createOWLOntologyManager();

        String inputFilePath = "E:/Users/warren/Documents/aPostdoc/code/~test-code/abstract-definitions-test/anatomy-module/anatomy.owl";
        String outputFilePath = "E:/Users/warren/Documents/aPostdoc/code/~test-code/abstract-definitions-test/anatomy-module/complexAxioms.owl";

        OWLOntology ont = man.loadOntologyFromOntologyDocument(IRI.create(new File(inputFilePath)));

        Set<OWLAxiom> inputAxioms = ont.getAxioms();

        Set<OWLAxiom> axiomsWithComplexNesting = CheckComplexNesting.returnAxiomsWithComplexNestingInSuperClass(inputAxioms);

        OWLOntology complexNestingOnt = man.createOntology(axiomsWithComplexNesting);
        for(OWLAxiom ax:ont.getAxioms()) {
            if(ax instanceof OWLAnnotationAssertionAxiom) {
                man.addAxiom(complexNestingOnt, ax);
            }
        }

        System.out.println("complex axioms" + axiomsWithComplexNesting);
        man.saveOntology(complexNestingOnt, new OWLXMLDocumentFormat(), IRI.create(new File(outputFilePath)));
    }

    //NOTE: this method searches for any axiom of the form A <= B or A == B such that
    //      B contains a concept of the form R some C where C contains at least one complex concept (such as S some C) and R is not
    //      the role group symbol.
    public static Set<OWLAxiom> returnAxiomsWithComplexNestingInSuperClass(Set<OWLAxiom> inputSet) {
        Set<OWLAxiom>  axiomsWithNestedPVs = new HashSet<OWLAxiom>();

        for(OWLAxiom ax:inputSet) {
            System.out.println("Checking for complex nesting in axiom: " + ax);
            if(ax instanceof OWLEquivalentClassesAxiom) {
                Set<OWLSubClassOfAxiom> axs = ((OWLEquivalentClassesAxiom)ax).asOWLSubClassOfAxioms();
                for(OWLSubClassOfAxiom subAxiom:axs) {
                    if(superClassContainsComplexNesting(subAxiom)) {
                        axiomsWithNestedPVs.add(ax);
                        break;
                    }
                }
            }
            else if(ax instanceof OWLSubClassOfAxiom) {
                if(superClassContainsComplexNesting((OWLSubClassOfAxiom)ax)) {
                    axiomsWithNestedPVs.add(ax);
                }
            }
        }
        return axiomsWithNestedPVs;
    }

    //      Assumption: since the language is an EL fragment, we can check for ObjectSomeValueFrom in the filler.
    //                : also assume Role Group is represented as a property with ID "609096000"
    //      TODO: does checking filler.getNestedClassExpressions for the presence of OWLObjectSomeValuesFrom sufficiently check nesting?
    //      case 1: RG some (R some (S some C)), case 2: R some (S some C). Second case simpler.
    private static boolean superClassContainsComplexNesting(OWLSubClassOfAxiom ax) {
        OWLClassExpression superClass = ax.getSuperClass();
        Set<OWLClassExpression> conjunctsInSuperClass = superClass.asConjunctSet();

        OWLDataFactory df = OWLManager.createOWLOntologyManager().getOWLDataFactory();
        OWLObjectProperty roleGroup = df.getOWLObjectProperty(IRI.create("http://snomed.info/id/609096000")); //TODO: store SNOMED IRI as variable somewhere higher
        for(OWLClassExpression conj:conjunctsInSuperClass) {
            System.out.println("Checking conjunct: " + conj);
            if(conj instanceof OWLObjectSomeValuesFrom) {
                OWLObjectPropertyExpression prop = ((OWLObjectSomeValuesFrom)conj).getProperty();
                OWLClassExpression filler = ((OWLObjectSomeValuesFrom)conj).getFiller();

                //Two options:
                // (a) If the statement has the form RG some (C1 and ... and Cn) then must check each Ci for complex nesting,
                //     since RG some (R some C) is permitted.
                // (b) Otherwise, if statement has form R some C, just check the statement itself for complex nesting.
                if(prop.equals(roleGroup)) {
                    for(OWLClassExpression subExp:filler.asConjunctSet()) {
                        if(expressionContainsComplexNesting(subExp)) {
                            return true;
                        }
                    }
                }
                else {
                    System.out.println("Performing non role group check.");
                    if(expressionContainsComplexNesting(conj)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    //Checks an OWLClassExpression for complex nesting of the form R some (S some C)
    private static boolean expressionContainsComplexNesting(OWLClassExpression exp) {
        if(exp instanceof OWLObjectSomeValuesFrom) {
            OWLClassExpression filler = ((OWLObjectSomeValuesFrom)exp).getFiller();

            if(!(filler instanceof OWLClass)) {
                return true;
            }
        }
        return false;
    }

}
