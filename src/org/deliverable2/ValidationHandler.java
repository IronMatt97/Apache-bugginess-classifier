package org.deliverable2;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import weka.attributeSelection.BestFirst;
import weka.attributeSelection.CfsSubsetEval;
import weka.classifiers.Classifier;
import weka.classifiers.CostMatrix;
import weka.classifiers.Evaluation;
import weka.classifiers.meta.CostSensitiveClassifier;
import weka.classifiers.meta.FilteredClassifier;
import weka.core.Instance;
import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.supervised.attribute.AttributeSelection;
import weka.filters.supervised.instance.Resample;
import weka.filters.supervised.instance.SpreadSubsample;
import weka.filters.supervised.instance.SMOTE;

public class ValidationHandler 
{
	private static final Logger logger =  Logger.getLogger(ValidationHandler.class.getName());
	
	//Walk forward: ogni volta prendo incrementalmente il test set e i precedenti come train set iterando le istanze
	public void walkForward(String featureSelection, String balancing, String costEvaluation, String classifier,List <Instances> sets, List<Measure> m, String projName)
	{
		int version = 0;
		for (int i = 1; i < sets.size(); i++)
		{
			Instances test = new Instances(sets.get(i));
			Instances train = new Instances(sets.get(i-1));
			Evaluation eval;
			Classifier c = ClassifierFactory.generateClassifier(classifier);
			
			int k = i-2;
			while(k!=-1)
			{
				train.addAll(sets.get(k));
				k--;
			}
			train.setClassIndex(train.numAttributes() - 1);
			test.setClassIndex(test.numAttributes() - 1);
			
			//Train set e test set sono pronti; nel caso si voglia applicare feature selection
			//verranno filtrati
			
			if(featureSelection.equals("BEST FIRST"))
			{
				List<Instances> r = featureSelection(train,test);
				train = r.get(1);
				test = r.get(0);
			}
			
			//Se si vuole applicare il balancing viene impostato il classificatore
			if(!balancing.equals("NO"))
				c = balancing(c,balancing,train,test.size()+train.size());
	
			//Se si vuole anche la cost sensitiveness bisogna usare un classificatore che ne tenga conto
			
			eval = costSensitiveness(costEvaluation,c,train,test);

			String[] s = {classifier,balancing,featureSelection,costEvaluation}; 
			if(eval == null)
				try {
					eval = new Evaluation(test);
				} catch (Exception e) {
					logger.log(Level.INFO,"An error has occurred evaluating a model.");
				}
			if(eval!=null)
			{
				stepEvaluation(eval,train,test,s,projName,m,version);
				version++;
			}
		}	
	}

	//Preparo la misura e la aggiungo alle altre
	private static void stepEvaluation(Evaluation eval,Instances train, Instances test, String[] s,String projName,List<Measure> measures,int version)
	{//s = classifier, balancing,featuresel,sensitivity
		Measure m = new Measure(projName);
		double r = (train.size())/((double)train.size()+(double)test.size());
		
		int mod = 1;
		if(projName.equals("BOOKKEEPER"))
			mod = 6;
		if(projName.equals("AVRO"))
			mod=16;
		
		m.setRelease((version%mod)+1);
		m.setClassifier(s[0]);
		m.setBalancing(s[1]);
		m.setFeatureSelection(s[2]);
		m.setSensitivity(s[3]);
		m.setAuc(eval.areaUnderROC(1));
		m.setKappa(eval.kappa());
		m.setRecall(eval.recall(1));
		m.setPrecision(eval.precision(1));
		m.setTrainPercentage(r);
		m.setDefectInTestPercentage(buggyPercentage(test));
		m.setDefectInTrainPercentage(buggyPercentage(train));
		m.setTn((int)eval.numTrueNegatives(1));
		m.setTp((int)eval.numTruePositives(1));
		m.setFn((int)eval.numFalseNegatives(1));
		m.setFp((int)eval.numFalsePositives(1));
		
		measures.add(m);
	}
	private static List<Instances> featureSelection(Instances train, Instances test)  
	{
		AttributeSelection filter = new AttributeSelection();
		
		CfsSubsetEval subsetEval = new CfsSubsetEval();		//evaluator
		BestFirst search = new BestFirst();		//search algorithm
		
		filter.setEvaluator(subsetEval);
		filter.setSearch(search);
		ArrayList<Instances> r = new ArrayList<>();
		try 
		{
			filter.setInputFormat(train);
			Instances trainf = Filter.useFilter(train, filter);
			trainf.setClassIndex(trainf.numAttributes() - 1);
			Instances testf= Filter.useFilter(test, filter);	
			testf.setClassIndex(testf.numAttributes() - 1);
			r.add(testf);
			r.add(trainf);
		}
		catch (Exception e) 
		{
			logger.log(Level.INFO,"Error during feature selection.");
		}
		return r;
	}
	
	private static FilteredClassifier balancing(Classifier c,String balancing,Instances train, int dim) 
	{
		FilteredClassifier fc = new FilteredClassifier();
			
		fc.setClassifier(c);
		
		try 
		{
			//---- over sampling ----
			switch (balancing) {
				case "OVERSAMPLING":
					Resample resample = new Resample();
					resample.setInputFormat(train);
					resample.setNoReplacement(false);
					resample.setBiasToUniformClass(0.1);

					double sizePerc = 2 * (getSampleSizePerc(train, dim));

					resample.setSampleSizePercent(sizePerc); //y/2 = %data appartenente a majority class

					//majority class : numero d'istanze

					String[] overOpts = new String[]{"-B", "1.0", "-Z", "130.3"};

					resample.setOptions(overOpts);

					fc.setFilter(resample);
					break;

				//---- under sampling ----
				case "UNDERSAMPLING":
					SpreadSubsample spreadSubsample = new SpreadSubsample();
					String[] opts = new String[]{"-M", "1.0"};
					spreadSubsample.setOptions(opts);
					fc.setFilter(spreadSubsample);
					break;

				//---- SMOTE ----
				case "SMOTE":
					SMOTE smote = new SMOTE();
					smote.setInputFormat(train);
					fc.setFilter(smote);
					break;

				default:
			}
				
		} 
		catch (Exception e) 
		{
			logger.log(Level.INFO,"Smote found a set with only one class to use.");
		}
		return fc;
	}
	private static Evaluation costSensitiveness(String costEvaluation, Classifier c, Instances train, Instances test)
	{
		
		Evaluation ev = null;
		
		if(!costEvaluation.equals("NO"))
		{//Sensitive
			CostSensitiveClassifier c1 = new CostSensitiveClassifier();
			if(costEvaluation.equals("SENSITIVE THRESHOLD"))
			{
				c1.setMinimizeExpectedCost(true);
			}
			else if(costEvaluation.equals("SENSITIVE LEARNING"))
			{
				c1.setMinimizeExpectedCost(false);
				train = reweight(train);
			}
			c1.setClassifier(c);
			c1.setCostMatrix(createCostMatrix());
			try 
			{
				c1.buildClassifier(train);
				ev = new Evaluation(test,c1.getCostMatrix());
				ev.evaluateModel(c1,test);
			} 
			catch (Exception e) 
			{
				logger.log(Level.INFO,"An error has occurred handling classifier sensitiveness.");
			}
		}	
		else//No sensitiveness
		{
			try 
			{
				c.buildClassifier(train);
				ev = new Evaluation(test);
				ev.evaluateModel(c, test);
			} 
			catch (Exception e) 
			{
				logger.log(Level.INFO,"An error has occurred evaluating a model.");
			}
		}
		return ev;
	}
	
	private static double getSampleSizePerc(Instances train, int dim) {
		
		double res;
		
		double numBuggyClasses = buggyPercentage(train);
		int numBuggyClassesTemp = (int) (numBuggyClasses)*100;
		
		if (numBuggyClassesTemp > dim - numBuggyClassesTemp) {
			
			res=numBuggyClasses;	//majority
		}
		
		else {
			res = ((dim - numBuggyClasses)*100)/ dim;
				
		}
		return res;	
	}
	
	private static double buggyPercentage(Instances train)
	{
		double count = 0.0;
		for(Instance i : train)
		{
			if(i.toString().endsWith("Yes"))
			{
				count++;
			}
		}
		return (count/train.size());
	}
	
	private static CostMatrix createCostMatrix()
	{//CFN = 10*CFP
		CostMatrix costMatrix = new CostMatrix(2);
		costMatrix.setCell(0, 0, 0.0);//Cost for true positive
		costMatrix.setCell(1, 0, 1.0);//Cost for false positive
		costMatrix.setCell(0, 1, 10.0);//Cost for false negative
		costMatrix.setCell(1, 1, 0.0);//Cost for true negative
		return costMatrix;
	}
	private static Instances reweight(Instances train)
	{//CFN = 10*CFP
		
		Instances train2 = new Instances(train);
		int cost = 10;
		for(Instance i : train)
		{
			if(i.toString().endsWith("No"))
			{
				for(int k = 0 ; k<cost-1; k++)
					train2.add(i);
			}
		}
		return train2;
	}
}