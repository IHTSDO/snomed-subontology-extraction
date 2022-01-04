package org.snomed.ontology.extraction.tools;

import org.snomed.ontology.extraction.exception.ReasonerException;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.ConsoleProgressMonitor;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.reasoner.SimpleConfiguration;

import java.util.Arrays;
import java.util.HashSet;

public class ConsistencyEntailmentChecker {

	public static void main(String[] args) throws OWLOntologyCreationException, ReasonerException {
		OWLOntologyManager man = OWLManager.createOWLOntologyManager();
		OWLDataFactory df = man.getOWLDataFactory();

		OWLOntology ont = man.createOntology();
		OWLClass A = df.getOWLClass(IRI.create("example.com/#" + "A"));
		OWLClass B = df.getOWLClass(IRI.create("example.com/#" + "B"));
		OWLClass C = df.getOWLClass(IRI.create("example.com/#" + "C"));
		OWLClass D = df.getOWLClass(IRI.create("example.com/#" + "D"));
		OWLClass E = df.getOWLClass(IRI.create("example.com/#" + "E"));
		OWLNamedIndividual a = df.getOWLNamedIndividual(IRI.create("example.com/#a"));

	   // OWLSubClassOfAxiom ax1 = df.getOWLSubClassOfAxiom(D, A);
	   // OWLSubClassOfAxiom ax2 = df.getOWLSubClassOfAxiom(D, B);
	   // OWLSubClassOfAxiom ax3 = df.getOWLSubClassOfAxiom(D, E);
	  //  OWLClassAssertionAxiom as1 = df.getOWLClassAssertionAxiom(E, a);
	  //  OWLClassAssertionAxiom as2 = df.getOWLClassAssertionAxiom(df.getOWLObjectComplementOf(C), a);

	 //   OWLSubClassOfAxiom ax4 = df.getOWLSubClassOfAxiom(E, C);

		OWLSubClassOfAxiom ax1 = df.getOWLSubClassOfAxiom(A, B);
		OWLClassAssertionAxiom as1 = df.getOWLClassAssertionAxiom(A, a);
		OWLClassAssertionAxiom as2 = df.getOWLClassAssertionAxiom(df.getOWLObjectComplementOf(B), a);

		man.addAxioms(ont, new HashSet<>(Arrays.asList(ax1, as1, as2)));

		OWLReasoner res1 = getReasonerFactory("org.semanticweb.elk.owlapi.ElkReasonerFactory").createReasoner(ont, new SimpleConfiguration(new ConsoleProgressMonitor()));

		System.out.println("CONSISTENT?" + res1.isConsistent());
	}

	private static OWLReasonerFactory getReasonerFactory(String reasonerFactoryName) throws ReasonerException {
		Class<?> reasonerFactoryClass;
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
}
