package org.deliverable2;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CSVPrinter
{
	private static final Logger logger =  Logger.getLogger(CSVPrinter.class.getName());
	
	private CSVPrinter() 
	{
		   throw new IllegalStateException("Utility class.");
	}
	
	public static void printMeasures(String projName, List<Measure> measures)
	{
		String outName = projName + "Measures.csv";
		
		try(FileWriter fileWriter = new FileWriter(outName))
		{
			fileWriter.append("Dataset,#TrainingRelease,%Training,%Defective in training,%Defective in testing,Classifier,Balancing,Feature Selection,Sensitivity,TP,FP,TN,FN,Precision,Recall,AUC,Kappa");
			fileWriter.append("\n");
			for (Measure measure : measures) {
				fileWriter.append(measure.getDataset());
				fileWriter.append(",");
				fileWriter.append(measure.getRelease().toString());
				fileWriter.append(",");
				fileWriter.append(measure.getTrainPercentage().toString());
				fileWriter.append(",");
				fileWriter.append(measure.getDefectInTrainPercentage().toString());
				fileWriter.append(",");
				fileWriter.append(measure.getDefectInTestPercentage().toString());
				fileWriter.append(",");
				fileWriter.append(measure.getClassifier());
				fileWriter.append(",");
				fileWriter.append(measure.getBalancing());
				fileWriter.append(",");
				fileWriter.append(measure.getFeatureSelection());
				fileWriter.append(",");
				fileWriter.append(measure.getSensitivity());
				fileWriter.append(",");
				fileWriter.append(measure.getTp().toString());
				fileWriter.append(",");
				fileWriter.append(measure.getFp().toString());
				fileWriter.append(",");
				fileWriter.append(measure.getTn().toString());
				fileWriter.append(",");
				fileWriter.append(measure.getFn().toString());
				fileWriter.append(",");
				fileWriter.append(measure.getPrecision().toString());
				fileWriter.append(",");
				fileWriter.append(measure.getRecall().toString());
				fileWriter.append(",");
				fileWriter.append(measure.getAuc().toString());
				fileWriter.append(",");
				fileWriter.append(measure.getKappa().toString());
				fileWriter.append("\n");
			}
		} 
		catch (IOException e) 
		{
			logger.log(Level.INFO,"Error occurred writing the CSV.");
		}
	}
}