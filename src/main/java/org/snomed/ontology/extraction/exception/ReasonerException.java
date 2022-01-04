package org.snomed.ontology.extraction.exception;

public class ReasonerException extends Exception {

	private static final long serialVersionUID = 1L;

	public ReasonerException(String message) {
		super(message);
	}

	public ReasonerException(String message, Throwable cause) {
		super(message, cause);
	}
}
