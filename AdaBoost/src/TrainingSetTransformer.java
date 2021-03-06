import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

/**
 * TrainingSetTransformer.java
 * 
 * A utility tool that is used for normalization of training set data to fit
 * within a certain range for ease of computation in AdaBoost type algorithms.
 * 
 * Transform a set of training samples using linear transformation:
 * x_i = offset_i + scale_i * v_i
 * such that v_i is normalized in range [N1, N2]
 *
 * Also, transform the target using linear transformation:
 * y = offset_t + scale_t * w
 * such that w is normalized in range [N1, N2]
 * 
 * Provide utility to determine approximate linear correlations between 
 * v_i and w, so the linear coefficients can be used as the initial values 
 * for gradient descent regression.
 * 
 * Last Updated February 23, 2013
 */
public class TrainingSetTransformer {
	public static final int N1 = 0;
	public static final int N2 = 3;

	double[] inputOffset;  // min value of input feature - i from all samples
	double[] inputScale;   // (max - min) value of input feature - i from all 
						   // samples
	double targetOffset;   // min target value from all samples
	double targetScale;    // (max - min) target value from all samples

	public TrainingSetTransformer(ArrayList<TrainingExample> training_set) {
		int inputCount = training_set.get(0).getInputVector().length;
		inputOffset = new double[inputCount];
		inputScale = new double[inputCount];
		
		/* targetOffset will hold the minimum of offset values of all samples
		 * and targetScale will hold the maximum scale value of all samples */
		targetOffset = Double.MAX_VALUE;         
		targetScale = (-1.0) * Double.MAX_VALUE;  
		for (int i = 0; i < inputCount; i++) {
			inputOffset[i] = Double.MAX_VALUE;          
			inputScale[i] = (-1.0) * Double.MAX_VALUE; 
		}
		for (TrainingExample s : training_set) {
			double target = s.getTarget();
			if (target < targetOffset) {
				targetOffset = target;
			}
			if (target > targetScale) {
				targetScale = target;
			}
			double[] input = s.getInputVector();
			for (int i = 0; i < inputCount; i++) {
				if (input[i] < inputOffset[i]) {
					inputOffset[i] = input[i];
				}
				if (input[i] > inputScale[i]) {
					inputScale[i] = input[i];
				}
			}
		}

		/* Set the scale to (max - min) / (N2 - N1)
		 * Set the offset to (min - scale * N1)
		 */
		targetScale = (targetScale - targetOffset) / (N2-N1); 
		targetOffset -= targetScale * N1; 						
		for (int i = 0; i < inputCount; i++) {
			inputScale[i] = (inputScale[i] - inputOffset[i]) / (N2-N1);
			inputOffset[i] -= inputScale[i] * N1;
		}
	}
	
	/**
	 * Use this transformer to convert input and target values of all samples,
	 * so they use the normalized values in the range [0, 1]
	 * 
	 * training_set is the list of samples to be transformed
	 */
	public void transform(ArrayList<TrainingExample> training_set) {
		for (TrainingExample s : training_set) {
			double target = s.getTarget() - targetOffset;
			if (targetScale > 0) target /= targetScale;
			s.setTarget(target);
			double[] sInput = s.getInputVector();
			for (int i = 0; i < sInput.length; i++) {
				double value = sInput[i] - inputOffset[i];
				if (inputScale[i] > 0) value /= inputScale[i];
				s.setInput(i, value);
			}
		}
	}
	
	/**
	 * Convert a normalized target value to the real value 
	 */
	public double toRealTarget(double normalizedTarget) {
		return targetOffset + targetScale * normalizedTarget;
	}
	
	/**
	 * Helper object for estimated correlation of input feature and target value
	 */
	public static class Correlation {
		int index;
		double offset;
		double factor;

		public Correlation(int index, double offset, double factor) {
			this.index = index;
			this.offset = offset;
			this.factor = factor;
		}

		public int getIndex() {
			return index;
		}

		public double getOffset() {
			return offset;
		}

		public double getFactor() {
			return factor;
		}
	}
	
	/**
	 * Calculate estimated coefficients between input features and target values of a set of training samples
	 *  
	 * @param training_set samples for estimating correlations
	 * @return list of coefficients sorted in descending order
	 */
	public static Correlation[] estimateCorrelations(ArrayList<TrainingExample> training_set) {

		// Collect statistics for input-to-target coefficient
		int featureCount = training_set.get(0).getInputVector().length;
		double[] xmean = new double[featureCount];
		double[] x2mean = new double[featureCount];
		double[] xymean = new double[featureCount];
		double ymean = 0;
		for (TrainingExample s : training_set) {
			double weight = s.getRelativeWeight();
			ymean += s.getTarget() * weight;
			double[] input = s.getInputVector();
			for (int i = 0; i < featureCount; i++) {
				xmean[i] += input[i] * weight;
				x2mean[i] += input[i]*input[i] * weight;
				xymean[i] += input[i]*s.getTarget() * weight;
			}
		}

		// Calculate estimated coefficients
		Correlation[] estimated = new Correlation[featureCount];
		for (int i = 0; i < featureCount; i++) {
			double factor = 0;
			double dev = xmean[i]*xmean[i] - x2mean[i];
			if (dev != 0) {
				factor = (xmean[i]*ymean - xymean[i]) / dev;
			}
			estimated[i] = new Correlation(i, ymean - factor*xmean[i], factor);
		}

		// Sort the array in ascending order of coefficient
		Arrays.sort(estimated, new Comparator<Correlation>() {
			public int compare(Correlation c1, Correlation c2) {
				if (c1.getFactor() < c2.getFactor()) {
					return -1;
				}
				else if (c1.getFactor() == c2.getFactor()) {
					return 0;
				}
				else {
					return 1;
				}
			}
		});
		return estimated;
	}
}
