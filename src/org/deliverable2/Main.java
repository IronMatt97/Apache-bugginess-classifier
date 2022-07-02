package org.deliverable2;

public class Main 
{
	public static void main(String[] args)
	{
		String dataSetA = "/home/matteo/Documenti/Workspaces/ISW2-2122/AVRO_OutputMetrics.arff";
		String dataSetB = "/home/matteo/Documenti/Workspaces/ISW2-2122/BOOKKEEPER_OutputMetrics.arff";
		AccuracyController.computeAccuracy(dataSetA,"AVRO");
		AccuracyController.computeAccuracy(dataSetB,"BOOKKEEPER");
	}
}
