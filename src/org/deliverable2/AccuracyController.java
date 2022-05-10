package org.deliverable2;

import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;

public class AccuracyController 
{
	private static final Logger logger =  Logger.getLogger(AccuracyController.class.getName());
	private static final String[] featureSelection = {"NO","BEST FIRST"};
	private static final String[] balancing = {"NO","UNDERSAMPLING","OVERSAMPLING","SMOTE"};
	private static final String[] costEvaluation = {"NO","SENSITIVE THRESHOLD","SENSITIVE LEARNING"};
	private static final String[] classifier = {"RANDOM FOREST","NAIVE BAYES","IBK"};
	private AccuracyController() 
	{
		   throw new IllegalStateException("Application controller.");
	}
	
	//Metodo di controllo dell'applicativo
	public static void computeAccuracy(String datasetPath,String projName)
	{
		ArrayList<Instances> sets = getSets(datasetPath);
		ArrayList<Measure> measures = new ArrayList<>();
		ValidationHandler v = new ValidationHandler();
		int p = 1;
		
		for(String f : featureSelection)
		{
			for(String b : balancing)
			{
				for(String e : costEvaluation)
				{
					for (String c : classifier)
					{
						String progress = String.format("%n--------RUNNING MEASURE COMPUTATION:%nProject: %s%nFeature Selection: %s%nBalancing: %s%nSensitiveness: %s%nClassifier: %s%n%nProgress: %d out of %d%n",projName,f,b,e,c,p,(featureSelection.length*balancing.length*costEvaluation.length*classifier.length));
						logger.log(Level.INFO,progress);
						v.walkForward(f,b,e,c,sets,measures,projName);
						p++;
					}
				}
			}
		}
		CSVPrinter.printMeasures(projName,measures);
	}
	
	//Ottengo una lista dove ogni elemento è l'insieme delle istanze divise per versione
	private static ArrayList<Instances> getSets(String datasetPath)
	{
		ArrayList<Instances> sets = new ArrayList<>();
		try 
		{
			DataSource source = new DataSource(datasetPath);
			int versions = (int)source.getDataSet().get(source.getDataSet().size()-1).value(0);
		
			for(int v = 1 ; v <= versions ; v++)
			{
				Instances instance = source.getDataSet();
				for (int i = instance.numInstances() - 1; i >= 0 ; i--) 
				{
					Instance inst = instance.get(i);
					if (inst.value(0)!=v) 
						instance.delete(i);
				}
				//sets contiene tutte le instance divise per version index
				sets.add(instance);
			}
		} 
		catch (Exception e) 
		{
			logger.log(Level.INFO,"An error has occurred acquiring the dataset.");
		}
		return sets;
	}
}