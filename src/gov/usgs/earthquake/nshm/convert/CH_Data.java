package gov.usgs.earthquake.nshm.convert;

import static org.opensha.eq.forecast.MFD_Attribute.A;
import static org.opensha.eq.forecast.MFD_Attribute.FLOATS;
import static org.opensha.eq.forecast.MFD_Attribute.M;
import static org.opensha.eq.forecast.MFD_Attribute.MAG_SCALING;
import static org.opensha.eq.forecast.MFD_Attribute.TYPE;
import static org.opensha.eq.forecast.MFD_Attribute.WEIGHT;

import org.opensha.eq.fault.scaling.MagScalingType;
import org.opensha.eq.forecast.SourceElement;
import org.opensha.mfd.MFD_Type;
import org.opensha.util.Parsing;
import org.w3c.dom.Element;

/*
 * Wrapper for characteristic (single) MFD data.
 * @author Peter Powers
 */
class CH_Data implements MFD_Data {
	
	double mag;
	double rate;
	double weight;
	boolean floats;
	MagScalingType scaling;
		
	private CH_Data(double mag, double rate, double weight) {
		this.mag = mag;
		this.rate = rate;
		this.weight = weight;
	}
	
	static CH_Data create(double mag, double rate, double weight) {
		return new CH_Data(mag, rate, weight);
	}

	@Override
	public Element appendTo(Element parent, MFD_Data ref) {
		Element e = Parsing.addElement(SourceElement.MAG_FREQ_DIST, parent);
		// always include type
		e.setAttribute(TYPE.toString(), MFD_Type.SINGLE.name());
		// always include rate
		e.setAttribute(A.toString(), Parsing.stripZeros(String.format("%.8g", rate)));
		// always include magnitude
		e.setAttribute(M.toString(), Parsing.stripZeros(String.format("%.3f", mag)));
		if (ref != null) {
			CH_Data refCH = (CH_Data) ref;
			if (floats != refCH.floats) {
				e.setAttribute(FLOATS.toString(),Boolean.toString(floats));
			}
			if (weight != refCH.weight) {
				e.setAttribute(WEIGHT.toString(), Double.toString(weight));
			}
		} else {
			e.setAttribute(FLOATS.toString(),Boolean.toString(floats));
			e.setAttribute(WEIGHT.toString(), Double.toString(weight));
			// at present, magScaling does not vary by source group
			e.setAttribute(MAG_SCALING.toString(), scaling.name());
		}
		return e;
	}
	
	@Override
	public Element appendDefaultTo(Element parent) {
		Element e = Parsing.addElement(SourceElement.MAG_FREQ_DIST, parent);
		e.setAttribute(TYPE.toString(), MFD_Type.SINGLE.name());
		e.setAttribute(M.toString(), Parsing.stripZeros(String.format("%.3f", mag)));
		return e;
	}

}
