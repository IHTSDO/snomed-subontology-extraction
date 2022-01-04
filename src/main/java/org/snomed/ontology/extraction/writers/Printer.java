package org.snomed.ontology.extraction.writers;

import java.io.BufferedWriter;
import java.io.IOException;

public abstract class Printer {

	//TODO: move all these to a printing package / class?
	public static void newline(BufferedWriter writer) throws IOException {
		writer.write("\r\n");// Add windows line ending before newline
		//writer.newLine();
	}

}
