package gov.usgs.earthquake.nshm.convert;

import static org.opensha.eq.forecast.SourceAttribute.A;
import static org.opensha.eq.forecast.SourceAttribute.B;
import static org.opensha.eq.forecast.SourceAttribute.D_MAG;
import static org.opensha.eq.forecast.SourceAttribute.MAG_SCALING;
import static org.opensha.eq.forecast.SourceAttribute.M_MAX;
import static org.opensha.eq.forecast.SourceAttribute.M_MIN;
import static org.opensha.eq.forecast.SourceAttribute.TYPE;
import static org.opensha.eq.forecast.SourceAttribute.WEIGHT;
import static org.opensha.eq.forecast.SourceElement.MAG_FREQ_DIST;
import static org.opensha.mfd.MFD_Type.GR;
import static org.opensha.mfd.MFDs.magCount;
import static org.opensha.util.Parsing.addAttribute;
import static org.opensha.util.Parsing.addElement;
import gov.usgs.earthquake.nshm.convert.FaultConverter.SourceData;
import gov.usgs.earthquake.nshm.util.Utils;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.opensha.eq.fault.scaling.MagScalingType;
import org.opensha.util.Parsing;
import org.w3c.dom.Element;

/*
 * Wrapper for Gutenberg-Richter MFD data; handles all validation and NSHMP
 * corrections to fault and subduction GR mfds.
 * 
 * TODO try and get rid of logger references
 */
class GR_Data implements MFD_Data {

	double aVal;
	double bVal;
	double mMin;
	double mMax;
	double dMag;
	double weight;
	int nMag;
	MagScalingType scaling;

	/* Used internally */
	private GR_Data() {}
	
	/* For parsing WUS fault sources */
	static GR_Data createForFault(String src, SourceData fd, MagScalingType scaling, Logger log) {
		GR_Data gr = new GR_Data();
		gr.readSource(src);
		// check for too small a dMag
		gr.validateDeltaMag(log, fd);
		// check if mags are not the same, center on closest dMag values
		gr.recenterMagBins(log, fd);
		// check mag count
		gr.validateMagCount(log, fd);
		gr.scaling = scaling;
		return gr;
	}

	/* For parsing subduction sources */
	static GR_Data createForSubduction(String src, MagScalingType scaling) {
		GR_Data gr = new GR_Data();
		gr.readSource(src);
		gr.scaling = scaling;
		gr.nMag = magCount(gr.mMin, gr.mMax, gr.dMag);
		return gr;
	}
	
	/* For parsing grid sources; mag bins are recentered */
	static GR_Data createForGrid(String src, MagScalingType scaling) {
		GR_Data gr = new GR_Data();
		List<Double> grDat = Parsing.toDoubleList(src);
		gr.bVal = grDat.get(0);
		gr.mMin = grDat.get(1);
		gr.mMax = grDat.get(2);
		gr.dMag = grDat.get(3);
		gr.recenterMagBins();
		gr.scaling = scaling;
		gr.weight = 1.0;
		gr.nMag = magCount(gr.mMin, gr.mMax, gr.dMag);
		return gr;
	}
	
	/* For final assembly and export of grid mfds */
	static GR_Data create(double aVal, double bVal, double mMin,
			double mMax, double dMag, double weight, MagScalingType scaling) {
		GR_Data gr = new GR_Data();
		gr.aVal = aVal;
		gr.bVal = bVal;
		gr.mMin = mMin;
		gr.mMax = mMax;
		gr.dMag = dMag;
		gr.weight = weight;
		gr.scaling = scaling;
		gr.nMag = magCount(mMin, mMax, dMag);
		return gr;
	}
	
	private void readSource(String src) {
		List<Double> grDat = Parsing.toDoubleList(src);
		aVal = grDat.get(0);
		bVal = grDat.get(1);
		mMin = grDat.get(2);
		mMax = grDat.get(3);
		dMag = grDat.get(4);
		try {
			weight = grDat.get(5); // may or may not be present
		} catch (IndexOutOfBoundsException oobe) {
			weight = 1;
		}
		nMag = 0;
	}
	
	private void validateDeltaMag(Logger log, SourceData fd) {
		if (dMag <= 0.004) {
			StringBuilder sb = new StringBuilder().append("GR dMag [")
				.append(dMag).append("] is being increased to 0.1");
			appendFaultDat(sb, fd);
			log.warning(sb.toString());
			dMag = 0.1;
		}
	}

	// for fault sources
	private void recenterMagBins(Logger log, SourceData fd) {
		if (mMin == mMax) {
			log.warning("GR to CH conversion : M=" + mMin + " : " + fd.name);
		} else {
			mMin = mMin + dMag / 2.0;
			mMax = mMax - dMag / 2.0 + 0.0001; // TODO 0.0001 necessary??
		}
	}

	// for grid sources
	private void recenterMagBins() {
		if (mMin == mMax) return;
		mMin = mMin + dMag / 2.0;
		mMax = mMax - dMag / 2.0;
	}

	private void validateMagCount(Logger log, SourceData fd) {
		nMag = magCount(mMin, mMax, dMag);
		if (nMag < 1) {
			RuntimeException rex = new RuntimeException(
				"Number of mags must be \u2265 1");
			StringBuilder sb = new StringBuilder()
				.append("GR nMag is less than 1");
			appendFaultDat(sb, fd);
			log.log(Level.WARNING, sb.toString(), rex);
			throw rex;
		}
	}

	// TODO clean if not necessary
//	/*
//	 * Returns true if (1) multi-mag and mMax-epi < 6.5 or (2) single-mag and
//	 * mMax-epi-2s < 6.5
//	 */
//	boolean hasMagExceptions(Logger log, FaultData fd, MagUncertaintyData md) {
//		if (nMag > 1) {
//			// for multi mag consider only epistemic uncertainty
//			double mMaxAdj = mMax + md.epiDeltas[0];
//			if (mMaxAdj < 6.5) {
//				StringBuilder sb = new StringBuilder()
//					.append("Multi mag GR mMax [").append(mMax)
//					.append("] with epistemic unc. [").append(mMaxAdj)
//					.append("] is \u003C 6.5");
//				appendFaultDat(sb, fd);
//				log.warning(sb.toString());
//				return true;
//			}
//		} else if (nMag == 1) {
//			// for single mag consider epistemic and aleatory uncertainty
//			double mMaxAdj = md.aleaMinMag(mMax + md.epiDeltas[0]);
//			if (mMaxAdj < 6.5) {
//				StringBuilder sb = new StringBuilder()
//					.append("Single mag GR mMax [").append(mMax)
//					.append("] with epistemic and aleatory unc. [")
//					.append(mMaxAdj).append("] is \u003C 6.5");
//				appendFaultDat(sb, fd);
//				log.warning(sb.toString());
//				return true;
//			}
//		} else {
//			// log empty mfd
//			StringBuilder sb = new StringBuilder()
//				.append("GR MFD with no mags");
//			appendFaultDat(sb, fd);
//			log.warning(sb.toString());
//		}
//		return false;
//	}

	@Override
	public Element appendTo(Element parent, MFD_Data ref) {
		Element e = addElement(MAG_FREQ_DIST, parent);
		// always include type
		addAttribute(TYPE, GR, e);
		// always include rate
		addAttribute(A, aVal, e);
		if (ref != null) {
			GR_Data refGR = (GR_Data) ref;
			if (bVal != refGR.bVal) addAttribute(B, bVal, e);
			if (mMin != refGR.mMin) addAttribute(M_MIN, mMin, e);
			if (mMax != refGR.mMax) addAttribute(M_MAX, mMax, e);
			if (dMag != refGR.dMag) addAttribute(D_MAG , dMag, e);
			if (weight != refGR.weight) addAttribute(WEIGHT, weight, e);
			if (scaling != refGR.scaling) addAttribute(MAG_SCALING, scaling, e);
		} else {
			addAttribute(B, Double.toString(bVal), e);
			addAttribute(M_MIN, Double.toString(mMin), e);
			addAttribute(M_MAX, Double.toString(mMax), e);
			addAttribute(D_MAG, Double.toString(dMag), e);
			addAttribute(WEIGHT, Double.toString(weight), e);
			// at present, magScaling does not vary by source group
			addAttribute(MAG_SCALING, scaling, e);
		}
		return e;
	}	
	
	private static final String LF = System.getProperty("line.separator");
	
	/*
	 * Convenience method to append to supplied <code>StringBuilder</code> fault
	 * and file information.
	 */
	static void appendFaultDat(StringBuilder b, SourceData fd) {
		b.append(LF).append(Utils.WARN_INDENT)
			.append(fd.name).append(LF)
			.append(Utils.WARN_INDENT).append(fd.file);
	}

}
