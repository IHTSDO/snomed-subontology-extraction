package org.snomed.ontology.extraction.naming;

import org.snomed.ontology.extraction.writers.MapPrinter;
import org.semanticweb.owlapi.model.*;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/*
Data structures: (to be updated / improved)
-pvNamingMap: key is each (unique) OWLClassExpression corresponding to R some C, value is associated name
-namingPvMap: swap above
----
-namedGCIMap: key is a name in the form of OWLClass, value is the original anonymous expression on left-hand side (LHS) of GCI that is associated with the name
-nameAndSuperGCIConceptMap: given a name for an anonymous LHS GCI expression, get the atomic concept present on the RHS of the GCI
-superGCIConceptAndNameMap: given a concept on the RHS of a GCI, get all names for associated anonymous (LHS) GCI concepts
 */

public class IntroducedNameHandler {

	private final Map<OWLRestriction, OWLClass> pvNamingMap;
	private final Map<OWLClass, OWLRestriction> namingPvMap;
	private final Map<OWLClass, OWLClassExpression> gciNameAndExpressionMap;
	private final Map<OWLClass, OWLClass> nameAndSuperGCIConceptMap; //TODO: improve.
	private final Map<OWLClass, Set<OWLClass>> superGCIConceptAndNameMap;
	private final OWLOntology originalOntology;
	private final OWLDataFactory df;
	private final OWLOntologyManager man;
	private final String IRIName = "http://snomed.info/id/";

	public IntroducedNameHandler(OWLOntology inputOntology) {
		pvNamingMap = new HashMap<>();
		namingPvMap = new HashMap<>();
		gciNameAndExpressionMap = new HashMap<>();
		nameAndSuperGCIConceptMap = new HashMap<>();
		superGCIConceptAndNameMap = new HashMap<>();
		originalOntology = inputOntology;
		man = originalOntology.getOWLOntologyManager();
		df = man.getOWLDataFactory();
	}

	public OWLOntology returnOntologyWithNamings() throws OWLOntologyCreationException {
		OWLOntology inputOntologyWithNamings = man.createOntology();

		//name property values, add equiv axioms for names
		namePropertyValuesInOntology();

		Set<OWLAxiom> pvNamingAxioms = new HashSet<>();
		for (Map.Entry<OWLRestriction, OWLClass> entry : pvNamingMap.entrySet()) {
			OWLRestriction pv = entry.getKey();
			OWLClass pvName = entry.getValue();
			pvNamingAxioms.add(df.getOWLEquivalentClassesAxiom(pv, pvName));
		}

		//name LHS of GCI axioms, add equiv axioms for names
		nameGCIs();

		Set<OWLAxiom> gciNamingAxioms = new HashSet<>();
		for (Map.Entry<OWLClass, OWLClassExpression> entry : gciNameAndExpressionMap.entrySet()) {
			gciNamingAxioms.add(df.getOWLEquivalentClassesAxiom(entry.getValue(), entry.getKey()));
		}

		man.addAxioms(inputOntologyWithNamings, originalOntology.getAxioms());
		man.addAxioms(inputOntologyWithNamings, pvNamingAxioms);
		man.addAxioms(inputOntologyWithNamings, gciNamingAxioms);

		return inputOntologyWithNamings;
	}

	private void namePropertyValuesInOntology() {
		//note: this does *not* separate GCIs from non-GCI PVs.
		for (OWLClassExpression exp : originalOntology.getNestedClassExpressions()) {
			if (exp instanceof OWLObjectSomeValuesFrom || exp instanceof OWLDataHasValue) {
				OWLRestriction pv = (OWLRestriction) exp;
				String pvName = producePVName();

				pvNamingMap.putIfAbsent(pv, df.getOWLClass(IRI.create(IRIName, pvName)));
				namingPvMap.putIfAbsent(df.getOWLClass(IRI.create(IRIName, pvName)), pv);
			}
		}
	}

	private void nameGCIs() {
		// Set<OWLSubClassOfAxiom> gciAxioms = new HashSet<OWLSubClassOfAxiom>();
		Set<OWLClass> gciSuperClasses = new HashSet<>();

		for (OWLClass cls : originalOntology.getClassesInSignature()) {
			//in SCT, GCI axioms are of form B and R some C <= A, i.e., no GCIs with anonymous superclass.
			//assumes equivalence axioms are not flattened.
			for (OWLSubClassOfAxiom ax : originalOntology.getSubClassAxiomsForSuperClass(cls)) {
				if (ax.isGCI()) {
					gciSuperClasses.add(cls);
					break;
				}
			}
		}

		for (OWLClass gciSuperCls : gciSuperClasses) {
			Set<OWLClass> gciNames = new HashSet<>();
			for (OWLSubClassOfAxiom ax : originalOntology.getSubClassAxiomsForSuperClass(gciSuperCls)) {
				if (ax.isGCI()) {
					OWLClassExpression gciExpression = ax.getSubClass();
					String gciNameString = produceGCIName();
					OWLClass gciClass = (OWLClass) ax.getSuperClass();
					OWLClass gciNameClass = df.getOWLClass(IRI.create(IRIName, gciNameString));

					gciNameAndExpressionMap.putIfAbsent(gciNameClass, gciExpression);
					nameAndSuperGCIConceptMap.putIfAbsent(gciNameClass, gciClass);
					gciNames.add(gciNameClass);
				}
			}
			superGCIConceptAndNameMap.putIfAbsent(gciSuperCls, gciNames);
		}
	}

	//PV naming methods
	private String producePVName() {
		return "PV_" + pvNamingMap.size();
	}

	public boolean isNamedPV(OWLClass cls) {
		return namingPvMap.containsKey(cls);
	}

	public OWLClass retrieveNameForPV(OWLRestriction pv) {
		return pvNamingMap.get(pv);
	}

	public OWLRestriction retrievePVForName(OWLClass cls) {
		return namingPvMap.get(cls);
	}

	public Set<OWLRestriction> retrievePVsFromNames(Set<OWLClass> renamedPVs) {
		Set<OWLRestriction> pvs = new HashSet<>();
		for (OWLClass cls : renamedPVs) {
			pvs.add(namingPvMap.get(cls));
		}
		return pvs;
	}

	public Set<OWLClass> retrieveNamesFromPVs(Set<OWLRestriction> pvs) {
		Set<OWLClass> names = new HashSet<>();
		for (OWLRestriction pv : pvs) {
			names.add(pvNamingMap.get(pv));
		}
		return names;
	}

	public void printNameAndPvPairs(String outputPath) throws IOException {
		MapPrinter printer = new MapPrinter(new File(outputPath));
		System.out.println("Printing naming map.");
		printer.printNamingsForPVs(pvNamingMap);
	}

	//GCI naming methods
	private String produceGCIName() {
		return "GCI_" + gciNameAndExpressionMap.size();
	}

	public boolean isNamedGCI(OWLClass cls) {
		return gciNameAndExpressionMap.containsKey(cls);
	}

	public boolean hasAssociatedGCIs(OWLClass cls) {
		return superGCIConceptAndNameMap.containsKey(cls);
	}

	public Set<OWLClass> returnNamesOfGCIsForSuperConcept(OWLClass cls) {
		if (!superGCIConceptAndNameMap.containsKey(cls)) {
			return new HashSet<>();
		}
		return superGCIConceptAndNameMap.get(cls);
	}

	public Set<OWLClass> retrieveAllNamesForGCIs() {
		return gciNameAndExpressionMap.keySet();
	}

	public OWLClassExpression retrieveExpressionFromGCIName(OWLClass gciName) {
		return gciNameAndExpressionMap.get(gciName);
	}

	public OWLClass retrieveSuperClassFromNamedGCI(OWLClass gciName) {
		return nameAndSuperGCIConceptMap.get(gciName);
	}

}
