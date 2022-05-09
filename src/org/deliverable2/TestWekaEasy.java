package org.deliverable2;

import weka.core.Instances;
import weka.classifiers.Evaluation;
import weka.classifiers.meta.FilteredClassifier;
import weka.classifiers.bayes.NaiveBayes;
import weka.filters.supervised.instance.Resample;
import weka.core.converters.ConverterUtils.DataSource;
import weka.classifiers.evaluation.*;


public class TestWekaEasy{
	public static void main(String args[]) throws Exception{
		//load datasets
				DataSource source1 = new DataSource("C:/Program Files/Weka-3-8/data/breast-cancerKnown.arff");
				Instances training = source1.getDataSet();
				DataSource source2 = new DataSource("C:/Program Files/Weka-3-8/data/breast-cancerNOTK.arff");
				Instances testing = source2.getDataSet();
	
				int numAttr = training.numAttributes();
				training.setClassIndex(numAttr - 1);
				testing.setClassIndex(numAttr - 1);

				NaiveBayes classifier = new NaiveBayes();

				classifier.buildClassifier(training);

				Evaluation eval = new Evaluation(testing);	

				eval.evaluateModel(classifier, testing); 
				
				System.out.println("AUC = "+eval.areaUnderROC(1));
				System.out.println("kappa = "+eval.kappa());
			
				
	}
}
