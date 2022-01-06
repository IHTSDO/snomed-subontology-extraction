package org.snomed.ontology.extraction.definitiongeneration;

import org.snomed.ontology.extraction.classification.OntologyReasoningService;
import org.snomed.ontology.extraction.naming.IntroducedNameHandler;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.search.EntitySearcher;

import java.util.*;

public abstract class DefinitionGenerator {

	protected OWLOntology sourceOntology;
	protected OntologyReasoningService reasonerService;
	protected IntroducedNameHandler namer;
	protected OWLDataFactory df;
	protected List<Set<OWLAxiom>> generatedDefinitions;
	protected List<OWLAxiom> gciDefinitions;
	protected Set<OWLClassExpression> latestNecessaryConditions;
	protected Set<OWLAxiom> undefinedClasses;

	public DefinitionGenerator(OWLOntology inputOntology, OntologyReasoningService reasonerService, IntroducedNameHandler namer) {
		sourceOntology = inputOntology;
		this.reasonerService = reasonerService;
		this.namer = namer;
		OWLOntologyManager man = OWLManager.createOWLOntologyManager();
		df = man.getOWLDataFactory();
		generatedDefinitions = new ArrayList<>();
		gciDefinitions = new ArrayList<>();
		undefinedClasses = new HashSet<>();
	}

	public abstract void generateDefinition(OWLClass cls, Set<RedundancyOptions> redundancyOptions);

	public Set<OWLClass> reduceClassSet(Set<OWLClass> inputClassSet) {
		inputClassSet = reasonerService.eliminateWeakerClasses(inputClassSet);
		return (inputClassSet);
	}

	//Eliminates redundant PVs nested under role groups. Assumes max nesting is RG(R some C ...).
	public Set<OWLRestriction> eliminateRoleGroupRedundancies(Set<OWLRestriction> inputPVs) {
		Set<OWLRestriction> reducedInputPVs = new HashSet<>();

		for (OWLRestriction pv : inputPVs) {
			if (pv instanceof OWLObjectSomeValuesFrom) {
				OWLObjectSomeValuesFrom objectSomeValuesFrom = (OWLObjectSomeValuesFrom) pv;
				if (objectSomeValuesFrom.getFiller() instanceof OWLObjectIntersectionOf) {
					Set<OWLRestriction> pvFillers = new HashSet<>();
					for (OWLClassExpression filler : objectSomeValuesFrom.getFiller().asConjunctSet()) {
						if (filler instanceof OWLObjectSomeValuesFrom) {
							pvFillers.add((OWLObjectSomeValuesFrom) filler);
						} else if (filler instanceof OWLDataHasValue) {
							pvFillers.add((OWLDataHasValue) filler);
						} else {
							System.out.println("NAMED CLASS FOUND IN ROLE GROUP!");
						}
					}

					Set<OWLRestriction> reducedFillers = replaceNamesWithPVs(reduceClassSet(replacePVsWithNames(pvFillers)));

					reducedInputPVs.add(df.getOWLObjectSomeValuesFrom(objectSomeValuesFrom.getProperty(), df.getOWLObjectIntersectionOf(reducedFillers)));
					continue;
				}
			}
			//if not a role group
			reducedInputPVs.add(pv);
		}
		return reducedInputPVs;
	}

	//not currently needed
	/*
	public void computeSubPropertiesOfReflexiveProperties() {
		System.out.println("Computing reflexive properties.");
		EntitySearcher searcher = new EntitySearcher();

		//get declared reflexive properties
		for(OWLObjectProperty prop:backgroundOntology.getObjectPropertiesInSignature()) {
			if(searcher.isReflexive(prop, backgroundOntology)) {
				subPropertiesOfreflexiveProperties.add(prop);
			}
		}
		ListIterator<OWLObjectPropertyExpression> reflexiveIterator = subPropertiesOfreflexiveProperties.listIterator();
		List<OWLObjectPropertyExpression> additionalReflexiveProperties = new ArrayList<OWLObjectPropertyExpression>();

		//recursively add all subproperties of reflexive properties as reflexive (ELK does not support finding superproperties)
		while(reflexiveIterator.hasNext()) {
			OWLObjectPropertyExpression prop = reflexiveIterator.next();
			System.out.println("Current prop: " + prop.toString());
			for(OWLSubObjectPropertyOfAxiom propAx:backgroundOntology.getObjectSubPropertyAxiomsForSuperProperty(prop)) {
				System.out.println("Propax: " + propAx.toString());
				if(!additionalReflexiveProperties.contains(propAx.getSubProperty())) {
					reflexiveIterator.add(propAx.getSubProperty());
					reflexiveIterator.previous();
					additionalReflexiveProperties.add(propAx.getSubProperty());
					System.out.println("Reflexive property added: " + propAx.getSubProperty().toString());
				}
			}
		}
		subPropertiesOfreflexiveProperties.addAll(additionalReflexiveProperties);
	}
	 */

	public Set<OWLRestriction> eliminateReflexivePVRedundancies(OWLClass inputClass, Set<OWLRestriction> inputPVs) {
		Set<OWLRestriction> reducedInputPVs = new HashSet<>();

		//retrieve reflexive PVs in definition
		for (OWLRestriction pv : inputPVs) {
			if (pv instanceof OWLObjectSomeValuesFrom) {
				OWLObjectSomeValuesFrom objectSomeValuesFrom = (OWLObjectSomeValuesFrom) pv;
				boolean isReflexiveProperty = checkIfReflexiveProperty(objectSomeValuesFrom.getProperty().asOWLObjectProperty());
				if (isReflexiveProperty) {
					OWLClassExpression filler = objectSomeValuesFrom.getFiller();
					if (reasonerService.getAncestors(inputClass).contains(filler)// FIXME: This will always be false because object types are different!
							|| filler.equals(inputClass)) {
						continue;
					}
				}
			}
			reducedInputPVs.add(pv);
		}
		return reducedInputPVs;
	}

	public boolean checkIfReflexiveProperty(OWLObjectPropertyExpression r) {
		return EntitySearcher.isReflexive(r, sourceOntology);
	}

	protected Set<OWLClass> extractNamedPVs(Set<OWLClass> classes) {
		Set<OWLClass> renamedPVs = new HashSet<>();

		for (OWLClass cls : classes) {
			if (namer.isNamedPV(cls)) {
				renamedPVs.add(cls);
			}
		}
		return renamedPVs;
	}

	protected Set<OWLClass> extractNamedGCIs(Set<OWLClass> classes) {
		Set<OWLClass> namedGCIs = new HashSet<>();

		for (OWLClass cls : classes) {
			if (namer.isNamedGCI(cls)) {
				namedGCIs.add(cls);
			}
		}
		return namedGCIs;
	}

	public Set<OWLRestriction> replaceNamesWithPVs(Set<OWLClass> classes) {
		return namer.retrievePVsFromNames(classes);
	}

	protected Set<OWLClass> replacePVsWithNames(Set<OWLRestriction> pvs) {
		return namer.retrieveNamesFromPVs(pvs);
	}

	//protected void constructDefinition(OWLClass definedClass, Set<Set<OWLClassExpression>> definingConditionsByAxiom) {
	protected void constructDefinition(OWLClass definedClass, Map<Set<OWLClassExpression>, Boolean> definingConditionsByAxiom) {
		Set<OWLAxiom> definitionAxioms = new HashSet<>();

		//for(Set<OWLClassExpression> definingConditions:definingConditionsByAxiom) {
		for (Map.Entry<Set<OWLClassExpression>, Boolean> entry : definingConditionsByAxiom.entrySet()) {
			Set<OWLClassExpression> definingConditions = entry.getKey();
			Boolean isEquivalence = entry.getValue();
			definingConditions.remove(df.getOWLThing());
			definingConditions.remove(df.getOWLNothing());
			if (definingConditions.size() == 0) {
				continue;
			}
			OWLAxiom definingAxiom;
			OWLClassExpression definitionExpression = definingConditions.size() == 1 ? definingConditions.iterator().next() : df.getOWLObjectIntersectionOf(definingConditions);
			if (isEquivalence) {
				definingAxiom = df.getOWLEquivalentClassesAxiom(definedClass, definitionExpression);
			} else {
				definingAxiom = df.getOWLSubClassOfAxiom(definedClass, definitionExpression);
			}

			latestNecessaryConditions = definingConditions;
			//store gci definitions separately. //TODO: improve in line with multi-axiom handling
			if (namer.isNamedGCI(definedClass)) {
				System.out.println("GCI DEFINITION ADDED: " + definingAxiom);
				gciDefinitions.add(definingAxiom);
				continue;
			}
			//generatedDefinitions.add(definingAxiom);
			definitionAxioms.add(definingAxiom);
			//return;
		}

		//make generatedDefinitions a set of sets, go from there? //check "get latest necessary conditions"
		if (definitionAxioms.size() == 0 && !namer.isNamedGCI(definedClass)) {
			System.out.println("Undefined class: " + definedClass);
			undefinedClasses.add(df.getOWLSubClassOfAxiom(df.getOWLThing(), definedClass));
			return;
		}
		generatedDefinitions.add(definitionAxioms);
	}

	public Set<OWLAxiom> getAllGeneratedDefinitions() {
		Set<OWLAxiom> allDefinitions = new HashSet<>();
		for (Set<OWLAxiom> definitionsForClass : generatedDefinitions) {
			allDefinitions.addAll(definitionsForClass);
		}
		return allDefinitions;
	}

	public Set<OWLAxiom> getLastDefinitionGenerated() {
		Set<OWLAxiom> latestDefinition = null;
		if (generatedDefinitions != null && !generatedDefinitions.isEmpty()) {
			latestDefinition = generatedDefinitions.get(generatedDefinitions.size() - 1);
		}
		return latestDefinition;
	}

	public void removeLastDefinition() {
		generatedDefinitions.remove(generatedDefinitions.get(generatedDefinitions.size() - 1));
	}

	public Set<OWLClassExpression> getLatestNecessaryConditions() {
		return this.latestNecessaryConditions;
	}

	public Set<OWLAxiom> getUndefinedClassAxioms() {
		return this.undefinedClasses;
	}

	protected OWLOntology getSourceOntology() {
		return this.sourceOntology;
	}

	//currently not storing property definitions, may make sense to combine these two generateDefinitions to operate simply over entities and decide based on type
	public Set<OWLAxiom> generatePropertyDefinition(OWLProperty propToDefine, AxiomType<?> axiomType) {
		@SuppressWarnings("unchecked")
		Set<? extends OWLSubPropertyAxiom<?>> propAxioms = (Set<? extends OWLSubPropertyAxiom<?>>) sourceOntology.getAxioms(axiomType);
		//collect stated super properties of property to define
		Set<OWLPropertyExpression> statedSuperProps = new HashSet<>();
		for (OWLSubPropertyAxiom<?> ax : propAxioms) {
			if (ax.getSubProperty().equals(propToDefine)) {
				statedSuperProps.add(ax.getSuperProperty());
			}
		}

		//eliminate non-direct super properties
		Set<OWLPropertyExpression> otherSuperProps = new HashSet<>();
		Set<OWLPropertyExpression> directSuperProps = new HashSet<>();

		for (OWLPropertyExpression superProp : statedSuperProps) {
			otherSuperProps.remove(superProp);
			if (isDirectSuperProperty(superProp, otherSuperProps, axiomType)) {
				directSuperProps.add(superProp);
			}
			otherSuperProps.add(superProp);
		}

		//return set of inclusion axioms of form r <= s for each direct super property s
		Set<OWLAxiom> definitionalAxioms = new HashSet<>();
		for (OWLPropertyExpression directSuperProp : directSuperProps) {
			if (axiomType == AxiomType.SUB_OBJECT_PROPERTY) {
				definitionalAxioms.add(df.getOWLSubObjectPropertyOfAxiom((OWLObjectPropertyExpression) propToDefine, (OWLObjectPropertyExpression) directSuperProp));
			} else if (axiomType == AxiomType.SUB_DATA_PROPERTY) {
				definitionalAxioms.add(df.getOWLSubDataPropertyOfAxiom((OWLDataPropertyExpression) propToDefine, (OWLDataPropertyExpression) directSuperProp));
			}
		}
		return definitionalAxioms;
	}

	private boolean isDirectSuperProperty(OWLPropertyExpression superProperty, Set<OWLPropertyExpression> otherSuperProperties, AxiomType<?> subObjectProperty) {
		@SuppressWarnings("unchecked")
		Set<? extends OWLSubPropertyAxiom<?>> propAxioms = (Set<? extends OWLSubPropertyAxiom<?>>) sourceOntology.getAxioms(subObjectProperty);
		for (OWLSubPropertyAxiom<?> propAx : propAxioms) {
			if (propAx.getSubProperty().equals(superProperty) && otherSuperProperties.contains(propAx.getSuperProperty())) {
				return false;
			}
		}
		return true;
	}
}
