package org.deliverable2;

import weka.classifiers.Classifier;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.lazy.IBk;
import weka.classifiers.trees.RandomForest;

public class ClassifierFactory 
{
	private ClassifierFactory() 
	{
	   throw new IllegalStateException("This is a factory.");
	}
	
	public static Classifier generateClassifier(String type)
	{
		Classifier c = null;

		switch (type) {
			case "RANDOM FOREST":
				c = new RandomForest();
				break;
			case "NAIVE BAYES":
				c = new NaiveBayes();
				break;
			case "IBK":
				c = new IBk();
				break;
			default:
		}
		return c;
	}
}