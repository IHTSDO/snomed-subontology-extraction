package org.snomed.ontology.extraction.classification;

import org.snomed.ontology.extraction.exception.ReasonerException;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.*;
import org.semanticweb.owlapi.util.InferredEquivalentClassAxiomGenerator;
import org.semanticweb.owlapi.util.InferredOntologyGenerator;
import org.semanticweb.owlapi.util.InferredSubClassAxiomGenerator;

import java.util.*;

public class OntologyReasoningService {

    private OWLReasoner reasoner;
    private final OWLReasonerConfiguration configuration = new SimpleConfiguration(new ConsoleProgressMonitor());
    private String reasonerName;

    public OntologyReasoningService(OWLOntology inputOntology) throws ReasonerException {
        this(inputOntology, "org.semanticweb.elk.owlapi.ElkReasonerFactory");
    }
    public OntologyReasoningService(OWLOntology inputOntology, String reasonerFactoryName) throws ReasonerException {
        //this.inputOntology = inputOntology;
        reasoner = getReasonerFactory(reasonerFactoryName).createReasoner(inputOntology, configuration);
        reasonerName = reasonerFactoryName;
    }

    public void classifyOntology() {
        System.out.println("Classifying ontology (precomputing hierarchy).");
        reasoner.flush();
        reasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY);
    }

    public OWLOntology getClassifiedOntology() throws OWLOntologyCreationException {
        InferredOntologyGenerator classifiedOntologyGenerator = new InferredOntologyGenerator(reasoner, Arrays.asList(new InferredSubClassAxiomGenerator(),
                                                                                                                      new InferredEquivalentClassAxiomGenerator()));
        OWLOntologyManager man = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = man.getOWLDataFactory();

        OWLOntology classifiedOntology = man.createOntology();
        classifiedOntologyGenerator.fillOntology(df, classifiedOntology);

        return classifiedOntology;
    }

    private OWLReasonerFactory getReasonerFactory(String reasonerFactoryName) throws ReasonerException {
        Class<?> reasonerFactoryClass = null;
        try {
            reasonerFactoryClass = Class.forName(reasonerFactoryName);
            return (OWLReasonerFactory) reasonerFactoryClass.newInstance();
        } catch (ClassNotFoundException e) {
            throw new ReasonerException(String.format("Requested reasoner class '%s' not found.", reasonerFactoryName), e);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            throw new ReasonerException(String.format("Requested reasoner class '%s' not found.", reasonerFactoryName), e);
        } catch (InstantiationException e) {
            e.printStackTrace();
            throw new ReasonerException("Reasoner instantiation exception.", e);
        }
    }

    public Map<OWLClass, Set<OWLClass>> getEquivalentClassesMap() {
        OWLOntology rootOntology = reasoner.getRootOntology();
        System.out.println("Computing equivalent classes map for ontology: " + rootOntology.getOntologyID().toString());
        Map<OWLClass, Set<OWLClass>> equivalentClassesMap = new HashMap<>();
        for(OWLClass cls:rootOntology.getClassesInSignature()) {
            equivalentClassesMap.computeIfAbsent(cls, s -> this.getEquivalentClasses(s));
        }
        return equivalentClassesMap;
    }

    public Set<OWLClass> getEquivalentClasses(OWLClass cls) {
        Node<OWLClass> clsNode = reasoner.getEquivalentClasses(cls);
        return clsNode.getEntities();
    }

    public Set<OWLClass> getDirectAncestors(OWLClass cls) {
        return reasoner.getSuperClasses(cls, true).getFlattened();
    }

    /* //ELK does not support directly
    public Set<OWLObjectPropertyExpression> getDirectAncestors(OWLObjectPropertyExpression role) {
        return reasoner.getSuperObjectProperties(role, false).getFlattened();
    }
     */

    public Set<OWLClass> getAncestors(OWLClass cls) {
        return reasoner.getSuperClasses(cls, false).getFlattened();
    }

    /* //ELK does not support
    public Set<OWLObjectPropertyExpression> getAncestors(OWLObjectPropertyExpression role) {
        return reasoner.getSuperObjectProperties(role, false).getFlattened();
    }
     */

    public Set<OWLClass> getDirectDescendants(OWLClass cls) {
        return reasoner.getSubClasses(cls, true).getFlattened();
    }

    //ELK does not support
    /*
    public Set<OWLObjectPropertyExpression> getDirectDescendants(OWLObjectPropertyExpression role) {
        return reasoner.getSubObjectProperties(role, true).getFlattened();
    }
     */
    //start at s <= r, then move down through t <= s etc, to return t, s etc for role r
    public Set<OWLObjectPropertyExpression> getDescendantProperties(OWLObjectPropertyExpression role) {
        ListIterator<OWLObjectPropertyExpression> rolesIterator = new ArrayList<OWLObjectPropertyExpression>(Arrays.asList(role)).listIterator();
        Set<OWLObjectPropertyExpression> descendantProperties = new HashSet<OWLObjectPropertyExpression>();

        //recursively add all subproperties of reflexive properties as reflexive (ELK does not support finding superproperties)
        while(rolesIterator.hasNext()) {
            OWLObjectPropertyExpression prop = rolesIterator.next();
            System.out.println("Current prop: " + prop.toString());

            for(OWLSubObjectPropertyOfAxiom propAx:reasoner.getRootOntology().getObjectSubPropertyAxiomsForSuperProperty(prop)) {
                descendantProperties.add(propAx.getSubProperty());
                rolesIterator.add(propAx.getSubProperty());
                rolesIterator.previous();
            }
            /*
            for(OWLSubObjectPropertyOfAxiom propAx:backgroundOntology.getObjectSubPropertyAxiomsForSuperProperty(prop)) {
                System.out.println("Propax: " + propAx.toString());
                if(!additionalReflexiveProperties.contains(propAx.getSubProperty())) {
                    reflexiveIterator.add(propAx.getSubProperty());
                    reflexiveIterator.previous();
                    additionalReflexiveProperties.add(propAx.getSubProperty());
                    System.out.println("Reflexive property added: " + propAx.getSubProperty().toString());
                }
            }
             */
        }
        return descendantProperties;
    }


    public Set<OWLClass> getDescendants(OWLClass cls) {
        return reasoner.getSubClasses(cls, false).getFlattened();
    }

    //ELK does not support , have to work around (see below)
    /*
    public Set<OWLObjectPropertyExpression> getDescendants(OWLObjectPropertyExpression role) {
        return reasoner.getSubObjectProperties(role, false).getFlattened();
    }
     */

    /*
    public Set<OWLClass> getDescendants(OWLClass cls, boolean getSelf) {
        return reasoner.getSubClasses(cls, getSelf).getFlattened();
    }
     */

    public OWLClass getTopClass() {
        ArrayList<OWLEntity> topEntities = new ArrayList<OWLEntity>(reasoner.getTopClassNode().getEntities());

        OWLClass topClass = topEntities.get(0).asOWLClass();
        return topClass;
    }

    // Currently assumes no equivalent classes: would be problematic if we have equivalent named classes or PVs, since this will mean both are removed. //TODO: detect equivalent cases, remove only one.
    public Set<OWLClass> eliminateWeakerClasses(Set<OWLClass> inputClassSet) {
        Set<OWLClass> redundantClasses = new HashSet<OWLClass>();

        Set<OWLClass> otherClasses = new HashSet<OWLClass>(inputClassSet);
        for (OWLClass cls : inputClassSet) {
            otherClasses.remove(cls);
            if (weakerThanAtLeastOneOf(cls, otherClasses)) {
                redundantClasses.add(cls);
            }
            otherClasses.add(cls); //retain redundancies to check against (?)
        }

        inputClassSet.removeAll(redundantClasses);
        return (inputClassSet);
    }

    public Set<OWLClass> eliminateStrongerClasses(Set<OWLClass> inputClassSet) {
        Set<OWLClass> redundantClasses = new HashSet<OWLClass>();

        Set<OWLClass> otherClasses = new HashSet<OWLClass>(inputClassSet);
        for (OWLClass cls : inputClassSet) {
            otherClasses.remove(cls);
            if (strongerThanAtLeastOneOf(cls, otherClasses)) {
                redundantClasses.add(cls);
            }
            otherClasses.add(cls); //retain redundancies to check against (?)
        }

        inputClassSet.removeAll(redundantClasses);
        return (inputClassSet);
    }

    /* //ELK does not support
    public Set<OWLObjectPropertyExpression> eliminateWeakerRoles(Set<OWLObjectPropertyExpression> inputRoleSet) {
        Set<OWLObjectPropertyExpression> redundantRoles = new HashSet<>();

        Set<OWLObjectPropertyExpression> otherRoles = new HashSet<>(inputRoleSet);
        for (OWLObjectPropertyExpression role : inputRoleSet) {
            otherRoles.remove(role);
            if (weakerThanAtLeastOneOf(role, otherRoles)) {
                redundantRoles.add(role);
            }
            otherRoles.add(role); //retain redundancies to check against (?)

        }

        inputRoleSet.removeAll(redundantRoles);
        return (inputRoleSet);
    }
     */

    public boolean isPrimitive(OWLClass cls) {
        if(reasoner.getRootOntology().getEquivalentClassesAxioms(cls).isEmpty()) {
            return true;
        }
        return false;
    }

    public boolean isStrongerThan(OWLClass classBeingChecked, OWLClass classCheckedAgainst) {
        if(this.getAncestors(classBeingChecked).contains(classCheckedAgainst)) {
            return true;
        }
        return false;
    }

    public boolean weakerThanAtLeastOneOf(OWLClass classBeingChecked, Set<OWLClass> setCheckedAgainst) {
        for(OWLClass classCheckedAgainst:setCheckedAgainst) {
            if(this.getAncestors(classCheckedAgainst).contains(classBeingChecked)) {
                return true;
            }
        }
        return false;
    }

    /* //ELK does not support
    public boolean weakerThanAtLeastOneOf(OWLObjectPropertyExpression roleBeingChecked, Set<OWLObjectPropertyExpression> setCheckedAgainst) {
        for(OWLObjectPropertyExpression roleCheckedAgainst:setCheckedAgainst) {
            if(this.getAncestors(roleCheckedAgainst).contains(roleBeingChecked)) {
                return true;
            }
        }
        return false;
    }
     */

    public boolean strongerThanAtLeastOneOf(OWLClass classBeingChecked, Set<OWLClass> setCheckedAgainst) {
        for(OWLClass classCheckedAgainst:setCheckedAgainst) {
            if(this.getDescendants(classCheckedAgainst).contains(classBeingChecked)) {
                return true;
            }
        }
        return false;
    }

    public boolean isConsistent() {
        return reasoner.isConsistent();
    }

    public boolean entails(OWLAxiom ax) {
        return reasoner.isEntailed(ax);
    }

    public void setNewSourceOntologyAndClassify(OWLOntology newSourceOntology) throws ReasonerException {
        reasoner.dispose();
        reasoner = getReasonerFactory(reasonerName).createReasoner(newSourceOntology, configuration);
        this.classifyOntology();
    }

    public OWLReasoner getReasoner() {
        return reasoner;
    }
}
