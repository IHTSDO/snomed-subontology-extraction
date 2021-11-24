package org.snomed.ontology.extraction.naming;

import org.snomed.ontology.extraction.writers.MapPrinter;
import org.semanticweb.owlapi.model.*;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

//TODO: defunct with introduction of GCI renamings -- see IntroducedNameHandler.
public class PropertyValueNamer {

    public Map<OWLObjectSomeValuesFrom, OWLClass> pvNamingMap;
    public Map<OWLClass, OWLObjectSomeValuesFrom> namingPvMap;
    //private OWLOntologyManager man;
    //private OWLDataFactory df;

    public PropertyValueNamer() {
        pvNamingMap = new HashMap<OWLObjectSomeValuesFrom, OWLClass>();
        namingPvMap = new HashMap<OWLClass, OWLObjectSomeValuesFrom>();
    }

    public OWLOntology returnOntologyWithNamedPropertyValues(OWLOntology inputOntology) throws OWLOntologyCreationException {
        OWLOntologyManager man = inputOntology.getOWLOntologyManager();
        OWLDataFactory df = man.getOWLDataFactory();

        OWLOntology inputOntologyWithRenamings = man.createOntology(inputOntology.getAxioms());
        //TODO: separate into GCI and non-GCI object properties? Do we need this?
        //Map<OWLObjectSomeValuesFrom, String> propertyValueMapNonGCIs = new HashMap<OWLObjectSomeValuesFrom, String>();

        //String IRIName = OntologyStringExtractor.extractIRI(inputOntology);
        String IRIName = "http://snomed.info/id/";
        //note: this does *not* separate GCIs from non-GCI PVs. This might require scanning axioms instead.
        Set<OWLObjectSomeValuesFrom> pvsInInput = new HashSet<OWLObjectSomeValuesFrom>();
        for (OWLClassExpression exp : inputOntology.getNestedClassExpressions()) {
            if (exp instanceof OWLObjectSomeValuesFrom) {
                OWLObjectSomeValuesFrom pv = (OWLObjectSomeValuesFrom) exp;
                pvsInInput.add(pv);
                String pvName = produceName(pv);
                //System.out.println("pvName: " + pvName);

                pvNamingMap.putIfAbsent(pv, df.getOWLClass(IRI.create(IRIName, pvName)));
                namingPvMap.putIfAbsent(df.getOWLClass(IRI.create(IRIName, pvName)), pv);
            }
        }

        //in case multiple ontologies have been named, only use PVs relevant to given input ontology
        for(OWLObjectSomeValuesFrom pv:pvsInInput) {
            man.addAxiom(inputOntologyWithRenamings, df.getOWLEquivalentClassesAxiom(pv, pvNamingMap.get(pv)));
        }
        /*
        for(Map.Entry<OWLObjectSomeValuesFrom, OWLClass> entry : pvNamingMap.entrySet()) {
            man.addAxiom(inputOntologyWithRenamings, df.getOWLEquivalentClassesAxiom(entry.getKey(), entry.getValue()));
        }
         */
        return inputOntologyWithRenamings;

    }
   // private void visitPropertyValues() {
   //     OWLObjectVisitor v = new OWLObjectVisitorAdapter() {
   //         public void visit(OWLObjectSomeValuesFrom pv) {
    //
     //       }
    //    };
   // }

    public boolean isNamedPV(OWLClass cls) {
        return namingPvMap.containsKey(cls);
    }

    public Set<OWLObjectSomeValuesFrom> retrievePVsFromNames(Set<OWLClass> renamedPVs) {
        Set<OWLObjectSomeValuesFrom> pvs = new HashSet<OWLObjectSomeValuesFrom>();
        for(OWLClass cls : renamedPVs) {
            pvs.add(namingPvMap.get(cls));
        }
        return pvs;
    }

    public Set<OWLClass> retrieveNamesForPVs(Set<OWLObjectSomeValuesFrom> pvs) {
        Set<OWLClass> names = new HashSet<OWLClass>();
        for(OWLObjectSomeValuesFrom pv : pvs) {
            names.add(pvNamingMap.get(pv));
        }
        return names;
    }

    private String produceName(OWLObjectSomeValuesFrom subclass) {
        return "PV_" + pvNamingMap.size();
    }

    public void resetNames() {
        pvNamingMap.clear();
    }

    public Map<OWLClass, OWLObjectSomeValuesFrom> getNamingPvMap() {
        return namingPvMap;
    }
    public Map<OWLObjectSomeValuesFrom, OWLClass> getPvNamingMap() {
        return pvNamingMap;
    }
    public void printNameAndPvPairs(String outputPath) throws IOException {
        MapPrinter printer = new MapPrinter(new File(outputPath));
        System.out.println("Printing naming map.");
        printer.printNamingsForPVs(pvNamingMap);
    }
}
