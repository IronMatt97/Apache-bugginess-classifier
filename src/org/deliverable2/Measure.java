package org.deliverable2;

public class Measure 
{
	private final String dataset;
	private String classifier;
	private String balancing;
	private String featureSelection;
	private String sensitivity;
	private Integer fp;
	private Integer tp;
	private Integer fn;
	private Integer tn;
	private Integer release;
	private Double precision;
	private Double recall;
	private Double auc;
	private Double kappa;
	private Double trainPercentage;
	private Double defectiveInTrainingPercentage;
	private Double defectiveInTestingPercentage;
	
	public Measure(String dataset)
	{
		this.dataset = dataset;
	}
	public String getDataset()
	{
		return this.dataset;
	}
	public String getClassifier()
	{
		return this.classifier;
	}
	public void setClassifier(String classifier)
	{
		this.classifier = classifier;
	}
	public String getBalancing()
	{
		return this.balancing;
	}
	public void setBalancing(String balancing)
	{
		this.balancing = balancing;
	}
	public String getFeatureSelection()
	{
		return this.featureSelection;
	}
	public void setFeatureSelection(String f)
	{
		this.featureSelection = f;
	}
	public String getSensitivity()
	{
		return this.sensitivity;
	}
	public void setSensitivity(String s)
	{
		this.sensitivity = s;
	}
	public Integer getRelease()
	{
		return this.release;
	}
	public void setRelease(Integer release)
	{
		this.release = release;
	}
	public Integer getTp()
	{
		return this.tp;
	}
	public void setTp(Integer t)
	{
		this.tp = t;
	}
	public Integer getFp()
	{
		return this.fp;
	}
	public void setFp(Integer f)
	{
		this.fp = f;
	}
	public Integer getTn()
	{
		return this.tn;
	}
	public void setTn(Integer t)
	{
		this.tn = t;
	}
	public Integer getFn()
	{
		return this.fn;
	}
	public void setFn(Integer f)
	{
		this.fn = f;
	}
	public Double getRecall()
	{
		return this.recall;
	}
	public void setRecall(Double recall)
	{
		this.recall = recall;
	}
	public Double getPrecision()
	{
		return this.precision;
	}
	public void setPrecision(Double precision)
	{
		this.precision = precision;
	}
	public Double getKappa()
	{
		return this.kappa;
	}
	public void setKappa(Double kappa)
	{
		this.kappa = kappa;
	}
	public Double getAuc()
	{
		return this.auc;
	}
	public void setAuc(Double auc)
	{
		this.auc = auc;
	}
	public Double getTrainPercentage()
	{
		return this.trainPercentage;
	}
	public void setTrainPercentage(Double p)
	{
		this.trainPercentage = p;
	}
	public Double getDefectInTrainPercentage()
	{
		return this.defectiveInTrainingPercentage;
	}
	public void setDefectInTrainPercentage(Double p)
	{
		this.defectiveInTrainingPercentage = p;
	}
	public Double getDefectInTestPercentage()
	{
		return this.defectiveInTestingPercentage;
	}
	public void setDefectInTestPercentage(Double p)
	{
		this.defectiveInTestingPercentage = p;
	}
}