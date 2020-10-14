package RenamingApproach;

import org.semanticweb.owlapi.model.*;
import tools.OntologyStringExtractor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class PropertyValueRenamer {

    public Map<OWLObjectSomeValuesFrom, OWLClass> pvRenamingMap;
    public Map<OWLClass, OWLObjectSomeValuesFrom> renamingPvMap;
    //private OWLOntologyManager man;
    //private OWLDataFactory df;

    public PropertyValueRenamer() {
        pvRenamingMap = new HashMap<OWLObjectSomeValuesFrom, OWLClass>();
        renamingPvMap = new HashMap<OWLClass, OWLObjectSomeValuesFrom>();
    }

    public OWLOntology renamePropertyValues(OWLOntology inputOntology) throws OWLOntologyCreationException {
        OWLOntologyManager man = inputOntology.getOWLOntologyManager();
        OWLDataFactory df = man.getOWLDataFactory();

        OWLOntology inputOntologyWithRenamings = man.createOntology(inputOntology.getAxioms());
        //TODO: separate into GCI and non-GCI object properties? Do we need this?
        //Map<OWLObjectSomeValuesFrom, String> propertyValueMapNonGCIs = new HashMap<OWLObjectSomeValuesFrom, String>();

        //String IRIName = OntologyStringExtractor.extractIRI(inputOntology);
        String IRIName = "http://snomed.info/id/";
        //TODO: is this best way? Seems fine for quantified role + fillers. Though note: this does *not* separate GCIs from non-GCI PVs. This might require scanning axioms instead.
        for(OWLClassExpression exp:inputOntologyWithRenamings.getNestedClassExpressions()) {
            if(exp instanceof OWLObjectSomeValuesFrom) {
                OWLObjectSomeValuesFrom expExis = (OWLObjectSomeValuesFrom) exp;
                String pvName = produceName(expExis);
                System.out.println("pvName: " + pvName);

                pvRenamingMap.putIfAbsent(expExis, df.getOWLClass(IRI.create(IRIName, pvName)));
                renamingPvMap.putIfAbsent(df.getOWLClass(IRI.create(IRIName, pvName)), expExis);
            }
        }

        //TODO: add equivalent classes axioms to ontology.
        for(Map.Entry<OWLObjectSomeValuesFrom, OWLClass> entry : pvRenamingMap.entrySet()) {
            man.addAxiom(inputOntologyWithRenamings, df.getOWLEquivalentClassesAxiom(entry.getKey(), entry.getValue()));
        }

        return inputOntologyWithRenamings;
    }



   // private void visitPropertyValues() {
   //     OWLObjectVisitor v = new OWLObjectVisitorAdapter() {
   //         public void visit(OWLObjectSomeValuesFrom pv) {
    //
     //       }
    //    };
   // }

    public boolean isRenamedPV(OWLClass cls) {
        if(renamingPvMap.containsKey(cls)) {
            return true;
        }
        return false;
    }

    //public OWLObjectSomeValuesFrom retrievePVFromNames(OWLClass renamedPV) {
    //
    //}

    public Set<OWLObjectSomeValuesFrom> retrievePVsFromNames(Set<OWLClass> renamedPVs) {
        Set<OWLObjectSomeValuesFrom> pvs = new HashSet<OWLObjectSomeValuesFrom>();
        for(OWLClass cls : renamedPVs) {
            pvs.add(renamingPvMap.get(cls));
            //System.out.println("RENAMING PV: " + renamingPvMap.get(cls).toString());
        }
        return pvs;
    }

    public Set<OWLClass> retrieveNamesForPVs(Set<OWLObjectSomeValuesFrom> pvs) {
        Set<OWLClass> names = new HashSet<OWLClass>();
        for(OWLObjectSomeValuesFrom pv : pvs) {
            names.add(pvRenamingMap.get(pv));
            //System.out.println("RENAMING PV: " + renamingPvMap.get(cls).toString());
        }
        return names;
    }

    private String produceName(OWLObjectSomeValuesFrom subclass) {
        String pvName = "PV_" + pvRenamingMap.size();
        return pvName;
    }


    //private Set<OWLEquivalentClassesAxiom> addRenamingEquivalentClassesAxiom() {
    //
    //}
    public void resetNames() {
        pvRenamingMap.clear();
    }

    public Map<OWLClass, OWLObjectSomeValuesFrom> getRenamingPvMap() {
        return renamingPvMap;
    }
    public Map<OWLObjectSomeValuesFrom, OWLClass> getPvRenamingMap() {
        return pvRenamingMap;
    }
}
