package gov.usgs.earthquake.nshm.nz;

import static org.opensha2.eq.TectonicSetting.*;
import static org.opensha2.eq.fault.FocalMech.*;

import java.util.Map;

import org.opensha2.eq.TectonicSetting;
import org.opensha2.eq.fault.FocalMech;

import com.google.common.collect.ImmutableMap;

/**
 * Add comments here
 *
 * @author Peter Powers
 */
@SuppressWarnings("javadoc")
public enum NZ_SourceID {

	// NOTE: the tectonic setting indicators are only used for
	// fault sources; grid source settings consider depth as well
	
	// TODO what is RO; discovered when checking grid source
	// processing completeness; assuming reverse-oblique, which
	// is likely correct but need to check with Mark (rs vs. sr vs. ro)
	
	IF(90.0, SUBDUCTION_INTERFACE),
	NV(-90.0, VOLCANIC),
	
	RV(90.0, ACTIVE_SHALLOW_CRUST),   // maps to REVERSE in McVerry
	RS(50.0, ACTIVE_SHALLOW_CRUST),   // maps to REVERSE_OBLIQUE
	RO(50.0, ACTIVE_SHALLOW_CRUST),   // maps to REVERSE_OBLIQUE
	SR(50.0, ACTIVE_SHALLOW_CRUST),   // maps to REVERSE_OBLIQUE
	SS(0.0, ACTIVE_SHALLOW_CRUST),    // maps to STRIKE_SLIP
	SN(-30.0, ACTIVE_SHALLOW_CRUST),  // maps to STRIKE_SLIP
	NS(-50.0, ACTIVE_SHALLOW_CRUST),  // maps to NORMAL
	NN(-90.0, ACTIVE_SHALLOW_CRUST);  // maps to NORMAL
	
	private double rake;
	private TectonicSetting trt;
	
	private NZ_SourceID(double rake, TectonicSetting trt) {
		this.rake = rake;
		this.trt = trt;
	}
	
	/**
	 * Convert 'id' from source file to enum type.
	 * @param id
	 * @return the source identifier
	 */
	public static NZ_SourceID fromString(String id) {
		return NZ_SourceID.valueOf(id.toUpperCase());
	}
	
	/**
	 * Returns the rake.
	 * @return the rake
	 */
	public double rake() {
		return rake;
	}
	
	/**
	 * Returns the {code TectonicRegionType}.
	 * @return the {code TectonicRegionType}
	 */
	public TectonicSetting tectonicType() {
		return trt;
	}
	
	// grid source focal mech maps (only those required by 2012 grid inputs)
	private static final Map<FocalMech, Double> SS_MAP = ImmutableMap.of(STRIKE_SLIP, 1.0, REVERSE, 0.0, NORMAL, 0.0);
	private static final Map<FocalMech, Double> R_MAP = ImmutableMap.of(STRIKE_SLIP, 0.0, REVERSE, 1.0, NORMAL, 0.0);
	private static final Map<FocalMech, Double> N_MAP = ImmutableMap.of(STRIKE_SLIP, 0.0, REVERSE, 0.0, NORMAL, 1.0);
	private static final Map<FocalMech, Double> SS_R_MAP = ImmutableMap.of(STRIKE_SLIP, 0.5, REVERSE, 0.5, NORMAL, 0.0);
	//private static final Map<FocalMech, Double> SS_N_MAP = ImmutableMap.of(STRIKE_SLIP, 0.5, REVERSE, 0.0, NORMAL, 0.5);
	
	/**
	 * Returns the focal mech weights associated with the type.
	 * @return a mech weight map
	 */
	public Map<FocalMech, Double> mechWtMap() {
		switch (this) {
			case SS:
				return SS_MAP;
			case RV:
				return R_MAP;
			case NN:
				return N_MAP;
			case SR:
				return SS_R_MAP;
			case RO:
				return SS_R_MAP;
			case NV:
				return N_MAP;
			default:
				throw new UnsupportedOperationException("No grid sources use :" +  this);
		}
	}
}
