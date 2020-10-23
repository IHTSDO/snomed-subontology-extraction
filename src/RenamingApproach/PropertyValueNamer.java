package RenamingApproach;

import org.semanticweb.owlapi.model.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class PropertyValueNamer {

    public Map<OWLObjectSomeValuesFrom, OWLClass> pvNamingMap;
    public Map<OWLClass, OWLObjectSomeValuesFrom> namingPvMap;
    //private OWLOntologyManager man;
    //private OWLDataFactory df;

    public PropertyValueNamer() {
        pvNamingMap = new HashMap<OWLObjectSomeValuesFrom, OWLClass>();
        namingPvMap = new HashMap<OWLClass, OWLObjectSomeValuesFrom>();
    }

    public OWLOntology namePropertyValues(OWLOntology inputOntology) throws OWLOntologyCreationException {
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

                pvNamingMap.putIfAbsent(expExis, df.getOWLClass(IRI.create(IRIName, pvName)));
                namingPvMap.putIfAbsent(df.getOWLClass(IRI.create(IRIName, pvName)), expExis);
            }
        }

        //TODO: add equivalent classes axioms to ontology.
        for(Map.Entry<OWLObjectSomeValuesFrom, OWLClass> entry : pvNamingMap.entrySet()) {
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

    public boolean isNamedPV(OWLClass cls) {
        if(namingPvMap.containsKey(cls)) {
            return true;
        }
        return false;
    }

    public Set<OWLObjectSomeValuesFrom> retrievePVsFromNames(Set<OWLClass> renamedPVs) {
        Set<OWLObjectSomeValuesFrom> pvs = new HashSet<OWLObjectSomeValuesFrom>();
        for(OWLClass cls : renamedPVs) {
            pvs.add(namingPvMap.get(cls));
            //System.out.println("RENAMING PV: " + renamingPvMap.get(cls).toString());
        }
        return pvs;
    }

    public Set<OWLClass> retrieveNamesForPVs(Set<OWLObjectSomeValuesFrom> pvs) {
        Set<OWLClass> names = new HashSet<OWLClass>();
        for(OWLObjectSomeValuesFrom pv : pvs) {
            names.add(pvNamingMap.get(pv));
            //System.out.println("RENAMING PV: " + renamingPvMap.get(cls).toString());
        }
        return names;
    }

    private String produceName(OWLObjectSomeValuesFrom subclass) {
        String pvName = "PV_" + pvNamingMap.size();
        return pvName;
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
}
